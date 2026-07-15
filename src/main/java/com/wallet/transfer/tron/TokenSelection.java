package com.wallet.transfer.tron;

import com.wallet.transfer.domain.SupportedToken;
import org.springframework.stereotype.Component;

/**
 * Текущий выбранный токен перевода — изменяемое состояние времени выполнения.
 * <p>
 * Близнец {@link NetworkSelection}: интерфейс переключает токен, ядро читает
 * актуальное значение. По умолчанию USDT — исторический токен приложения.
 * <p>
 * Только хранит значение. Согласованность с сетью (в сети должен быть
 * известный контракт токена) обеспечивает вызывающая сторона.
 */
@Component
public class TokenSelection {

    private volatile SupportedToken current = SupportedToken.USDT;

    /** Актуальный токен перевода. */
    public SupportedToken current() {
        return current;
    }

    /** Сменить текущий токен. */
    public void select(SupportedToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Токен не задан");
        }
        this.current = token;
    }
}
