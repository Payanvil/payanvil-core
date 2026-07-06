package com.wallet.transfer.tron;

import com.wallet.transfer.persistence.TransactionLogEntity;
import com.wallet.transfer.persistence.TransferEntity;
import com.wallet.transfer.persistence.TransferPersistenceService;
import com.wallet.transfer.persistence.TransferStatus;
import org.springframework.stereotype.Service;
import org.tron.trident.abi.FunctionEncoder;
import org.tron.trident.abi.TypeReference;
import org.tron.trident.abi.datatypes.Address;
import org.tron.trident.abi.datatypes.Bool;
import org.tron.trident.abi.datatypes.Function;
import org.tron.trident.abi.datatypes.generated.Uint256;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.utils.ByteArray;
import org.tron.trident.proto.Chain.Transaction;
import org.tron.trident.proto.Response.TransactionExtention;
import org.tron.trident.proto.Response.TransactionInfo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.time.Instant;

/**
 * Отправка USDT-перевода в сеть Tron.
 * <p>
 * Схема защиты от двойной отправки: фиксируем намерение (PREPARED)
 * в БД ДО broadcast, затем после успешного broadcast — SENT с txid.
 * Перед отправкой проверяем идемпотентность (не отправлен ли уже).
 */
@Service
public class TransferSendService {

    private static final BigDecimal SUN_PER_TRX = BigDecimal.valueOf(1_000_000);

    /** Множитель feeLimit (потолок). Лишнее НЕ сгорает — запас безопасен. */
    private static final BigDecimal FEE_LIMIT_BUFFER = BigDecimal.valueOf(1.3);

    /** Интервал между опросами сети. */
    private static final long CONFIRM_POLL_INTERVAL_MS = 3_000L;

    /** Повтор broadcast: максимум попыток. */
    private static final int BROADCAST_MAX_ATTEMPTS = 5;

    /** Повтор broadcast: базовая пауза (мс), далее нарастает ×2. */
    private static final long BROADCAST_BASE_DELAY_MS = 2_000L;

    private final TronClientHolder clientHolder;
    private final TronProperties properties;
    private final FeeEstimateService feeEstimateService;
    private final TransferPersistenceService persistenceService;
    private final NetworkRetry networkRetry;

    public TransferSendService(TronClientHolder clientHolder, TronClientHolder clientHolder1,
                               TronProperties properties,
                               FeeEstimateService feeEstimateService,
                               TransferPersistenceService persistenceService,
                               NetworkRetry networkRetry) {
        this.clientHolder = clientHolder1;
        this.properties = properties;
        this.feeEstimateService = feeEstimateService;
        this.persistenceService = persistenceService;
        this.networkRetry = networkRetry;
    }

    /**
     * Отправить перевод, самостоятельно оценив комиссию.
     * Удобно для одиночной отправки; в пачке используйте перегрузку
     * с готовой оценкой, чтобы не считать её дважды.
     */
    public SendResult send(TransferEntity transfer) {
        String recipient = transfer.getWalletAddress();
        BigInteger amount = toMinimalUnits(amountToSend(transfer));
        FeeEstimate estimate = feeEstimateService.estimate(recipient, amount);
        return send(transfer, estimate);
    }

