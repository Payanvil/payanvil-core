package com.wallet.transfer.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Порт журнала и статусов переводов.
 * <p>
 * Открытое ядро объявляет контракт, ничего не зная о способе хранения;
 * реализацию (JPA-адаптер) предоставляет закрытая часть приложения.
 * Каждый метод — этапное событие жизненного цикла перевода: ядро сообщает,
 * что произошло, адаптер решает, как это сохранить.
 * <p>
 * Связь этапов одной попытки — через идентификатор записи журнала:
 * {@link #logPrepared} создаёт запись и возвращает её id,
 * {@link #logSent} дополняет ту же запись фактом отправки.
 */
public interface TransferLogPort {

    /** Перевод уже отправлялся ранее (защита от повторной отправки). */
    boolean isAlreadySent(Long transferId);

    /**
     * Балансы отправителя проверены.
     *
     * @return id созданной записи журнала
     */
    long logBalanceChecked(Long transferId, BigDecimal amount,
                           BigDecimal balanceTrx, BigDecimal balanceToken);

    /**
     * Транзакция построена и подписана; оценка комиссии зафиксирована.
     *
     * @return id созданной записи журнала
     */
    long logPrepared(Long transferId, BigDecimal amount,
                     BigDecimal feeTrx, Long energyUsed);

    /**
     * Транзакция отправлена в сеть; дополняет запись, созданную
     * {@link #logPrepared}.
     *
     * @param logId id записи журнала из {@link #logPrepared}
     */
    void logSent(long logId, String txid);

    /** Итог подтверждения: CONFIRMED или FAILED, фактическая комиссия. */
    void logConfirmOutcome(Long transferId, BigDecimal amount,
                           TransferStatus finalStatus, String txid,
                           BigDecimal actualFeeTrx, Instant confirmedAt);

    /** Обновить статус перевода. */
    void updateStatus(Long transferId, TransferStatus status);
}
