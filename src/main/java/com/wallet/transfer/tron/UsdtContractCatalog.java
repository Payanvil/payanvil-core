package com.wallet.transfer.tron;

/**
 * Известные адреса контракта USDT (TRC20) по сетям.
 * Выбор по сети, а не из конфигурации, исключает класс ошибок
 * «боевая сеть с тестовым контрактом» и наоборот.
 */
public final class UsdtContractCatalog {

    /** Боевой Tether: официальный контракт USDT в mainnet. */
    public static final String MAINNET = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

    /** Тестовый USDT в Nile. */
    public static final String NILE = "TXYZopYRdj2D9XRtbG411XZZ3kM5VkAeBf";

    private UsdtContractCatalog() {
    }

    /** Адрес контракта для сети; неизвестная сеть → исключение. */
    public static String forNetwork(String network) {
        return switch (network) {
            case "mainnet" -> MAINNET;
            case "nile" -> NILE;
            default -> throw new IllegalArgumentException(
                    "Нет известного контракта USDT для сети: " + network);
        };
    }
}
