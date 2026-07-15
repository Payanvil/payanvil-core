package com.wallet.transfer.rate;

import com.wallet.transfer.domain.SupportedToken;
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
 * Курс TRX к токену перевода с Binance public ticker (без API-ключа).
 * Символ пары собирается из шаблона в настройках: TRX{token} → TRXUSDT,
 * TRXUSDC. Ответ: {"symbol":"TRXUSDT","price":"0.32100000"}.
 */
@Component
@org.springframework.core.annotation.Order(1)
public class BinanceRateProvider implements TrxRateProvider {

    private static final Logger log = LoggerFactory.getLogger(BinanceRateProvider.class);

    private final RateProperties properties;
    private final HttpClient http;

    /** Тайм-аут по умолчанию, если в конфиге не задан (или задан некорректно). */
    private static final long DEFAULT_TIMEOUT_SECONDS = 5;

    public BinanceRateProvider(RateProperties properties) {
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
        return "Binance";
    }

    @Override
    public Optional<TrxRate> fetch(SupportedToken token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.binanceUrl().replace("{token}", token.name())))
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Binance вернул статус {}", response.statusCode());
                return Optional.empty();
            }

            return parsePrice(response.body())
                    .map(price -> new TrxRate(price, Instant.now(), name()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // восстановить флаг прерывания
            log.warn("Binance: запрос прерван");
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Binance: не удалось получить курс: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Извлечь цену из тела ответа Binance. Package-private для теста.
     *
     * @return цена или empty, если поле отсутствует/не число
     */
    Optional<BigDecimal> parsePrice(String body) {
        try {
            JsonNode root = JsonMapper.shared().readTree(body);
            JsonNode priceNode = root.get("price");
            if (priceNode == null || priceNode.isNull()) {
                log.warn("Binance: поле price отсутствует в ответе");
                return Optional.empty();
            }
            BigDecimal price = new BigDecimal(priceNode.asString());
            if (price.signum() <= 0) {
                log.warn("Binance: неположительная цена {}", price);
                return Optional.empty();
            }
            return Optional.of(price);
        } catch (Exception e) {
            log.warn("Binance: не удалось разобрать ответ: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
