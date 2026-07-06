package com.wallet.transfer.tron;

/**
 * Фаза обработки пачки для индикации прогресса.
 * SENDING — Проход 1 (отправка), CONFIRMING — Проход 2 (подтверждение).
 */
public enum ProgressPhase {
    SENDING,
    CONFIRMING
}