    /**
     * Отправить перевод с уже посчитанной оценкой комиссии.
     * Двигает статус → PREPARED → SENT. Защита от двойной отправки внутри.
     *
     * @param transfer перевод (должен быть сохранён в БД, иметь id)
     * @param estimate готовая оценка комиссии (из оркестратора)
     * @return результат с txid
     */
    public SendResult send(TransferEntity transfer, FeeEstimate estimate) {
        // 1. Идемпотентность: не отправлять повторно
        if (persistenceService.isAlreadySent(transfer.getId())) {
            throw new IllegalStateException(
                    "Перевод уже отправлен: id=" + transfer.getId());
        }

        String recipient = transfer.getWalletAddress();
        BigInteger amount = toMinimalUnits(amountToSend(transfer));

        // 2. feeLimit (потолок с запасом) из переданной оценки
        long feeLimitSun = computeFeeLimitSun(estimate.feeTrx());

        // 3. Фиксируем НАМЕРЕНИЕ до broadcast: лог PREPARED + статус перевода
        TransactionLogEntity log = new TransactionLogEntity(
                transfer.getId(), transfer.getAmount(), TransferStatus.PREPARED);
        log.setFeeTrx(estimate.feeTrx());
        log.setEnergyUsed(estimate.energyUsed());
        log = persistenceService.saveLog(log);
        persistenceService.updateStatus(transfer.getId(), TransferStatus.PREPARED);

        // 4. Отправка в сеть
        String txid = broadcast(recipient, amount, feeLimitSun);

        // 5. Фиксируем результат: txid + статус SENT
        log.setTxid(txid);
        log.setStatus(TransferStatus.SENT);
        persistenceService.saveLog(log);
        persistenceService.updateStatus(transfer.getId(), TransferStatus.SENT);

        return new SendResult(txid, estimate.feeTrx(), estimate.energyUsed());
    }

    /**
     * Сумма к отправке в USDT: исходная сумма минус удержанная комиссия.
     * Для FeePayer.SENDER удержание равно 0 — отправляется полная сумма.
     * Для RECIPIENT — за вычетом комиссии (посчитана координатором заранее).
     */
    private BigDecimal amountToSend(TransferEntity transfer) {
        return transfer.getAmount().subtract(transfer.getDeductedFeeUsdt());
    }

    /** USDT (человеческий BigDecimal) → минимальные единицы. */
    private BigInteger toMinimalUnits(BigDecimal amountUsdt) {
        return amountUsdt
                .multiply(BigDecimal.TEN.pow(properties.usdtDecimals()))
                .toBigIntegerExact();
    }

