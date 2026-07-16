package com.wallet.transfer.domain;

import java.math.BigDecimal;

/**
 * Перевод, подготовленный к обработке: распарсен, сохранён, получил
 * идентификатор и разметку комиссии.
 * <p>
 * Следующая стадия жизни после {@link WalletTransfer} (входной команды):
 * тот описывает «что отправить», этот — конкретный отслеживаемый перевод,
 * с которым работают сервисы отправки. Открытое ядро оперирует этим типом,
 * не зная о персистентной сущности закрытой части.
 *
 * @param id              идентификатор перевода (ключ для журнала и статусов)
 * @param recipientName   имя получателя (для результатов и отчётов)
 * @param walletAddress   адрес кошелька получателя
 * @param amount          сумма перевода, USDT
 * @param feePayer        кто платит сетевую комиссию
 * @param deductedFee   комиссия в токене перевода, вычтенная из суммы
 *                      (режим «платит получатель»), иначе ноль
 */
public record PreparedTransfer(
        Long id,
        String recipientName,
        String walletAddress,
        BigDecimal amount,
        FeePayer feePayer,
        BigDecimal deductedFee) {
}
