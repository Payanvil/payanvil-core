package com.wallet.transfer.tron;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ограничитель темпа запросов к ноде Tron.
 * <p>
 * TronGrid на бесплатном ключе допускает не более 15 запросов/сек, а при
 * превышении банит ключ на 30 секунд (HTTP 429). Этот дроссель выдерживает
 * минимальный интервал между запросами, держа частоту заведомо ниже потолка.
 * <p>
 * Темп настраивается через tron.max-requests-per-second (по умолчанию 5/сек —
 * втрое ниже лимита бесплатного ключа). Для платного тарифа с более высоким
 * потолком значение поднимается переменной окружения, код не меняется.
 * <p>
 * Приложение однопоточное по сетевым вызовам (пачка идёт последовательно),
 * но метод synchronized — на случай будущей многопоточности.
 */
@Component
public class RateLimiter {

    private final long minIntervalMs;
    private long lastRequestAt = 0L;

    public RateLimiter(
            @Value("${tron.max-requests-per-second:5}") double maxPerSecond) {
        if (maxPerSecond <= 0) {
            throw new IllegalArgumentException(
                    "tron.max-requests-per-second должно быть > 0, получено: " + maxPerSecond);
        }
        this.minIntervalMs = Math.round(1000.0 / maxPerSecond);
    }

    /**
     * Блокирует вызывающий поток ровно настолько, чтобы с момента прошлого
     * запроса прошёл минимальный интервал. Если интервал уже выдержан —
     * возвращается немедленно.
     */
    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestAt;
        if (elapsed < minIntervalMs) {
            sleep(minIntervalMs - elapsed);
        }
        lastRequestAt = System.currentTimeMillis();
    }

    /** Минимальный интервал между запросами в мс — для проверки в тестах. */
    long minIntervalMs() {
        return minIntervalMs;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ограничитель темпа прерван", e);
        }
    }
}
