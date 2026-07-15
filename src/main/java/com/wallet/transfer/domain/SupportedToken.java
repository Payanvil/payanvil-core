package com.wallet.transfer.domain;

/**
 * Токены, которыми умеет платить приложение в сети Tron.
 * <p>
 * Число знаков после запятой и имя функции чёрного списка — свойства самого
 * токена, а не настройки: держим рядом с ним, чтобы исключить рассинхрон
 * с контрактом.
 */
public enum SupportedToken {

    /** Tether: функция чёрного списка с заглавной L. */
    USDT(6, "isBlackListed"),

    /** Circle: функция чёрного списка со строчной l. */
    USDC(6, "isBlacklisted");

    private final int decimals;
    private final String blacklistFunction;

    SupportedToken(int decimals, String blacklistFunction) {
        this.decimals = decimals;
        this.blacklistFunction = blacklistFunction;
    }

    /** Число знаков после запятой у токена. */
    public int decimals() {
        return decimals;
    }

    /**
     * Имя view-функции контракта, отдающей флаг чёрного списка.
     * Написание у эмитентов различается — отсюда и хранение рядом с токеном.
     */
    public String blacklistFunction() {
        return blacklistFunction;
    }
}
