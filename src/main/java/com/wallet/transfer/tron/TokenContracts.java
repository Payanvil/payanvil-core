package com.wallet.transfer.tron;

import org.springframework.stereotype.Component;

/**
 * Контракт актуально выбранного токена в актуально выбранной сети.
 * <p>
 * Единственная точка, где адрес контракта связывается с сетью и токеном:
 * берёт их из {@link NetworkSelection} и {@link TokenSelection} (а не из
 * неизменяемых настроек), поэтому переключение во время работы сразу
 * отражается на адресе. Это исключает рассинхрон «клиент в одной сети,
 * контракт из другой».
 */
@Component
public class TokenContracts {

    private final NetworkSelection networkSelection;
    private final TokenSelection tokenSelection;

    public TokenContracts(NetworkSelection networkSelection, TokenSelection tokenSelection) {
        this.networkSelection = networkSelection;
        this.tokenSelection = tokenSelection;
    }

    /** Адрес контракта выбранного токена в текущей сети. */
    public String address() {
        return TokenContractCatalog.forNetwork(
                networkSelection.current(), tokenSelection.current());
    }

    /** Число знаков после запятой у выбранного токена. */
    public int decimals() {
        return tokenSelection.current().decimals();
    }
}