    /** feeLimit в SUN: комиссия TRX × 1e6 × буфер, округление вверх. */
    private long computeFeeLimitSun(BigDecimal feeTrx) {
        return feeTrx
                .multiply(SUN_PER_TRX)
                .multiply(FEE_LIMIT_BUFFER)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    /**
     * Построить, подписать и отправить transfer с повтором временных сбоев.
     * Возвращает txid.
     * <p>
     * Транзакция строится и подписывается ОДИН раз; при повторе шлётся
     * тот же подписанный объект (тот же txid) — сеть дедуплицирует, двойной
     * отправки не будет. txid считаем заранее, чтобы вернуть его и при DUP.
     */
    private String broadcast(String recipient, BigInteger amount, long feeLimitSun) {
        Transaction signed = buildAndSign(recipient, amount, feeLimitSun);
        String expectedTxid = computeTxid(signed);
        return broadcastWithRetry(signed, expectedTxid);
    }

    /** Построить и подписать транзакцию (один раз, без отправки). */
    private Transaction buildAndSign(String recipient, BigInteger amount, long feeLimitSun) {
        try {
            Function transfer = new Function(
                    "transfer",
                    List.of(new Address(recipient), new Uint256(amount)),
                    List.of(new TypeReference<Bool>() {})
            );
            String encodedHex = FunctionEncoder.encode(transfer);

            TransactionExtention txnExt = clientHolder.client().triggerContract(
                    clientHolder.senderAddress(),
                    properties.usdtContractAddress(),
                    encodedHex,
                    0L, 0L, null,
                    feeLimitSun
            );
            return clientHolder.client().signTransaction(txnExt);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Не удалось построить/подписать транзакцию: " + e.getMessage(), e);
        }
    }

    /**
     * Отправить подписанную транзакцию с повтором временных сбоев.
     * DUP_TRANSACTION_ERROR трактуется как успех (уже в сети).
     */
    private String broadcastWithRetry(Transaction signed, String expectedTxid) {
        return networkRetry.execute(
                () -> {
                    try {
                        return clientHolder.client().broadcastTransaction(signed);
                    } catch (RuntimeException e) {
                        if (isDuplicate(e)) {
                            // Транзакция уже в сети — это успех
                            return expectedTxid;
                        }
                        throw e; // остальное классифицирует предикат повтора
                    }
                },
                NetworkRetry::isTransientNetworkError,
                BROADCAST_MAX_ATTEMPTS,
                BROADCAST_BASE_DELAY_MS
        );
    }

    /** DUP = транзакция уже принята сетью. Не ошибка, а успех. */
    private boolean isDuplicate(RuntimeException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("DUP_TRANSACTION_ERROR");
    }

    /**
     * Опросить сеть о статусе ранее отправленной транзакции, записать
     * событие в журнал (с фактической комиссией и временем блока) и
     * обновить финальный статус перевода в БД.
     * <p>
     * Поллинг с таймаутом: транзакция попадает в блок не мгновенно.
     * Пока сеть отдаёт пустой ответ (не в блоке) — ждём и повторяем.
     *
     * @param transfer перевод (должен быть в статусе SENT, иметь txid в логе)
     * @param txid     хэш транзакции
     * @return итог: CONFIRMED/FAILED с фактической комиссией и временем блока;
     *         PENDING — не дождались (статус не меняем, комиссия/время null)
     */
    public ConfirmOutcome confirm(TransferEntity transfer, String txid) {
        long deadline = System.currentTimeMillis()
                + properties.confirmationTimeoutSeconds() * 1000L;

        while (System.currentTimeMillis() < deadline) {
            TransactionInfo info = tryGetInfo(txid);

            // info != null и в блоке — транзакция обработана сетью
            if (info != null && info.getBlockNumber() > 0) {
                boolean success = info.getResult() == TransactionInfo.code.SUCESS;
                TransferStatus finalStatus =
                        success ? TransferStatus.CONFIRMED : TransferStatus.FAILED;

                // Фактические данные из сети (честные, не оценка)
                BigDecimal actualFeeTrx = sunToTrx(info.getFee());
                Instant confirmedAt = Instant.ofEpochMilli(info.getBlockTimeStamp());

                // Журнал: новая запись-событие (append-only)
                TransactionLogEntity log = new TransactionLogEntity(
                        transfer.getId(), transfer.getAmount(), finalStatus);
                log.setTxid(txid);
                log.setFeeTrx(actualFeeTrx);
                log.setConfirmedAt(confirmedAt);
                persistenceService.saveLog(log);

                // Статус перевода
                persistenceService.updateStatus(transfer.getId(), finalStatus);

                ConfirmResult result =
                        success ? ConfirmResult.CONFIRMED : ConfirmResult.FAILED;
                return new ConfirmOutcome(result, actualFeeTrx, confirmedAt);
            }

            sleep(CONFIRM_POLL_INTERVAL_MS);
        }

        // Не дождались — оставляем SENT, можно опросить позже
        return new ConfirmOutcome(ConfirmResult.PENDING, null, null);
    }

    /** SUN (целое) → TRX (BigDecimal). */
    private BigDecimal sunToTrx(long sun) {
        return BigDecimal.valueOf(sun)
                .divide(SUN_PER_TRX, MathContext.DECIMAL64);
    }

    /**
     * Запросить информацию о транзакции. Если транзакция ещё не в блоке,
     * trident бросает IllegalException ("not found") — для нас это сигнал
     * "ещё не готово", возвращаем null (ждём дальше), а не падаем.
     */
    private TransactionInfo tryGetInfo(String txid) {
        try {
            return clientHolder.client().getTransactionInfoById(txid);
        } catch (Exception e) {
            return null;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Опрос статуса прерван", e);
        }
    }

    /**
     * Вычислить txid из подписанной транзакции — SHA-256 от raw_data.
     * Тот же способ, что использует trident внутри broadcastTransaction,
     * поэтому результат идентичен возвращаемому сетью txid.
     * <p>
     * Считаем заранее, чтобы txid был на руках и при успехе, и при DUP
     * (когда сеть бросает исключение и txid из ответа не приходит).
     */
    private String computeTxid(Transaction signed) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(signed.getRawData().toByteArray());
            return ByteArray.toHexString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}