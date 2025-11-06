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
 * @property index Индекс варианта ответа
 * @property message Сгенерированное сообщение от ассистента
 * @property logprobs Логарифмические вероятности (если запрошены)
 * @property finishReason Причина завершения генерации ("stop", "length", "content_filter" и т.д.)
 */
@Serializable
data class OpenAiChoiceDto(
    @SerialName("index") val index: Int,
    @SerialName("message") val message: OpenAiResponseMessageDto,
    @SerialName("logprobs") val logprobs: String? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

/**
 * DTO для сообщения в ответе от OpenAI (отличается от запроса наличием дополнительных полей).
 *
 * @property role Роль отправителя
 * @property content Текстовое содержимое сообщения
 * @property refusal Причина отказа (если модель отказалась отвечать)
 * @property annotations Аннотации к сообщению
 */
@Serializable
data class OpenAiResponseMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
    @SerialName("refusal") val refusal: String? = null,
    @SerialName("annotations") val annotations: List<String>? = null
)

/**
 * DTO для ответа от OpenAI Chat Completions API через ProxyAPI.
 *
 * @property id Уникальный идентификатор запроса
 * @property objectType Тип объекта (обычно "chat.completion")
 * @property created Timestamp создания
 * @property model Идентификатор использованной модели
 * @property choices Массив вариантов ответа (обычно содержит один элемент)
 * @property usage Статистика использования токенов
 * @property serviceTier Уровень сервиса
 * @property systemFingerprint Отпечаток системы
 * @property responseTimeMs Время ответа в миллисекундах
 */
@Serializable
data class OpenAiChatResponse(
    @SerialName("id") val id: String,
    @SerialName("object") val objectType: String,
    @SerialName("created") val created: Long,
    @SerialName("model") val model: String,
    @SerialName("choices") val choices: List<OpenAiChoiceDto>,
    @SerialName("usage") val usage: OpenAiUsageDto? = null,
    @SerialName("service_tier") val serviceTier: String? = null,
    @SerialName("system_fingerprint") val systemFingerprint: String? = null,
    @SerialName("responseTimeMs") val responseTimeMs: Long? = null
)

/**
 * DTO для статистики использования токенов в OpenAI API.
 */
@Serializable
data class OpenAiUsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetailsDto? = null,
    @SerialName("completion_tokens_details") val completionTokensDetails: CompletionTokensDetailsDto? = null
)

/**
 * DTO для детальной информации о токенах промпта.
 */
@Serializable
data class PromptTokensDetailsDto(
    @SerialName("cached_tokens") val cachedTokens: Int? = null,
    @SerialName("audio_tokens") val audioTokens: Int? = null
)

/**
 * DTO для детальной информации о токенах ответа.
 */
@Serializable
data class CompletionTokensDetailsDto(
    @SerialName("reasoning_tokens") val reasoningTokens: Int? = null,
    @SerialName("audio_tokens") val audioTokens: Int? = null,
    @SerialName("accepted_prediction_tokens") val acceptedPredictionTokens: Int? = null,
    @SerialName("rejected_prediction_tokens") val rejectedPredictionTokens: Int? = null
)
