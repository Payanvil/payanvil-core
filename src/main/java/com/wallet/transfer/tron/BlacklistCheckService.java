package com.wallet.transfer.tron;

import org.springframework.stereotype.Service;
import org.tron.trident.abi.FunctionReturnDecoder;
import org.tron.trident.abi.TypeReference;
import org.tron.trident.abi.datatypes.Address;
import org.tron.trident.abi.datatypes.Bool;
import org.tron.trident.abi.datatypes.Function;
import org.tron.trident.abi.datatypes.Type;
import org.tron.trident.proto.Response.TransactionExtention;
import org.tron.trident.utils.Numeric;
import com.wallet.transfer.domain.SupportedToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Проверка адреса в чёрном списке эмитента токена.
 * <p>
 * Контракты стейблкоинов хранят флаг блокировки и отдают его view-функцией;
 * имя функции у эмитентов различается (см. {@link SupportedToken}).
 * Заблокированному адресу перевод не пройдёт, поэтому получателей полезно
 * проверять до отправки.
 * <p>
 * Только для боевой сети: в тестовых сетях контракты токенов — упрощённые,
 * функции чёрного списка у них нет и списков как таковых не существует.
 * <p>
 * Операция чтения (view): без транзакции, подписи и затрат ресурсов.
 */
@Service
public class BlacklistCheckService {

    /** Повтор чтения из сети: максимум попыток. */
    private static final int READ_MAX_ATTEMPTS = 5;
    /** Повтор чтения: базовая пауза (мс), далее нарастает ×2. */
    private static final long READ_BASE_DELAY_MS = 1_000L;

    private static final Logger log = LoggerFactory.getLogger(BlacklistCheckService.class);

    private final NetworkRetry networkRetry;
    private final TronClientHolder clientHolder;
    private final TokenContracts tokenContracts;
    private final NetworkSelection networkSelection;
    private final TokenSelection tokenSelection;

    public BlacklistCheckService(NetworkRetry networkRetry,
                                 TronClientHolder clientHolder,
                                 TokenContracts tokenContracts,
                                 NetworkSelection networkSelection,
                                 TokenSelection tokenSelection) {
        this.networkRetry = networkRetry;
        this.clientHolder = clientHolder;
        this.tokenContracts = tokenContracts;
        this.networkSelection = networkSelection;
        this.tokenSelection = tokenSelection;
    }

    /**
     * Заблокирован ли адрес в чёрном списке эмитента выбранного токена.
     *
     * @param address адрес получателя в формате Base58 (начинается с T)
     * @return true, если адрес в чёрном списке; false в тестовых сетях,
     *         где чёрных списков нет
     */
    public boolean isBlacklisted(String address) {
        if (!"mainnet".equals(networkSelection.current())) {
            log.debug("Чёрный список не проверяется в сети {}: "
                            + "у тестовых контрактов такой функции нет",
                    networkSelection.current());
            return false;
        }
        Function fn = new Function(
                tokenSelection.current().blacklistFunction(),
                List.of(new Address(address)),
                List.of(new TypeReference<Bool>() {})
        );
        TransactionExtention ext = networkRetry.execute(
                () -> clientHolder.client().triggerConstantContract(
                        clientHolder.senderAddress(),
                        tokenContracts.address(),
                        fn
                ),
                NetworkRetry::isTransientNetworkError,
                READ_MAX_ATTEMPTS,
                READ_BASE_DELAY_MS
        );
        List<Type> decoded = FunctionReturnDecoder.decode(
                Numeric.toHexString(ext.getConstantResult(0).toByteArray()),
                fn.getOutputParameters()
        );
        return !decoded.isEmpty() && ((Bool) decoded.get(0)).getValue();
    }
}
