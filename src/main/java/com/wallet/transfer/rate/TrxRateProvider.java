package com.wallet.transfer.rate;

import com.wallet.transfer.domain.SupportedToken;

import java.util.Optional;

/**
 * Источник курса TRX к токену перевода. Реализации: Binance (основной),
 * CoinGecko (запасной); в перспективе — Chainlink on-chain оракул на Tron.
 */
public interface TrxRateProvider {

    /** Человекочитаемое имя источника (для логов и отчёта). */
    String name();

    /**
     * Получить текущий курс TRX в единицах токена перевода.
     *
     * @param token токен, в котором нужен курс
     * @return курс или empty, если источник недоступен/ответ невалиден
     *         (оркестратор тогда пробует следующий источник)
     */
    Optional<TrxRate> fetch(SupportedToken token);
}
