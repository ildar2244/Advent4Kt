package com.example.tgbot.data.remote.dto.ai.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для сообщения в Claude Messages API.
 *
 * Примечание: Claude не поддерживает роль "system" в массиве messages,
 * системные инструкции передаются отдельным параметром "system" в запросе.
 *
 * @property role Роль отправителя: "user" (пользователь) или "assistant" (ассистент)
 * @property content Текстовое содержимое сообщения
 */
@Serializable
data class ClaudeMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)

/**
 * DTO для запроса к Claude Messages API.
 *
 * @property model Идентификатор модели (например, "claude-3-5-haiku-20241022")
 * @property messages Массив сообщений в диалоге (без системных сообщений)
 * @property maxTokens Максимальное количество токенов в ответе (обязательный параметр для Claude)
 * @property system Системная инструкция (опционально). Передается отдельно от messages
 * @property temperature Температура генерации (0.0 - 1.0, опционально). Управляет случайностью ответов
 */
@Serializable
data class ClaudeMessageRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<ClaudeMessageDto>,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    @SerialName("system") val system: String? = null,
    @SerialName("temperature") val temperature: Double? = null
)

/**
 * DTO для контента в ответе от Claude.
 * Claude может возвращать контент разных типов, но для текстовых ответов используется тип "text".
 *
 * @property type Тип контента (обычно "text")
 * @property text Текстовое содержимое ответа
 */
@Serializable
data class ClaudeContentDto(
    @SerialName("type") val type: String,
    @SerialName("text") val text: String
)

/**
 * DTO для ответа от Claude Messages API.
 *
 * @property id Уникальный идентификатор запроса
 * @property content Массив блоков контента (обычно один текстовый блок)
 * @property model Идентификатор использованной модели
 * @property role Роль ответа (всегда "assistant")
 * @property stopReason Причина завершения генерации ("end_turn", "max_tokens" и т.д.)
 */
@Serializable
data class ClaudeMessageResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("content") val content: List<ClaudeContentDto>,
    @SerialName("model") val model: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null
)
