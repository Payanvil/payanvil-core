package com.wallet.transfer.tron;

import org.springframework.stereotype.Component;

/**
 * Текущая выбранная сеть Tron — изменяемое состояние времени выполнения.
 * <p>
 * {@link TronProperties} остаётся иммутабельным источником сети по умолчанию
 * (из конфигурации при старте); этот бин хранит сеть, которую пользователь
 * может переключить в интерфейсе уже во время работы. Читатели актуальной
 * сети (фабрика клиента, топ-бар, отчёт) обращаются сюда, а не к настройкам.
 * <p>
 * Только хранит значение — без оркестрации. Пересоздание клиента и сброс
 * кошелька при смене сети выполняет вызывающая сторона в приватном слое.
 */
@Component
public class NetworkSelection {

    private volatile String current;

    public NetworkSelection(TronProperties properties) {
        this.current = properties.network();
    }

    /** Актуальная сеть: nile, shasta или mainnet. */
    public String current() {
        return current;
    }

    /** Сменить текущую сеть. Валидацию значения выполняет вызывающая сторона. */
    /**
     * Сменить текущую сеть.
     *
     * @param network одна из: nile, shasta, mainnet
     * @throws IllegalArgumentException если сеть неизвестна
     */
    public void select(String network) {
        if (!"nile".equals(network)
                && !"shasta".equals(network)
                && !"mainnet".equals(network)) {
            throw new IllegalArgumentException(
                    "Неизвестная сеть Tron: " + network
                            + ". Допустимо: nile, shasta, mainnet");
        }
        this.current = network;
    }
}
