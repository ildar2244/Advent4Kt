package com.example.tgbot.domain.model.ai

/**
 * Запрос к AI-модели.
 *
 * @property model Модель для обработки запроса
 * @property messages История сообщений в диалоге
 * @property temperature Температура генерации (0.0 - 2.0). Чем выше, тем более креативные ответы
 * @property maxTokens Максимальное количество токенов в ответе
 * @property huggingFaceModel Выбранная модель HuggingFace (используется только когда model == HUGGING_FACE)
 */
data class AiRequest(
    val model: AiModel,
    val messages: List<AiMessage>,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val huggingFaceModel: HuggingFaceModel? = null
)
