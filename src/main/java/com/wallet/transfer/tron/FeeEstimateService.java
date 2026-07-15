package com.wallet.transfer.tron;

import org.springframework.stereotype.Service;
import org.tron.trident.abi.TypeReference;
import org.tron.trident.abi.datatypes.Address;
import org.tron.trident.abi.datatypes.Bool;
import org.tron.trident.abi.datatypes.Function;
import org.tron.trident.abi.datatypes.generated.Uint256;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.proto.Response.TransactionExtention;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;

/**
 * Оценка комиссии USDT-перевода по данным сети.
 * <p>
 * energy берётся "сухим" вызовом transfer (triggerConstantContract),
 * цена energy — из getEnergyPrices. Буфер здесь НЕ применяется:
 * это честная сетевая оценка. Буферы (feeLimit, порог баланса)
 * накладывает вызывающий код.
 */
@Service
public class FeeEstimateService {

    private static final BigDecimal SUN_PER_TRX = BigDecimal.valueOf(1_000_000);
    /** Повтор чтения из сети: максимум попыток. */
    private static final int READ_MAX_ATTEMPTS = 5;
    /** Повтор чтения: базовая пауза (мс), далее нарастает ×2. */
    private static final long READ_BASE_DELAY_MS = 1_000L;

    private final NetworkRetry networkRetry;
    private final TronClientHolder clientHolder;
    private final TokenContracts tokenContracts;

    public FeeEstimateService(NetworkRetry networkRetry, TronClientHolder clientHolder, TokenContracts tokenContracts) {
        this.networkRetry = networkRetry;
        this.clientHolder = clientHolder;
        this.tokenContracts = tokenContracts;
    }

    /**
     * Оценить комиссию перевода USDT на адрес получателя.
     *
     * @param recipient адрес получателя (Base58, начинается с T)
     * @param amount    сумма в минимальных единицах USDT (с учётом decimals)
     * @return оценка комиссии (energy, цена, итог в TRX)
     */
    public FeeEstimate estimate(String recipient, BigInteger amount) {
        long energyUsed = estimateEnergy(recipient, amount);
        long sunPerEnergy = currentEnergyPriceSun();

        long feeSun = energyUsed * sunPerEnergy;
        BigDecimal feeTrx = BigDecimal.valueOf(feeSun)
                .divide(SUN_PER_TRX, MathContext.DECIMAL64);

        return new FeeEstimate(energyUsed, sunPerEnergy, feeTrx);
    }

    /** Оценка energy через "сухой" transfer (без отправки, без подписи). */
    private long estimateEnergy(String recipient, BigInteger amount) {
        Function transfer = new Function(
                "transfer",
                List.of(new Address(recipient), new Uint256(amount)),
                List.of(new TypeReference<Bool>() {})
        );
        TransactionExtention ext = networkRetry.execute(
                () -> clientHolder.client().triggerConstantContract(
                        clientHolder.senderAddress(),
                        tokenContracts.address(),
                        transfer
                ),
                NetworkRetry::isTransientNetworkError,
                READ_MAX_ATTEMPTS,
                READ_BASE_DELAY_MS
        );
        return ext.getEnergyUsed();
    }

    /** Текущая цена energy в sun: последняя пара в строке "ts:price,...". */
    private long currentEnergyPriceSun() {
        String prices = networkRetry.execute(
                () -> clientHolder.client().getEnergyPrices().getPrices(),
                NetworkRetry::isTransientNetworkError,
                READ_MAX_ATTEMPTS,
                READ_BASE_DELAY_MS
        );
        String[] parts = prices.split(",");
        String last = parts[parts.length - 1];
        return Long.parseLong(last.split(":")[1]);
    }
}
