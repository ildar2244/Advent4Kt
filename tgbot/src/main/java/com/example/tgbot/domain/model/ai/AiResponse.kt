package com.example.tgbot.domain.model.ai

/**
 * Ответ от AI-модели.
 *
 * @property content Текстовое содержимое ответа
 * @property model Модель, которая сгенерировала ответ
 * @property responseTimeMillis Время выполнения запроса в миллисекундах (опционально)
 * @property tokenUsage Статистика использования токенов (опционально)
 */
data class AiResponse(
    val content: String,
    val model: AiModel,
    val responseTimeMillis: Long? = null,
    val tokenUsage: TokenUsage? = null
)

/**
 * Статистика использования токенов в запросе и ответе.
 *
 * @property promptTokens Количество токенов в запросе (промпт)
 * @property completionTokens Количество токенов в ответе (генерация)
 * @property totalTokens Общее количество токенов
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
