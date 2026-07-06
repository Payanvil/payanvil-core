package com.wallet.transfer.rate;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Зафиксированный курс TRX→USDT.
 *
 * @param price     цена 1 TRX в USDT (например, 0.32)
 * @param fetchedAt момент получения курса
 * @param source    источник («Binance», «CoinGecko») — для прозрачности в отчёте
 */
public record TrxRate(BigDecimal price, Instant fetchedAt, String source) {

    public TrxRate {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("TRX rate must be positive, got: " + price);
        }
    }
}
