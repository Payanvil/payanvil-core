package com.wallet.transfer.tron;

/**
 * Бросается, когда все попытки повтора исчерпаны, а действие так и не удалось.
 * Хранит исходную причину последней неудачной попытки.
 */
public class RetryExhaustedException extends RuntimeException {

    public RetryExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
