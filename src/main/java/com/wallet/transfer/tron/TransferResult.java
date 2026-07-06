package com.wallet.transfer.tron;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Результат обработки одного перевода в пачке.
 *
 * @param transferId идентификатор перевода в БД
 * @param recipient  адрес получателя
 * @param amount     сумма перевода в USDT
 * @param outcome    итог обработки
 * @param txid       хэш транзакции (null, если не отправляли)
 * @param feeTrx     фактическая комиссия в TRX (null, если не подтверждён)
 * @param sentAt     время попадания в блок (null, если не подтверждён)
 * @param detailKey  ключ причины (report.reason.*) для перевода на язык отчёта
 * @param detailArg  динамическая часть причины (текст исключения; может быть null)
 */
public record TransferResult(
        Long transferId,
        String recipient,
        BigDecimal amount,
        TransferOutcome outcome,
        String txid,
        BigDecimal feeTrx,
        Instant sentAt,
        String detailKey,
        String detailArg
) {
}