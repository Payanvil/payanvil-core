package com.wallet.transfer.tron;

import org.springframework.stereotype.Component;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Общий помощник повтора временных (transient) сбоев.
 * <p>
 * Не знает ничего про Tron: просто выполняет действие, и если оно бросило
 * <b>повторяемое</b> (по решению вызывающего) исключение — ждёт и пробует снова,
 * до исчерпания попыток. Неповторяемые исключения пробрасываются сразу.
 * <p>
 * Пауза нарастающая (base, base×2, base×4...). Без jitter: приложение
 * однопоточное, синхронных «штормов повторов» нет.
 */
@Component
public class NetworkRetry {

    /** Пауза при бане по частоте (429): TronGrid банит на 30с, ждём с запасом. */
    private static final long RATE_LIMIT_COOLDOWN_MS = 35_000L;

    private final RateLimiter rateLimiter;

    public NetworkRetry(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * Признак бана по частоте запросов (HTTP 429 / «frequency limit»).
     * TronGrid при превышении лимита подвешивает ключ на 30 секунд —
     * короткими паузами это не переждать, нужна выдержка ≥30с.
     */
    public static boolean isRateLimited(RuntimeException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("429") || msg.contains("frequency limit");
    }

    /**
     * Универсальный классификатор временных сетевых сбоев.
     * Повторять стоит только заведомо сетевые/временные ошибки:
     * недоступность узла, таймаут, временную занятость.
     * Постоянные ошибки (валидация, подпись, истечение) — не повторяем.
     *
     * @param e исключение
     * @return true, если сбой временный и повтор уместен
     */
    public static boolean isTransientNetworkError(RuntimeException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("UNAVAILABLE")
                || msg.contains("DEADLINE_EXCEEDED")
                || msg.contains("SERVER_BUSY")
                || msg.contains("NO_CONNECTION")
                || msg.contains("NOT_ENOUGH_EFFECTIVE_CONNECTION");
    }

    /**
     * Выполнить действие с повтором временных сбоев.
     *
     * @param action      что выполнить (возвращает результат)
     * @param isRetryable предикат: стоит ли повторять при данном исключении
     * @param maxAttempts максимум попыток (включая первую)
     * @param baseDelayMs базовая пауза между попытками в мс
     * @param <T>         тип результата
     * @return результат успешного выполнения
     * @throws RetryExhaustedException если все попытки исчерпаны
     */
    public <T> T execute(Supplier<T> action,
                         Predicate<RuntimeException> isRetryable,
                         int maxAttempts,
                         long baseDelayMs) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            rateLimiter.acquire(); // выдержать темп перед каждым обращением к ноде
            try {
                return action.get();
            } catch (RuntimeException e) {
                lastError = e;

                // Неповторяемую ошибку пробрасываем сразу
                if (!isRetryable.test(e)) {
                    throw e;
                }

                // Если попытки ещё остались — ждём перед повтором.
                if (attempt < maxAttempts) {
                    // Бан по частоте требует длинной выдержки (≥30с);
                    // прочие временные сбои — нарастающая пауза.
                    long delay = isRateLimited(e)
                            ? RATE_LIMIT_COOLDOWN_MS
                            : baseDelayMs * (1L << (attempt - 1)); // base × 2^(attempt-1)
                    sleep(delay);
                }
            }
        }

        throw new RetryExhaustedException(
                "Исчерпаны попытки повтора (" + maxAttempts + ")", lastError);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Повтор прерван", e);
        }
    }
}
