package com.wallet.transfer.tron;

import java.math.BigDecimal;

/**
 * Результат отправки перевода.
 *
 * @param txid       хэш транзакции в сети
 * @param feeTrx     оценка комиссии в TRX (на момент отправки)
 * @param energyUsed оценка energy
 */
public record SendResult(String txid, BigDecimal feeTrx, long energyUsed) {
}
