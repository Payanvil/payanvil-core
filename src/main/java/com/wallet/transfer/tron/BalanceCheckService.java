package com.wallet.transfer.tron;

import com.wallet.transfer.persistence.TransactionLogEntity;
import com.wallet.transfer.persistence.TransferEntity;
import com.wallet.transfer.persistence.TransferPersistenceService;
import com.wallet.transfer.persistence.TransferStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Сервис проверки балансов перед переводом.
 * <p>
 * Оркестратор: соединяет блокчейн-слой (чтение балansов из сети)
 * со слоем персистентности (запись в БД). Сам не лезет ни в gRPC,
 * ни в SQL напрямую — пользуется готовыми сервисами.
 * <p>
 * Для каждого перевода читает балансы TRX и USDT кошелька-отправителя,
 * фиксирует их в журнале (transaction_log) и переводит перевод
 * в статус BALANCE_CHECKED.
 */
@Service
public class BalanceCheckService {

    private final TronBalanceService balanceService;
    private final TransferPersistenceService persistenceService;
    private final TronClientHolder clientHolder;

    public BalanceCheckService(TronBalanceService balanceService,
                               TransferPersistenceService persistenceService,
                               TronClientHolder clientHolder) {
        this.balanceService = balanceService;
        this.persistenceService = persistenceService;
        this.clientHolder = clientHolder;
    }

    /**
     * Проверить балансы отправителя для конкретного перевода
     * и зафиксировать результат в БД.
     *
     * @param transfer перевод (ожидается в статусе PARSED)
     * @return созданная запись журнала с балансами
     */
    public TransactionLogEntity checkBalances(TransferEntity transfer) {
        String sender = clientHolder.senderAddress();

        // Читаем балансы отправителя из сети
        BigDecimal trxBalance = balanceService.getTrxBalance(sender);
        BigDecimal usdtBalance = balanceService.getUsdtBalance(sender);

        // Формируем запись журнала
        TransactionLogEntity logEntry = new TransactionLogEntity(
                transfer.getId(),
                transfer.getAmount(),
                TransferStatus.BALANCE_CHECKED
        );
        logEntry.setBalanceTrx(trxBalance);
        logEntry.setBalanceUsdt(usdtBalance);

        TransactionLogEntity saved = persistenceService.saveLog(logEntry);

        // Обновляем статус самого перевода
        persistenceService.updateStatus(transfer.getId(), TransferStatus.BALANCE_CHECKED);

        return saved;
    }
}