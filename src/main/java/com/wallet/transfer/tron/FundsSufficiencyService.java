package com.wallet.transfer.tron;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Проверка достаточности средств для перевода.
 * <p>
 * Чистая логика: НЕ ходит в сеть. Получает свежие балансы и оценку
 * комиссии готовыми (их добывает оркестратор) и выносит вердикт.
 * Это быстрый фильтр перед попыткой; финальный арбитр — сама сеть.
 */
@Service
public class FundsSufficiencyService {

    private final BigDecimal balanceCheckBuffer;

    public FundsSufficiencyService(TronProperties properties) {
        this.balanceCheckBuffer = properties.balanceCheckBuffer();
    }

    /**
     * Проверить, хватает ли средств отправителя на перевод.
     *
     * @param amountUsdt  сумма перевода в USDT
     * @param usdtBalance баланс USDT отправителя
     * @param trxBalance  баланс TRX отправителя
     * @param feeTrx      оценка комиссии в TRX (без буфера)
     * @return вердикт с детализацией
     */
    public FundsVerdict check(BigDecimal amountUsdt,
                              BigDecimal usdtBalance,
                              BigDecimal trxBalance,
                              BigDecimal feeTrx) {
        BigDecimal requiredTrx = feeTrx.multiply(balanceCheckBuffer);

        boolean usdtOk = usdtBalance.compareTo(amountUsdt) >= 0;
        boolean trxOk = trxBalance.compareTo(requiredTrx) >= 0;

        return new FundsVerdict(
                usdtOk && trxOk,
                usdtOk,
                trxOk,
                amountUsdt,
                usdtBalance,
                requiredTrx,
                trxBalance
        );
    }
}
