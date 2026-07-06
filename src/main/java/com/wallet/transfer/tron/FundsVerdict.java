package com.wallet.transfer.tron;

import java.math.BigDecimal;

/**
 * Результат проверки достаточности средств для одного перевода.
 *
 * @param sufficient     хватает ли средств в целом (USDT и TRX)
 * @param usdtSufficient хватает ли USDT на сумму перевода
 * @param trxSufficient  хватает ли TRX на комиссию (с буфером)
 * @param requiredUsdt   требуется USDT (сумма перевода)
 * @param availableUsdt  доступно USDT на балансе
 * @param requiredTrx    требуется TRX (комиссия × буфер)
 * @param availableTrx   доступно TRX на балансе
 */
public record FundsVerdict(
        boolean sufficient,
        boolean usdtSufficient,
        boolean trxSufficient,
        BigDecimal requiredUsdt,
        BigDecimal availableUsdt,
        BigDecimal requiredTrx,
        BigDecimal availableTrx
) {
}
