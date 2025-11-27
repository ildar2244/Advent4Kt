package com.example.tgbot.domain.model

/**
 * Состояние интерактивного RAG-поиска для пользователя.
 *
 * Хранит результаты поиска и текущую попытку для сценария RAG_INTERACTIVE.
 * Позволяет пользователю последовательно пробовать разные группы чанков (по 3 шт.)
 * для получения более релевантного ответа от AI.
 *
 * @property query Исходный запрос пользователя
 * @property allResults Все найденные чанки из RAG-поиска (до 9 штук)
 * @property currentAttempt Текущая попытка (0 = первая, 1 = вторая, 2 = третья)
 * @property maxAttempts Максимальное количество попыток (1-3 в зависимости от количества результатов)
 */
data class RagInteractiveState(
    val query: String,
    val allResults: List<RagSearchResult>,
    val currentAttempt: Int = 0,
    val maxAttempts: Int
) {
    /**
     * Получить чанки для текущей попытки (по 3 чанка).
     *
     * @return Список из 1-3 чанков для текущей попытки
     */
    fun getCurrentChunks(): List<RagSearchResult> {
        val startIdx = currentAttempt * 3
        val endIdx = minOf(startIdx + 3, allResults.size)
        return allResults.subList(startIdx, endIdx)
    }

    /**
     * Проверить, можно ли перейти к следующей попытке.
     *
     * @return true если есть еще неиспользованные чанки, false иначе
     */
    fun hasNextAttempt(): Boolean {
        return currentAttempt + 1 < maxAttempts
    }

    /**
     * Получить диапазон similarity scores для текущих чанков.
     *
     * @return Пара (максимальный score, минимальный score) для текущей группы чанков
     */
    fun getCurrentSimilarityRange(): Pair<Float, Float> {
        val chunks = getCurrentChunks()
        if (chunks.isEmpty()) return 0f to 0f
        return chunks.maxOf { it.similarity } to chunks.minOf { it.similarity }
    }
}
