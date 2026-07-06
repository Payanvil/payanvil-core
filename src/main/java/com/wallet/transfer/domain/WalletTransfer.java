package com.wallet.transfer.domain;

import java.math.BigDecimal;

/**
 * Доменная модель: один перевод USDT в сети Tron.
 * <p>
 * Поля делятся на две группы:
 * <ul>
 *   <li><b>Критичные</b> (walletAddress, amount) — участвуют в блокчейн-транзакции,
 *       валидируются строго.</li>
 *   <li><b>Метаданные</b> (recipientName, purpose) — нужны только для отчёта,
 *       в блокчейн не отправляются, могут быть пустыми.</li>
 * </ul>
 * Иммутабельная по природе record-а. Валидация в компактном конструкторе
 * гарантирует, что объект не может существовать в невалидном состоянии.
 */
public record WalletTransfer(
        String recipientName,
        String walletAddress,
        String purpose,
        BigDecimal amount,
        FeePayer feePayer
) {

    public WalletTransfer {
        // Критичные поля — строгая валидация
        if (walletAddress == null || walletAddress.isBlank()) {
            throw new IllegalArgumentException("Wallet address must not be empty");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive, got: " + amount + " for " + walletAddress);
        }

        // Метаданные — нормализуем null в пустую строку,
        // чтобы в отчёте не было слова "null"
        recipientName = (recipientName == null) ? "" : recipientName.trim();
        purpose = (purpose == null) ? "" : purpose.trim();

        // Кто платит комиссию: по умолчанию отправитель (безопасно).
        feePayer = (feePayer == null) ? FeePayer.SENDER : feePayer;
    }

    /**
     * Конструктор без флага комиссии — ставит SENDER по умолчанию.
     * Сохраняет совместимость с парсерами и очередью, создающими перевод
     * до того, как пользователь пометит режим комиссии в UI.
     */
    public WalletTransfer(String recipientName, String walletAddress,
                          String purpose, BigDecimal amount) {
        this(recipientName, walletAddress, purpose, amount, FeePayer.SENDER);
    }

    /** Копия перевода с другим плательщиком комиссии (record иммутабелен). */
    public WalletTransfer withFeePayer(FeePayer newFeePayer) {
        return new WalletTransfer(recipientName, walletAddress, purpose, amount, newFeePayer);
    }
}
