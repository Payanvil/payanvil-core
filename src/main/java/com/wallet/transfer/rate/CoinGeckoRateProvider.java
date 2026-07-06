package com.wallet.transfer.rate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Запасной источник курса TRX→USDT — CoinGecko (без API-ключа).
 * Ответ: {"tron":{"usd":0.32}}. Цена приходит числом, не строкой.
 */
@Component
@org.springframework.core.annotation.Order(2)
public class CoinGeckoRateProvider implements TrxRateProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoRateProvider.class);

    /** Тайм-аут по умолчанию, если в конфиге не задан (или некорректен). */
    private static final long DEFAULT_TIMEOUT_SECONDS = 5;

    private final RateProperties properties;
    private final HttpClient http;

    public CoinGeckoRateProvider(RateProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(effectiveTimeout(properties)))
                .build();
    }

    private static long effectiveTimeout(RateProperties properties) {
        long t = properties.timeoutSeconds();
        return t > 0 ? t : DEFAULT_TIMEOUT_SECONDS;
    }

    @Override
    public String name() {
        return "CoinGecko";
    }

    @Override
    public Optional<TrxRate> fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.coingeckoUrl()))
                    .timeout(Duration.ofSeconds(effectiveTimeout(properties)))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("CoinGecko вернул статус {}", response.statusCode());
                return Optional.empty();
            }

            return parsePrice(response.body())
                    .map(price -> new TrxRate(price, Instant.now(), name()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // восстановить флаг прерывания
            log.warn("CoinGecko: запрос прерван");
            return Optional.empty();
        } catch (Exception e) {
            log.warn("CoinGecko: не удалось получить курс: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Извлечь цену из тела ответа CoinGecko ({"tron":{"usd":0.32}}).
     * Package-private для теста.
     *
     * @return цена или empty, если структура неверна/значение не положительно
     */
    Optional<BigDecimal> parsePrice(String body) {
        try {
            JsonNode root = JsonMapper.shared().readTree(body);
            JsonNode usdNode = root.path("tron").path("usd");
            if (usdNode.isMissingNode() || usdNode.isNull() || !usdNode.isNumber()) {
                log.warn("CoinGecko: поле tron.usd отсутствует или не число");
                return Optional.empty();
            }
            BigDecimal price = usdNode.decimalValue();
            if (price.signum() <= 0) {
                log.warn("CoinGecko: неположительная цена {}", price);
                return Optional.empty();
            }
            return Optional.of(price);
        } catch (Exception e) {
            log.warn("CoinGecko: не удалось разобрать ответ: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
