package com.wallet.transfer.chain;

import java.util.Objects;

/**
 * Метаданные пакета переводов, единые для всех переводов одного пакета.
 * Известны в момент сборки пакета и проставляются каждому переводу при сохранении.
 * <p>
 * Носитель «на будущее»: при мультичейне сюда добавится выбор токена
 * (USDT/USDC/…) и прочая метадата пакета — без изменения сигнатур
 * в цепочке обработки, которая уже принимает этот контекст целиком.
 *
 * @param chain  блокчейн-сеть, в которую отправляется пакет
 * @param source источник платёжных данных (файл/Telegram) для аудита
 */
public record BatchContext(SupportedChain chain, TransferSource source) {

    public BatchContext {
        Objects.requireNonNull(chain, "chain must not be null");
        Objects.requireNonNull(source, "source must not be null");
    }
}
