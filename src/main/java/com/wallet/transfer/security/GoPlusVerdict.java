package com.wallet.transfer.security;

import java.util.List;

/**
 * Вердикт проверки адреса через GoPlus Malicious Address API.
 * <p>
 * Репутационная оценка (второй слой поверх чёрного списка USDT):
 * перевод на рискованный адрес технически пройдёт, но получатель
 * замечен в криминальной активности — пользователь предупреждается
 * и решает сам.
 *
 * @param risky      сработал ли хотя бы один флаг ядра рисков
 * @param flags      сработавшие флаги ядра (имена полей GoPlus), пусто если чисто
 * @param dataSource источник данных GoPlus (напр. "GoPlus"/"SlowMist"), null если нет данных
 */
public record GoPlusVerdict(boolean risky, List<String> flags, String dataSource) {

    /** Чистый вердикт: рисков нет (или сервис недоступен — деградация). */
    public static GoPlusVerdict clean() {
        return new GoPlusVerdict(false, List.of(), null);
    }
}
