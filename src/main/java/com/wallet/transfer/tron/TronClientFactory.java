package com.wallet.transfer.tron;

import org.springframework.stereotype.Component;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.key.KeyPair;

import java.util.regex.Pattern;

/**
 * Фабрика клиентов trident. Создаёт ApiWrapper по приватному ключу,
 * переданному в момент запуска перевода, а не при старте приложения.
 * <p>
 * Вызывающая сторона отвечает за close() созданного клиента
 * (try-with-resources или закрытие после завершения пакета).
 */
@Component
public class TronClientFactory {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final TronProperties properties;
    private final NetworkGate networkGate;
    private final NetworkSelection networkSelection;

    public TronClientFactory(TronProperties properties, NetworkGate networkGate, NetworkSelection networkSelection) {
        this.properties = properties;
        this.networkGate = networkGate;
        this.networkSelection = networkSelection;
    }

    /**
     * Создаёт клиент для сети из настроек. Ключ принимается как char[]
     * и зачищается после использования.
     */
    public ApiWrapper create(char[] privateKey) {
        String network = networkSelection.current();
        networkGate.ensureAllowed(network);
        String key = validateAndConvert(privateKey);
        return switch (network) {
            case "nile" -> ApiWrapper.ofNile(key);
            case "shasta" -> ApiWrapper.ofShasta(key);
            case "mainnet" -> {
                String apiKey = properties.apiKey();
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "Mainnet требует API-ключ TronGrid: задайте TRONGRID_API_KEY");
                }
                yield ApiWrapper.ofMainnet(key, apiKey);
            }
            default -> throw new IllegalArgumentException(
                    "Неизвестная сеть Tron: " + network
                            + ". Допустимо: nile, shasta, mainnet");
        };
    }

    /**
     * Выводит base58-адрес отправителя из ключа — для сверки
     * пользователем перед первой транзакцией. Клиент не создаётся.
     */
    public String deriveAddress(char[] privateKey) {
        String key = validateAndConvert(privateKey);
        return new KeyPair(key).toBase58CheckAddress();
    }

    private String validateAndConvert(char[] privateKey) {
        if (privateKey == null || privateKey.length == 0) {
            throw new IllegalArgumentException("Приватный ключ не задан");
        }
        String key = new String(privateKey);
        java.util.Arrays.fill(privateKey, '\0');
        if (!HEX_64.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Приватный ключ должен быть 64 hex-символа");
        }
        return key;
    }
}
