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

import java.util.List;

/**
 * Проверка адреса в чёрном списке USDT (Tether).
 * <p>
 * USDT-контракт хранит флаг блокировки и отдаёт его функцией
 * isBlackListed(address). Заблокированному адресу перевод USDT
 * не пройдёт (контракт откатит транзакцию), поэтому получателей
 * полезно проверять до отправки.
 * <p>
 * Операция чтения (view): без транзакции, подписи и затрат ресурсов.
 */
@Service
public class BlacklistCheckService {

    /** Повтор чтения из сети: максимум попыток. */
    private static final int READ_MAX_ATTEMPTS = 5;
    /** Повтор чтения: базовая пауза (мс), далее нарастает ×2. */
    private static final long READ_BASE_DELAY_MS = 1_000L;

    private final NetworkRetry networkRetry;
    private final TronClientHolder clientHolder;
    private final TronProperties properties;

    public BlacklistCheckService(NetworkRetry networkRetry,
                                 TronClientHolder clientHolder,
                                 TronProperties properties) {
        this.networkRetry = networkRetry;
        this.clientHolder = clientHolder;
        this.properties = properties;
    }

    /**
     * Заблокирован ли адрес в чёрном списке USDT.
     *
     * @param address адрес получателя в формате Base58 (начинается с T)
     * @return true, если адрес в чёрном списке Tether
     */
    public boolean isBlacklisted(String address) {
        Function fn = new Function(
                "isBlackListed",
                List.of(new Address(address)),
                List.of(new TypeReference<Bool>() {})
        );
        TransactionExtention ext = networkRetry.execute(
                () -> clientHolder.client().triggerConstantContract(
                        clientHolder.senderAddress(),
                        properties.usdtContractAddress(),
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
