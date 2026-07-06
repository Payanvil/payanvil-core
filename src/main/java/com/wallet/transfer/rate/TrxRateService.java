package com.wallet.transfer.rate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Оркестратор получения курса TRX→USDT.
 * <p>
 * Перебирает источники в порядке {@code @Order}: основной (Binance),
 * затем запасной (CoinGecko). Возвращает курс первого ответившего;
 * если молчат все — empty (вызывающий решает, что делать — например,
 * заблокировать пакет, раз курс не получить).
 * <p>
 * Spring внедряет провайдеры списком, отсортированным по {@code @Order},
 * поэтому добавление нового источника (например, Chainlink-оракул) не
 * требует правки этого класса — достаточно пометить его {@code @Order}.
 */
@Service
public class TrxRateService {

    private static final Logger log = LoggerFactory.getLogger(TrxRateService.class);

    private final List<TrxRateProvider> providers;

    public TrxRateService(List<TrxRateProvider> providers) {
        this.providers = providers;
    }

    /**
     * Получить курс из первого доступного источника.
     *
     * @return курс или empty, если ни один источник не ответил
     */
    public Optional<TrxRate> fetchRate() {
        for (TrxRateProvider provider : providers) {
            Optional<TrxRate> rate = provider.fetch();
            if (rate.isPresent()) {
                log.info("Курс TRX→USDT получен от {}: {}",
                        provider.name(), rate.get().price());
                return rate;
            }
            log.warn("Источник {} не дал курс, пробуем следующий", provider.name());
        }
        log.error("Ни один источник курса TRX→USDT не доступен");
        return Optional.empty();
    }
}
