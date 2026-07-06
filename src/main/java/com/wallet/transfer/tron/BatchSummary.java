package com.wallet.transfer.tron;

import java.util.List;

/**
 * Сводка обработки пачки переводов.
 *
 * @param total     всего переводов в пачке
 * @param confirmed сколько подтверждено
 * @param failed    сколько провалилось (FAILED в сети)
 * @param skipped   сколько пропущено (проблема в переводе)
 * @param stopped   была ли пачка остановлена досрочно
 * @param results   подробные итоги по каждому переводу (для отчёта)
 */
public record BatchSummary(
        int total,
        int confirmed,
        int failed,
        int skipped,
        int feeExceeded,
        boolean stopped,
        List<TransferResult> results
) {

}
