package com.wallet.transfer.tron;

import com.wallet.transfer.domain.SupportedToken;
import org.springframework.stereotype.Component;

/**
 * Текущий выбранный токен перевода — изменяемое состояние времени выполнения.
 * <p>
 * Близнец {@link NetworkSelection}: интерфейс переключает токен, ядро читает
 * актуальное значение. По умолчанию USDT — исторический токен приложения.
 * <p>
 * Хранит значение и следит за согласованностью с сетью: выбрать токен,
 * контракт которого в текущей сети неизвестен, нельзя.
 */
@Component
public class TokenSelection {

    private final NetworkSelection networkSelection;

    private volatile SupportedToken current = SupportedToken.USDT;

    public TokenSelection(NetworkSelection networkSelection) {
        this.networkSelection = networkSelection;
    }

    /** Актуальный токен перевода. */
    public SupportedToken current() {
        return current;
    }

    /**
     * Сменить текущий токен.
     *
     * @param token токен, доступный в текущей сети
     * @throws IllegalArgumentException если токен не задан или в текущей сети
     *                                  его контракт неизвестен
     */
    public void select(SupportedToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Токен не задан");
        }
        String network = networkSelection.current();
        if (!TokenContractCatalog.tokensOf(network).contains(token)) {
            throw new IllegalArgumentException(
                    "Токен " + token + " недоступен в сети " + network);
        }
        this.current = token;
    }
}
