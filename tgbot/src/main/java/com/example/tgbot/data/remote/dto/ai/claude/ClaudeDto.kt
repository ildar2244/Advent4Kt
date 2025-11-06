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
    @SerialName("max_tokens") val maxTokens: Int,
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
 * DTO для ответа от Claude Messages API через ProxyAPI.
 *
 * @property model Идентификатор использованной модели
 * @property id Уникальный идентификатор запроса
 * @property type Тип ответа (обычно "message", для ошибок - "error")
 * @property role Роль ответа (всегда "assistant")
 * @property content Массив блоков контента (обычно один текстовый блок)
 * @property stopReason Причина завершения генерации ("end_turn", "max_tokens" и т.д.)
 * @property stopSequence Последовательность остановки (если использовалась)
 * @property usage Статистика использования токенов
 * @property responseTimeMs Время ответа в миллисекундах
 * @property error Информация об ошибке (если запрос неуспешный)
 */
@Serializable
data class ClaudeMessageResponse(
    @SerialName("model") val model: String? = null,
    @SerialName("id") val id: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("content") val content: List<ClaudeContentDto>? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
    @SerialName("stop_sequence") val stopSequence: String? = null,
    @SerialName("usage") val usage: ClaudeUsageDto? = null,
    @SerialName("responseTimeMs") val responseTimeMs: Long? = null,
    @SerialName("error") val error: ClaudeErrorDto? = null
)

/**
 * DTO для статистики использования токенов в Claude API.
 */
@Serializable
data class ClaudeUsageDto(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Int? = null,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int? = null,
    @SerialName("cache_creation") val cacheCreation: CacheCreationDto? = null,
    @SerialName("output_tokens") val outputTokens: Int,
    @SerialName("service_tier") val serviceTier: String? = null
)

/**
 * DTO для информации о создании кэша в Claude API.
 */
@Serializable
data class CacheCreationDto(
    @SerialName("ephemeral_5m_input_tokens") val ephemeral5mInputTokens: Int? = null,
    @SerialName("ephemeral_1h_input_tokens") val ephemeral1hInputTokens: Int? = null
)

/**
 * DTO для информации об ошибке в ответе от Claude API.
 *
 * @property type Тип ошибки
 * @property message Сообщение об ошибке
 */
@Serializable
data class ClaudeErrorDto(
    @SerialName("type") val type: String,
    @SerialName("message") val message: String
)
