package com.wallet.transfer.rate;

import java.util.Optional;

/**
 * Источник курса TRX→USDT. Реализации: Binance (основной), CoinGecko
 * (запасной); в перспективе — Chainlink on-chain оракул на Tron.
 */
public interface TrxRateProvider {

    /** Человекочитаемое имя источника (для логов и отчёта). */
    String name();

    /**
     * Получить текущий курс.
     *
     * @return курс или empty, если источник недоступен/ответ невалиден
     *         (оркестратор тогда пробует следующий источник)
     */
    Optional<TrxRate> fetch();
}
