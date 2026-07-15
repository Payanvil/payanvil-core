package com.wallet.transfer.tron;

import com.wallet.transfer.domain.SupportedToken;

import java.util.List;

/**
 * Известные адреса контрактов токенов (TRC20) по сетям.
 * Выбор по сети и токену, а не из конфигурации, исключает класс ошибок
 * «боевая сеть с тестовым контрактом» и наоборот.
 */
public final class TokenContractCatalog {

    /** Боевой Tether: официальный контракт USDT в mainnet. */
    public static final String USDT_MAINNET = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

    /** Тестовый USDT в Nile. */
    public static final String USDT_NILE = "TXYZopYRdj2D9XRtbG411XZZ3kM5VkAeBf";

    /** Боевой Circle: официальный контракт USDC в mainnet. */
    public static final String USDC_MAINNET = "TEkxiTehnzSmSe2XqrBj4w32RUN966rdz8";

    /** Тестовый USDC в Nile. */
    public static final String USDC_NILE = "TEMVynQpntMqkPxP6wXTW2K7e4sM3cRmWz";

    private TokenContractCatalog() {
    }

    /**
     * Адрес контракта токена в сети.
     *
     * @throws IllegalArgumentException если пара сеть+токен неизвестна
     */
    public static String forNetwork(String network, SupportedToken token) {
        return switch (network) {
            case "mainnet" -> switch (token) {
                case USDT -> USDT_MAINNET;
                case USDC -> USDC_MAINNET;
            };
            case "nile" -> switch (token) {
                case USDT -> USDT_NILE;
                case USDC -> USDC_NILE;
            };
            default -> throw new IllegalArgumentException(
                    "Нет известных контрактов для сети: " + network);
        };
    }

    /**
     * Токены, доступные в сети: интерфейс предлагает только то,
     * для чего есть проверенный адрес контракта.
     *
     * @return список токенов; пустой, если сеть неизвестна
     */
    public static List<SupportedToken> tokensOf(String network) {
        return switch (network) {
            case "mainnet", "nile" -> List.of(SupportedToken.USDT, SupportedToken.USDC);
            default -> List.of();
        };
    }
}
