package com.wallet.transfer.chain;

import com.wallet.transfer.domain.SupportedToken;

import java.util.Objects;

/**
 * Метаданные пакета переводов, единые для всех переводов одного пакета.
 * Известны в момент сборки пакета и проставляются каждому переводу при сохранении.
 * <p>
 * Токен фиксируется здесь, потому что он выбран на момент сборки пакета:
 * история должна показывать, чем платили тогда, а не что выбрано сейчас.
 *
 * @param chain  блокчейн-сеть, в которую отправляется пакет
 * @param token  токен, которым отправляется пакет
 * @param source источник платёжных данных (файл/Telegram) для аудита
 */
public record BatchContext(SupportedChain chain, SupportedToken token, TransferSource source) {

    public BatchContext {
        Objects.requireNonNull(chain, "chain must not be null");
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(source, "source must not be null");
    }
}
