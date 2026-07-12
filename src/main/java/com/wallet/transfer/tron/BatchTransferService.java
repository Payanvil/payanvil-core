package com.wallet.transfer.tron;

import com.wallet.transfer.domain.PreparedTransfer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Оркестратор пачки переводов. Проход 1 — отправка.
 * <p>
 * Для каждого перевода: свежий баланс → оценка → проверка средств → отправка.
 * Баланс берётся как min(сеть, локальный счётчик): сеть ловит внешние
 * списания, локальный счётчик — отправленное "в полёте", ещё не списанное.
 * <p>
 * Классификация сбоев:
 * - не хватает TRX на комиссию → STOP (общий ресурс)
 * - не хватает USDT, но впереди есть платёж по карману → SKIP, дальше
 * - не хватает USDT и впереди ничего по карману → STOP
 * - сетевой сбой при отправке (повтор исчерпан) → STOP
 * - прочая ошибка отправки (revert, битый адрес) → SKIP (FAILED), дальше
 */
@Service
public class BatchTransferService {

    private final TronBalanceService balanceService;
    private final FeeEstimateService feeEstimateService;
    private final FundsSufficiencyService fundsSufficiencyService;
    private final TransferSendService sendService;
    private final TronProperties properties;
    private final TronClientHolder clientHolder;

    public BatchTransferService(TronBalanceService balanceService,
                                FeeEstimateService feeEstimateService,
                                FundsSufficiencyService fundsSufficiencyService,
                                TransferSendService sendService,
                                TronProperties properties, TronClientHolder clientHolder) {
        this.balanceService = balanceService;
        this.feeEstimateService = feeEstimateService;
        this.fundsSufficiencyService = fundsSufficiencyService;
        this.sendService = sendService;
        this.properties = properties;
        this.clientHolder = clientHolder;
    }

    /**
     * Проход 1: отправить пачку переводов.
     * Возвращает сводку; отправленные помечены SENT_UNCONFIRMED
     * (подтверждение статуса — Проход 2, отдельно).
     *
     * @param transfers переводы из БД (сохранённые, с id)
     * @return сводка обработки
     */
    public BatchSummary sendBatch(List<PreparedTransfer> transfers) {
        return sendBatch(transfers, ProgressListener.NO_OP);
    }

    public BatchSummary sendBatch(List<PreparedTransfer> transfers, ProgressListener progress) {
        String sender = clientHolder.senderAddress();
        List<TransferResult> results = new ArrayList<>();
        boolean stopped = false;

        // Локальный счётчик USDT: читаем сеть один раз вначале, дальше вычитаем сами
        BigDecimal localUsdt = balanceService.getUsdtBalance(sender);

        for (int i = 0; i < transfers.size(); i++) {
            PreparedTransfer transfer = transfers.get(i);
            BigDecimal amount = transfer.amount().subtract(transfer.deductedFeeUsdt());
            progress.onProgress(ProgressPhase.SENDING, i + 1, transfers.size());

            // Комиссия с получателя съела сумму (≤ 0) — не отправляем, метим в отчёт.
            if (amount.signum() <= 0) {
                results.add(feeExceedsAmount(transfer));
                continue;
            }

            // Свежие балансы из сети
            BigDecimal networkUsdt = balanceService.getUsdtBalance(sender);
            BigDecimal trxBalance = balanceService.getTrxBalance(sender);

            // Безопасный USDT = минимум из сети и локального счётчика
            BigDecimal safeUsdt = networkUsdt.min(localUsdt);

            // Оценка комиссии
            BigInteger amountUnits = toMinimalUnits(amount);
            FeeEstimate estimate =
                    feeEstimateService.estimate(transfer.walletAddress(), amountUnits);

            // Проверка достаточности (на безопасном балансе)
            FundsVerdict verdict = fundsSufficiencyService.check(
                    amount, safeUsdt, trxBalance, estimate.feeTrx());

            // 1) Не хватает TRX на комиссию → STOP всей пачки
            if (!verdict.trxSufficient()) {
                stopRemaining(transfers, i, results,
                        "report.reason.insufficientTrxFee", null);
                stopped = true;
                break;
            }

            // 2) Не хватает USDT на этот перевод
            if (!verdict.usdtSufficient()) {
                results.add(skipped(transfer, "report.reason.insufficientUsdt", null));

                BigDecimal minAfter = minAmountAfter(transfers, i);
                if (minAfter == null) {
                    continue; // это был последний — просто заканчиваем
                }
                if (safeUsdt.compareTo(minAfter) < 0) {
                    // впереди ничего по карману → STOP остатка
                    stopRemaining(transfers, i + 1, results,
                            "report.reason.insufficientUsdtRemaining", null);
                    stopped = true;
                    break;
                }
                continue; // впереди есть платёж по карману → дальше
            }

            // 3) Средств хватает → отправляем
            try {
                SendResult sent = sendService.send(transfer, estimate);
                results.add(sentUnconfirmed(transfer, sent.txid()));
                // Вычитаем из локального счётчика отправленную сумму
                localUsdt = localUsdt.subtract(amount);
            } catch (RetryExhaustedException e) {
                // Сеть не оклемалась → STOP
                stopRemaining(transfers, i, results,
                        "report.reason.networkFailure", e.getMessage());
                stopped = true;
                break;
            } catch (RuntimeException e) {
                // Проблема конкретного перевода → SKIP (FAILED), дальше
                results.add(failed(transfer, "report.reason.sendError", e.getMessage()));
            }
        }

        return summary(transfers.size(), results, stopped);
    }

