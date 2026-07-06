package com.wallet.transfer.tron;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Настройки подключения к сети Tron.
 * Отображается на блок tron: в application.yaml.
 *
 * @param network            сеть: nile, shasta или mainnet
 * @param apiKey             API-ключ TronGrid (обязателен для mainnet)
 * @param recipientAddress   адрес тестового получателя (из .env, для разведки/тестов)
 * @param usdtDecimals       число знаков после запятой у USDT (для Tether = 6)
 * @param balanceCheckBuffer множитель запаса TRX при проверке достаточности средств
 * @param confirmationTimeoutSeconds тайм-аут 60сек на подтверждение из сети
 */
@ConfigurationProperties(prefix = "tron")
public record TronProperties(
        String network,
        String apiKey,
        String recipientAddress,
        int usdtDecimals,
        BigDecimal balanceCheckBuffer,
        long confirmationTimeoutSeconds
) {
    /**
     * Адрес контракта USDT — всегда из каталога по сети.
     * Не настраивается извне: это исключает запуск
     * «боевая сеть с тестовым контрактом» и наоборот.
     */
    public String usdtContractAddress() {
        return UsdtContractCatalog.forNetwork(network);
    }
}