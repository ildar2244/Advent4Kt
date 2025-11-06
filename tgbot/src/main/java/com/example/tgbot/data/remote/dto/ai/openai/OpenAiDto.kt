package com.example.tgbot.data.remote.dto.ai.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для сообщения в OpenAI Chat Completions API.
 *
 * @property role Роль отправителя: "system" (системное сообщение), "user" (пользователь), "assistant" (ассистент)
 * @property content Текстовое содержимое сообщения
 */
@Serializable
data class OpenAiMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)

/**
 * DTO для запроса к OpenAI Chat Completions API.
 *
 * @property model Идентификатор модели (например, "gpt-4o-mini")
 * @property messages Массив сообщений в диалоге
 * @property temperature Температура генерации (0.0 - 2.0, опционально). Управляет случайностью ответов
 */
@Serializable
data class OpenAiChatRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<OpenAiMessageDto>,
    @SerialName("temperature") val temperature: Double? = null
)

/**
 * DTO для выбора (choice) в ответе от OpenAI.
 * API может возвращать несколько вариантов ответа, каждый представлен этим классом.
 *
 * @property message Сгенерированное сообщение от ассистента
 * @property finishReason Причина завершения генерации ("stop", "length", "content_filter" и т.д.)
 */
@Serializable
data class OpenAiChoiceDto(
    @SerialName("message") val message: OpenAiMessageDto,
    @SerialName("finish_reason") val finishReason: String? = null
)

/**
 * DTO для ответа от OpenAI Chat Completions API.
 *
 * @property id Уникальный идентификатор запроса
 * @property choices Массив вариантов ответа (обычно содержит один элемент)
 * @property model Идентификатор использованной модели
 */
@Serializable
data class OpenAiChatResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("choices") val choices: List<OpenAiChoiceDto>,
    @SerialName("model") val model: String? = null
)