    /**
     * Полная обработка пачки: Проход 1 (отправка) + Проход 2 (подтверждение).
     * Боевой вход — отправляет всё, затем сверяет статусы.
     *
     * @param transfers переводы из БД (сохранённые, с id)
     * @return финальная сводка с подтверждёнными статусами
     */
    public BatchSummary processBatch(List<PreparedTransfer> transfers) {
        return processBatch(transfers, ProgressListener.NO_OP);
    }

    public BatchSummary processBatch(List<PreparedTransfer> transfers, ProgressListener progress) {
        BatchSummary afterSend = sendBatch(transfers, progress);
        return confirmBatch(transfers, afterSend, progress);
    }

    /**
     * Проход 2: подтвердить статусы отправленных переводов.
     * Опрашивает сеть по каждому SENT_UNCONFIRMED и проставляет CONFIRMED/FAILED.
     * PENDING (не дождались) оставляет SENT_UNCONFIRMED — досверим позже.
     *
     * @param transfers исходные переводы (для поиска перевода по id)
     * @param sendSummary сводка после Прохода 1
     * @return финальная сводка
     */
    public BatchSummary confirmBatch(List<PreparedTransfer> transfers,
                                     BatchSummary sendSummary) {
        return confirmBatch(transfers, sendSummary, ProgressListener.NO_OP);
    }

    public BatchSummary confirmBatch(List<PreparedTransfer> transfers,
                                     BatchSummary sendSummary,
                                     ProgressListener progress) {
        // id → перевод, чтобы передать в confirm для обновления статуса
        Map<Long, PreparedTransfer> byId = new HashMap<>();
        for (PreparedTransfer t : transfers) {
            byId.put(t.id(), t);
        }

        List<TransferResult> finalResults = new ArrayList<>();

        int total = sendSummary.results().size();
        int done = 0;
        for (TransferResult r : sendSummary.results()) {
            progress.onProgress(ProgressPhase.CONFIRMING, ++done, total);
            // Подтверждаем только отправленные, но ещё не подтверждённые
            if (r.outcome() != TransferOutcome.SENT_UNCONFIRMED) {
                finalResults.add(r); // SKIPPED/STOPPED/FAILED — уже финальные
                continue;
            }

            PreparedTransfer entity = byId.get(r.transferId());
            ConfirmOutcome outcome = sendService.confirm(entity, r.txid());

            TransferOutcome finalOutcome = switch (outcome.result()) {
                case CONFIRMED -> TransferOutcome.CONFIRMED;
                case FAILED -> TransferOutcome.FAILED;
                case PENDING -> TransferOutcome.SENT_UNCONFIRMED; // не дождались
            };

            String detailKey = outcome.result() == ConfirmResult.PENDING
                    ? "report.reason.confirmationTimeout"
                    : r.detailKey();
            String detailArg = outcome.result() == ConfirmResult.PENDING
                    ? null
                    : r.detailArg();

            finalResults.add(new TransferResult(
                    r.transferId(), r.recipient(), r.amount(),
                    finalOutcome, r.txid(),
                    outcome.feeTrx(), outcome.confirmedAt(), detailKey, detailArg
            ));
        }

        return summary(sendSummary.total(), finalResults, sendSummary.stopped());
    }

