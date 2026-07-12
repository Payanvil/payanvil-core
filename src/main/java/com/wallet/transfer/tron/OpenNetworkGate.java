package com.wallet.transfer.tron;

import org.springframework.stereotype.Component;

/**
 * Нейтральный пропускной контроль открытого ядра: разрешает любую сеть.
 * <p>
 * Это поведение открытой сборки — ядро полностью функционально и не
 * навязывает лицензирования. В полной сборке приложения бин подменяется
 * реализацией, требующей лицензию для отдельных сетей (помечена @Primary),
 * поэтому здесь никакой логики лицензий нет и быть не должно.
 */
@Component
public class OpenNetworkGate implements NetworkGate {

    @Override
    public void ensureAllowed(String network) {
        // Открытое ядро не ограничивает сети.
    }
}
