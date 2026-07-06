package com.wallet.transfer.tron;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Итог подтверждения транзакции: статус + фактические данные из сети.
 * <p>
 * Фактическая комиссия и время блока берутся из TransactionInfo при
 * подтверждении — это честные данные блокчейна (не оценка).
 * Для PENDING (не дождались) комиссия и время могут быть null.
 *
 * @param result      результат подтверждения (CONFIRMED / FAILED / PENDING)
 * @param feeTrx      фактическая комиссия в TRX (null, если ещё не подтверждено)
 * @param confirmedAt время блока — момент попадания транзакции в сеть (null для PENDING)
 */
public record ConfirmOutcome(
        ConfirmResult result,
        BigDecimal feeTrx,
        Instant confirmedAt
) {
}
