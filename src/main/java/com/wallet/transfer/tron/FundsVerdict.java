package com.wallet.transfer.tron;

import java.math.BigDecimal;

/**
 * Результат проверки достаточности средств для одного перевода.
 *
 * @param sufficient      хватает ли средств в целом (токен перевода и TRX)
 * @param tokenSufficient хватает ли токена на сумму перевода
 * @param trxSufficient   хватает ли TRX на комиссию (с буфером)
 * @param requiredToken   требуется токена (сумма перевода)
 * @param availableToken  доступно токена на балансе
 * @param requiredTrx     требуется TRX (комиссия × буфер)
 * @param availableTrx    доступно TRX на балансе
 */
public record FundsVerdict(
        boolean sufficient,
        boolean tokenSufficient,
        boolean trxSufficient,
        BigDecimal requiredToken,
        BigDecimal availableToken,
        BigDecimal requiredTrx,
        BigDecimal availableTrx
) {
}
