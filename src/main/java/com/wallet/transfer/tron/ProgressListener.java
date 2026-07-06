package com.wallet.transfer.tron;

/**
 * Колбэк прогресса обработки пачки. Ядро вызывает его по ходу работы;
 * потребитель (GUI) сам решает, как отобразить. Ядро не знает про UI.
 *
 * @see BatchTransferService
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * Сообщение о прогрессе внутри фазы.
     *
     * @param phase текущая фаза (отправка/подтверждение)
     * @param done  обработано элементов в этой фазе (1..total)
     * @param total всего элементов в фазе (размер пачки)
     */
    void onProgress(ProgressPhase phase, int done, int total);

    /** Заглушка «ничего не делать» — для CLI и тестов, где прогресс не нужен. */
    ProgressListener NO_OP = (phase, done, total) -> {
    };
}
