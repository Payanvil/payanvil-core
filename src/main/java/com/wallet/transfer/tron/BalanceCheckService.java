package com.wallet.transfer.tron;

import com.wallet.transfer.domain.PreparedTransfer;
import com.wallet.transfer.domain.TransferLogPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Сервис проверки балансов перед переводом.
 * <p>
 * Оркестратор: читает балансы отправителя из сети и фиксирует их
 * в журнале через порт {@link TransferLogPort}, не зная о способе
 * хранения. Сам не лезет ни в gRPC, ни в SQL напрямую.
 * <p>
 * Для каждого перевода читает балансы TRX и USDT кошелька-отправителя,
 * фиксирует их в журнале и переводит перевод в статус BALANCE_CHECKED.
 */
@Service
public class BalanceCheckService {

    private final TronBalanceService balanceService;
    private final TransferLogPort transferLog;
    private final TronClientHolder clientHolder;

    public BalanceCheckService(TronBalanceService balanceService,
                               TransferLogPort transferLog,
                               TronClientHolder clientHolder) {
        this.balanceService = balanceService;
        this.transferLog = transferLog;
        this.clientHolder = clientHolder;
    }

    /**
     * Проверить балансы отправителя для конкретного перевода
     * и зафиксировать результат в журнале.
     *
     * @param transfer перевод (ожидается в статусе PARSED)
     * @return id созданной записи журнала
     */
    public long checkBalances(PreparedTransfer transfer) {
        String sender = clientHolder.senderAddress();

        BigDecimal trxBalance = balanceService.getTrxBalance(sender);
        BigDecimal usdtBalance = balanceService.getUsdtBalance(sender);

        return transferLog.logBalanceChecked(
                transfer.id(), transfer.amount(), trxBalance, usdtBalance);
    }
}