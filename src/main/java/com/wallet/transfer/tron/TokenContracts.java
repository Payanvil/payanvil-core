package com.wallet.transfer.tron;

import org.springframework.stereotype.Component;

/**
 * Контракт токена перевода для актуально выбранной сети.
 * <p>
 * Единственная точка, где адрес контракта связывается с сетью: берёт сеть
 * из {@link NetworkSelection} (а не из неизменяемых настроек), поэтому
 * переключение сети во время работы сразу отражается на адресе. Это
 * исключает рассинхрон «клиент в одной сети, контракт из другой».
 */
@Component
public class TokenContracts {

    private final NetworkSelection networkSelection;
    private final TronProperties properties;

    public TokenContracts(NetworkSelection networkSelection, TronProperties properties) {
        this.networkSelection = networkSelection;
        this.properties = properties;
    }

    /** Адрес контракта токена в текущей сети. */
    public String address() {
        return UsdtContractCatalog.forNetwork(networkSelection.current());
    }

    /** Число знаков после запятой у токена. */
    public int decimals() {
        return properties.usdtDecimals();
    }
}
