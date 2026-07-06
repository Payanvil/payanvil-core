package com.wallet.transfer.rate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки получения курса TRX→USDT. Блок rate: в application.yaml.
 *
 * @param timeoutSeconds   тайм-аут HTTP-запроса к источнику курса
 * @param binanceUrl       endpoint Binance ticker (пара TRXUSDT)
 * @param coingeckoUrl     endpoint CoinGecko (simple price, запасной)
 */
@ConfigurationProperties(prefix = "rate")
public record RateProperties(
        long timeoutSeconds,
        String binanceUrl,
        String coingeckoUrl
) {
}
