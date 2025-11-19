package com.example.mcptasks.domain.model.ai

/**
 * Запрос к AI-модели.
 *
 * @property model Модель для обработки запроса
 * @property messages История сообщений в диалоге
 * @property temperature Температура генерации (0.0 - 2.0). Чем выше, тем более креативные ответы
 * @property maxTokens Максимальное количество токенов в ответе
 */
data class AiRequest(
    val model: AiModel,
    val messages: List<AiMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null
)
