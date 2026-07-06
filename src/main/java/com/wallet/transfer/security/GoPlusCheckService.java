package com.wallet.transfer.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.transfer.chain.SupportedChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Проверка адресов через GoPlus Malicious Address API — второй слой
 * безопасности поверх нативного чёрного списка USDT.
 * <p>
 * Бесплатный публичный API без ключа: один GET на адрес, плоский JSON
 * с риск-флагами ("0"/"1"). Риском считается ядро жёстких флагов
 * (криминал/санкции); мягкие сигналы (honeypot_related_address,
 * fake_kyc и т.п.) игнорируются — для получателя платежа они спорны.
 * <p>
 * Деградация: при недоступности API / таймауте / кривом ответе возвращается
 * чистый вердикт с warn-логом — внешний сервис не должен останавливать
 * отправку, нативный слой продолжает работать.
 */
@Service
public class GoPlusCheckService {

    private static final Logger log = LoggerFactory.getLogger(GoPlusCheckService.class);

    /** Ядро жёстких флагов: криминальные и санкционные маркеры. */
    static final List<String> CORE_RISK_FLAGS = List.of(
            "blacklist_doubt",
            "phishing_activities",
            "stealing_attack",
            "blackmail_activities",
            "cybercrime",
            "money_laundering",
            "financial_crime",
            "darkweb_transactions",
            "sanctioned",
            "mixer");

    private static final String ENDPOINT =
            "https://api.gopluslabs.io/api/v1/address_security/%s?chain_id=%s";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GoPlusCheckService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Проверить набор адресов с дедупликацией: один HTTP-вызов на уникальный
     * адрес. Порядок не гарантируется; для адресов без рисков вердикт чистый.
     *
     * @param addresses адреса получателей (могут повторяться)
     * @return вердикт по каждому уникальному адресу
     */
    public Map<String, GoPlusVerdict> checkAll(List<String> addresses, SupportedChain chain) {
        Set<String> unique = new LinkedHashSet<>(addresses);
        Map<String, GoPlusVerdict> verdicts = new HashMap<>();
        for (String address : unique) {
            verdicts.put(address, check(address, chain));
        }
        long risky = verdicts.values().stream().filter(GoPlusVerdict::risky).count();
        log.info("GoPlus: проверено адресов: {}, рискованных: {}", unique.size(), risky);
        return verdicts;
    }

    /**
     * Проверить один адрес. Любой сбой (сеть, таймаут, неожиданный ответ) —
     * чистый вердикт с warn-логом: деградация без остановки отправки.
     */
    public GoPlusVerdict check(String address, SupportedChain chain) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT.formatted(address, goPlusChainId(chain))))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GoPlus: HTTP {} для адреса {} — пропускаем проверку",
                        response.statusCode(), address);
                return GoPlusVerdict.clean();
            }
            return parseVerdict(response.body(), address);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GoPlus: проверка адреса {} прервана", address);
            return GoPlusVerdict.clean();
        } catch (Exception e) {
            log.warn("GoPlus: недоступен для адреса {} ({}) — пропускаем проверку",
                    address, e.getMessage());
            return GoPlusVerdict.clean();
        }
    }

    /** Разбор ответа: {"code":1,"message":"ok","result":{флаги "0"/"1", data_source}}. */
    GoPlusVerdict parseVerdict(String body, String address) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.path("result");
            if (result.isMissingNode() || result.isNull()) {
                log.warn("GoPlus: пустой result для адреса {} — пропускаем проверку", address);
                return GoPlusVerdict.clean();
            }
            List<String> fired = CORE_RISK_FLAGS.stream()
                    .filter(flag -> "1".equals(result.path(flag).asText()))
                    .toList();
            if (fired.isEmpty()) {
                return GoPlusVerdict.clean();
            }
            String dataSource = result.path("data_source").asText(null);
            return new GoPlusVerdict(true, fired, dataSource);
        } catch (Exception e) {
            log.warn("GoPlus: не удалось разобрать ответ для адреса {} ({}) — пропускаем",
                    address, e.getMessage());
            return GoPlusVerdict.clean();
        }
    }

    /**
     * Идентификатор сети в терминах GoPlus API. Для EVM-сетей GoPlus ждёт
     * числовой chain id, для не-EVM — имя (напр. "tron", "solana");
     * при добавлении сети в SupportedChain сюда добавляется её маппинг.
     */
    private static String goPlusChainId(SupportedChain chain) {
        return switch (chain) {
            case TRON -> "tron";
        };
    }
}
