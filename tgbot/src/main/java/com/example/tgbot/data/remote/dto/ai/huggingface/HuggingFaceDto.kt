package com.example.tgbot.data.remote.dto.ai.huggingface

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для сообщения в HuggingFace Router Chat Completions API.
 * Формат полностью совместим с OpenAI API.
 *
 * @property role Роль отправителя: "system", "user", "assistant"
 * @property content Текстовое содержимое сообщения
 */
@Serializable
data class HuggingFaceMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)

/**
 * DTO для запроса к HuggingFace Router Chat Completions API.
 * OpenAI-совместимый формат.
 *
 * @property model Идентификатор модели (например, "meta-llama/Llama-3.2-1B-Instruct")
 * @property messages Массив сообщений в диалоге
 * @property temperature Температура генерации (0.0 - 2.0, опционально)
 * @property maxTokens Максимальное количество токенов в ответе (опционально)
 */
@Serializable
data class HuggingFaceChatRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<HuggingFaceMessageDto>,
    @SerialName("temperature") val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

/**
 * DTO для сообщения в ответе от HuggingFace Router API.
 *
 * @property role Роль отправителя
 * @property content Текстовое содержимое сообщения
 */
@Serializable
data class HuggingFaceResponseMessage(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)

/**
 * DTO для выбора (choice) в ответе от HuggingFace Router API.
 *
 * @property index Индекс варианта ответа
 * @property message Сгенерированное сообщение от ассистента
 * @property finishReason Причина завершения генерации ("stop", "length", и т.д.)
 */
@Serializable
data class HuggingFaceChoice(
    @SerialName("index") val index: Int,
    @SerialName("message") val message: HuggingFaceResponseMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

/**
 * DTO для статистики использования токенов в HuggingFace Router API.
 *
 * @property promptTokens Количество токенов в запросе
 * @property completionTokens Количество токенов в ответе
 * @property totalTokens Общее количество токенов
 */
@Serializable
data class HuggingFaceUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

/**
 * DTO для ответа от HuggingFace Router Chat Completions API.
 * OpenAI-совместимый формат.
 *
 * @property id Уникальный идентификатор запроса
 * @property objectType Тип объекта (обычно "chat.completion")
 * @property created Timestamp создания
 * @property model Идентификатор использованной модели
 * @property choices Массив вариантов ответа
 * @property usage Статистика использования токенов
 */
@Serializable
data class HuggingFaceChatResponse(
    @SerialName("id") val id: String,
    @SerialName("object") val objectType: String,
    @SerialName("created") val created: Long,
    @SerialName("model") val model: String,
    @SerialName("choices") val choices: List<HuggingFaceChoice>,
    @SerialName("usage") val usage: HuggingFaceUsage
)

/**
 * DTO для ошибки от HuggingFace API.
 * Используется для обработки 503 Service Unavailable (модель загружается).
 *
 * @property error Текст ошибки
 * @property estimatedTime Примерное время загрузки модели в секундах (опционально)
 */
@Serializable
data class HuggingFaceErrorDto(
    @SerialName("error") val error: String,
    @SerialName("estimated_time") val estimatedTime: Double? = null
)
