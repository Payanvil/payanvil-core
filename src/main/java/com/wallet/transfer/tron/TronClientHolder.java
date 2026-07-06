package com.wallet.transfer.tron;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.tron.trident.core.ApiWrapper;

/**
 * Держатель клиента Tron, созданного в рантайме.
 * <p>
 * Жизненный цикл: пользователь вводит приватный ключ →
 * {@link #initialize(char[])} создаёт клиент и выводит адрес отправителя →
 * сервисы работают через {@link #client()} и {@link #senderAddress()} →
 * {@link #close()} вызывается при смене кошелька или завершении сессии.
 * <p>
 * До инициализации обращение к клиенту или адресу — ошибка программиста,
 * а не пользователя: бросаем IllegalStateException с понятным текстом.
 */
@Component
public class TronClientHolder {

    private final TronClientFactory factory;

    private volatile ApiWrapper client;
    private volatile String senderAddress;

    public TronClientHolder(TronClientFactory factory) {
        this.factory = factory;
    }

    /**
     * Создаёт клиент по приватному ключу и запоминает адрес отправителя.
     * Ключ зачищается внутри фабрики.
     *
     * @return base58-адрес отправителя — для показа пользователю
     */
    public synchronized String initialize(char[] privateKey) {
        if (client != null) {
            throw new IllegalStateException("Клиент Tron уже инициализирован");
        }
        // Адрес выводим до создания клиента: ключ нужен дважды,
        // а фабрика зачищает массив — поэтому копия.
        char[] keyCopy = privateKey.clone();
        this.senderAddress = factory.deriveAddress(keyCopy);
        this.client = factory.create(privateKey);
        return senderAddress;
    }

    public ApiWrapper client() {
        ApiWrapper c = client;
        if (c == null) {
            throw new IllegalStateException(
                    "Клиент Tron не инициализирован: сначала вызовите initialize()");
        }
        return c;
    }

    public String senderAddress() {
        String s = senderAddress;
        if (s == null) {
            throw new IllegalStateException(
                    "Адрес отправителя неизвестен: сначала вызовите initialize()");
        }
        return s;
    }

    /** Инициализирован ли клиент (введён ли ключ в этой сессии). */
    public boolean isInitialized() {
        return client != null;
    }

    /** Закрывает gRPC-соединение и сбрасывает состояние. Повторный вызов безопасен. */
    @PreDestroy
    public synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
            senderAddress = null;
        }
    }
}
