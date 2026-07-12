package com.wallet.transfer.tron;

/**
 * Работа в запрошенной сети запрещена пропускным контролем
 * ({@link NetworkGate}) — например, mainnet без действующей лицензии.
 * <p>
 * Несёт идентификатор сети, чтобы вышестоящий слой мог показать
 * уместное сообщение (в т.ч. предложить активацию лицензии).
 */
public class NetworkNotAllowedException extends RuntimeException {

    private final String network;

    public NetworkNotAllowedException(String network, String message) {
        super(message);
        this.network = network;
    }

    /** Сеть, работа в которой была отклонена. */
    public String network() {
        return network;
    }
}