    /** Минимальная сумма среди переводов ПОСЛЕ index (null, если их нет). */
    private BigDecimal minAmountAfter(List<PreparedTransfer> transfers, int index) {
        BigDecimal min = null;
        for (int j = index + 1; j < transfers.size(); j++) {
            BigDecimal a = transfers.get(j).amount();
            if (min == null || a.compareTo(min) < 0) {
                min = a;
            }
        }
        return min;
    }

    /** Пометить переводы [from .. конец] как STOPPED. */
    private void stopRemaining(List<PreparedTransfer> transfers, int from,
                               List<TransferResult> results,
                               String detailKey, String detailArg) {
        for (int j = from; j < transfers.size(); j++) {
            results.add(stopped(transfers.get(j), detailKey, detailArg));
        }
    }

    private BigInteger toMinimalUnits(BigDecimal amountUsdt) {
        return amountUsdt
                .multiply(BigDecimal.TEN.pow(properties.usdtDecimals()))
                .toBigIntegerExact();
    }

    private TransferResult sentUnconfirmed(PreparedTransfer t, String txid) {
        return new TransferResult(t.id(), t.walletAddress(), t.amount(),
                TransferOutcome.SENT_UNCONFIRMED, txid, null, null, null, null);
    }

    private TransferResult skipped(PreparedTransfer t, String detailKey, String detailArg) {
        return new TransferResult(t.id(), t.walletAddress(), t.amount(),
                TransferOutcome.SKIPPED, null, null, null, detailKey, detailArg);
    }

    /**
     * Комиссия с получателя ≥ суммы перевода: отправлять нечего.
     * В отчёт идёт исходная сумма (getAmount), статус FEE_EXCEEDS_AMOUNT.
     */
    private TransferResult feeExceedsAmount(PreparedTransfer t) {
        return new TransferResult(t.id(), t.walletAddress(), t.amount(),
                TransferOutcome.FEE_EXCEEDS_AMOUNT, null, null, null,
                "report.reason.feeExceedsAmount", null);
    }

    private TransferResult failed(PreparedTransfer t, String detailKey, String detailArg) {
        return new TransferResult(t.id(), t.walletAddress(), t.amount(),
                TransferOutcome.FAILED, null, null, null, detailKey, detailArg);
    }

    private TransferResult stopped(PreparedTransfer t, String detailKey, String detailArg) {
        return new TransferResult(t.id(), t.walletAddress(), t.amount(),
                TransferOutcome.STOPPED, null, null, null, detailKey, detailArg);
    }

    private BatchSummary summary(int total, List<TransferResult> results, boolean stopped) {
        int confirmed = (int) results.stream()
                .filter(r -> r.outcome() == TransferOutcome.CONFIRMED).count();
        int failed = (int) results.stream()
                .filter(r -> r.outcome() == TransferOutcome.FAILED).count();
        int skipped = (int) results.stream()
                .filter(r -> r.outcome() == TransferOutcome.SKIPPED).count();
        int feeExceeded = (int) results.stream()
                .filter(r -> r.outcome() == TransferOutcome.FEE_EXCEEDS_AMOUNT).count();
        return new BatchSummary(total, confirmed, failed, skipped, feeExceeded, stopped, results);
    }
}
