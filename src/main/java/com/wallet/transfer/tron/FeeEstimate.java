package com.wallet.transfer.tron;

import java.math.BigDecimal;

/**
 * Результат оценки комиссии перевода (данные из сети, без буфера).
 *
 * @param energyUsed   оценка energy для перевода
 * @param sunPerEnergy текущая цена energy в sun за единицу
 * @param feeTrx       итоговая комиссия в TRX (energyUsed × sunPerEnergy / 1e6)
 */
public record FeeEstimate(long energyUsed, long sunPerEnergy, BigDecimal feeTrx) {
}
