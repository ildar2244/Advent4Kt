package com.example.tgbot.domain.model.ai

/**
 * Ответ от AI-модели.
 *
 * @property content Текстовое содержимое ответа
 * @property model Модель, которая сгенерировала ответ
 */
data class AiResponse(
    val content: String,
    val model: AiModel
)
