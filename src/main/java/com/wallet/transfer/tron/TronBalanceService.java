package com.wallet.transfer.tron;

import org.springframework.stereotype.Service;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.contract.Contract;
import org.tron.trident.core.contract.Trc20Contract;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * Сервис чтения балансов из сети Tron.
 * Использует бин ApiWrapper для обращения к ноде.
 */
@Service
public class TronBalanceService {

    /** Количество SUN в одном TRX: 1 TRX = 1 000 000 SUN. */
    private static final BigDecimal SUN_PER_TRX = BigDecimal.valueOf(1_000_000);
    /** Повтор чтения из сети: максимум попыток. */
    private static final int READ_MAX_ATTEMPTS = 5;
    /** Повтор чтения: базовая пауза (мс), далее нарастает ×2. */
    private static final long READ_BASE_DELAY_MS = 1_000L;


    private final TronClientHolder clientHolder;
    private final TokenContracts tokenContracts;
    private final NetworkRetry networkRetry;

    public TronBalanceService(TronClientHolder clientHolder, TokenContracts tokenContracts,
                              NetworkRetry networkRetry) {
        this.clientHolder = clientHolder;
        this.tokenContracts = tokenContracts;
        this.networkRetry = networkRetry;
    }

    /**
     * Прочитать баланс TRX по адресу.
     * <p>
     * Обращается к сети Tron (gRPC). Может бросить RuntimeException
     * при сетевых проблемах или недоступности ноды — обработка
     * остаётся на стороне вызывающего кода.
     *
     * @param address адрес кошелька в формате Base58 (начинается с T)
     * @return баланс в TRX (не в SUN), с точностью BigDecimal
     */
    public BigDecimal getTrxBalance(String address) {
        long balanceSun = networkRetry.execute(
                () -> clientHolder.client().getAccountBalance(address),
                NetworkRetry::isTransientNetworkError,
                READ_MAX_ATTEMPTS,
                READ_BASE_DELAY_MS
        );
        return BigDecimal.valueOf(balanceSun)
                .divide(SUN_PER_TRX, MathContext.DECIMAL64);
    }

    /**
     * Прочитать баланс USDT (TRC20) по адресу.
     * <p>
     * USDT — это смарт-контракт, поэтому баланс читается вызовом
     * метода balanceOf у контракта. Результат приходит в минимальных
     * единицах токена и переводится в человеческий USDT по числу
     * decimals из настроек (для Tether = 6).
     * <p>
     * Это операция чтения (view): она не создаёт транзакцию,
     * не тратит ресурсы сети и не требует подписи.
     *
     * @param address адрес кошелька в формате Base58 (начинается с T)
     * @return баланс в USDT, с точностью BigDecimal
     */
    public BigDecimal getUsdtBalance(String address) {
        BigInteger rawBalance = networkRetry.execute(
                () -> {
                    Contract contract =
                            clientHolder.client().getContract(tokenContracts.address());
                    Trc20Contract usdt = new Trc20Contract(contract, address, clientHolder.client());
                    return usdt.balanceOf(address);
                },
                NetworkRetry::isTransientNetworkError,
                READ_MAX_ATTEMPTS,
                READ_BASE_DELAY_MS
        );

        BigDecimal divisor = BigDecimal.TEN.pow(tokenContracts.decimals());
        return new BigDecimal(rawBalance).divide(divisor, MathContext.DECIMAL64);
    }
}