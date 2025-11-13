package com.example.tgbot.domain.util

/**
 * Утилита для приближённой оценки количества токенов в тексте.
 *
 * Гибридный подход:
 * - Используем точные promptTokens из API для истории
 * - Оцениваем только новое сообщение пользователя (1 токен ≈ 4 символа)
 */
object TokenCounter {

    /**
     * Оценивает количество токенов в строке текста.
     * Используется ТОЛЬКО для оценки нового сообщения пользователя.
     * Для истории используем точные значения из API.
     *
     * @param text Исходный текст
     * @return Примерное количество токенов
     */
    fun estimateTokens(text: String): Int {
        return (text.length / 4.0).toInt()
    }

    /**
     * Константы для YandexGPT Lite.
     */
    const val YANDEX_GPT_CONTEXT_SIZE = 8192
    const val THRESHOLD_PERCENT = 90
    const val TOKEN_LIMIT = (YANDEX_GPT_CONTEXT_SIZE * THRESHOLD_PERCENT / 100) // ~7372 токена

    /**
     * Проверяет, нужно ли сжимать историю диалога.
     *
     * @param lastPromptTokens Точное количество токенов из последнего ответа API (promptTokens)
     * @param newMessageTokens Оценка токенов в новом сообщении пользователя
     * @return true, если история должна быть сжата перед отправкой запроса
     */
    fun shouldCompress(lastPromptTokens: Int, newMessageTokens: Int): Boolean {
        return (lastPromptTokens + newMessageTokens) > TOKEN_LIMIT
    }

    /**
     * Вычисляет процент использования контекста.
     *
     * @param currentTokens Текущее количество токенов (обычно lastPromptTokens из сессии)
     * @return Процент использования (0-100)
     */
    fun calculateUsagePercent(currentTokens: Int): Int {
        return ((currentTokens.toDouble() / TOKEN_LIMIT) * 100).toInt()
    }
}
