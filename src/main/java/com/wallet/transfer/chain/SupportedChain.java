package com.wallet.transfer.chain;

import java.util.Arrays;
import java.util.List;

/**
 * Блокчейн-сети, в которых приложение реально выполняет отправку токенов.
 * Плашка поддерживаемых сетей на лаунчере генерится из этого списка.
 * <p>
 * Принцип «по чесноку»: добавлять константу можно только когда отправка
 * в эту сеть действительно работает, а не «в планах».
 */
public enum SupportedChain {

    TRON("Tron", "TRC20");
    // Заготовки на будущее — включать, ТОЛЬКО когда отправка реально заработает:
    // BNB("BNB Chain", "BEP20"),
    // SOLANA("Solana", "SPL");

    private final String displayName;
    private final String standard;

    SupportedChain(String displayName, String standard) {
        this.displayName = displayName;
        this.standard = standard;
    }

    public String displayName() {
        return displayName;
    }

    public String standard() {
        return standard;
    }

    /** Подпись для пилюли: «Tron (TRC20)». */
    public String label() {
        return displayName + " (" + standard + ")";
    }

    /** Все поддерживаемые сети в порядке объявления. */
    public static List<SupportedChain> all() {
        return Arrays.asList(values());
    }
}
