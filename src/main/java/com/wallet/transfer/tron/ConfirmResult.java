package com.wallet.transfer.tron;

/**
 * Итог опроса статуса транзакции в сети.
 */
public enum ConfirmResult {
    /** Транзакция подтверждена и успешна. */
    CONFIRMED,
    /** Транзакция исполнилась с ошибкой (revert, out-of-energy и т.п.). */
    FAILED,
    /** За отведённое время не подтвердилась (ещё не в блоке). Не вердикт — ждём дальше. */
    PENDING
}