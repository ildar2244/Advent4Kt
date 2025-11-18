package com.example.tgbot.data.remote.dto.ai.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * DTO для сообщения в OpenAI Chat Completions API.
 *
 * @property role Роль отправителя: "system" (системное сообщение), "user" (пользователь), "assistant" (ассистент), "tool" (результат вызова инструмента)
 * @property content Текстовое содержимое сообщения (null для tool calls)
 * @property toolCalls Вызовы инструментов (только для ассистента)
 * @property toolCallId Идентификатор вызова инструмента (только для роли "tool")
 * @property name Имя инструмента (только для роли "tool")
 */
@Serializable
data class OpenAiMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("name") val name: String? = null
)

/**
 * DTO для запроса к OpenAI Chat Completions API.
 *
 * @property model Идентификатор модели (например, "gpt-4o-mini")
 * @property messages Массив сообщений в диалоге
 * @property temperature Температура генерации (0.0 - 2.0, опционально). Управляет случайностью ответов
 * @property tools Массив доступных инструментов для вызова (опционально)
 * @property toolChoice Режим выбора инструментов: "none", "auto", "required" или {"type": "function", "function": {"name": "имя"}} (опционально)
 */
@Serializable
data class OpenAiChatRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<OpenAiMessageDto>,
    @SerialName("temperature") val temperature: Double? = null,
    @SerialName("tools") val tools: List<OpenAiTool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null
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
 * @property content Текстовое содержимое сообщения (null если есть tool_calls)
 * @property refusal Причина отказа (если модель отказалась отвечать)
 * @property annotations Аннотации к сообщению
 * @property toolCalls Вызовы инструментов (если модель решила вызвать инструмент)
 */
@Serializable
data class OpenAiResponseMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String? = null,
    @SerialName("refusal") val refusal: String? = null,
    @SerialName("annotations") val annotations: List<String>? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
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

// ================================
// Function Calling API Support
// ================================

/**
 * DTO для описания инструмента в OpenAI Function Calling API.
 *
 * @property type Тип инструмента (всегда "function")
 * @property function Определение функции
 */
@Serializable
data class OpenAiTool(
    @SerialName("type") val type: String = "function",
    @SerialName("function") val function: FunctionDefinition
)

/**
 * DTO для определения функции в OpenAI Function Calling API.
 *
 * @property name Имя функции (должно соответствовать формату [a-zA-Z0-9_-]{1,64})
 * @property description Описание функции (что она делает)
 * @property parameters JSON Schema описание параметров функции
 */
@Serializable
data class FunctionDefinition(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("parameters") val parameters: JsonObject
)

/**
 * DTO для вызова инструмента в ответе от OpenAI.
 *
 * @property id Уникальный идентификатор вызова инструмента
 * @property type Тип инструмента (всегда "function")
 * @property function Детали вызова функции
 */
@Serializable
data class ToolCall(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("function") val function: FunctionCall
)

/**
 * DTO для вызова функции в OpenAI Function Calling API.
 *
 * @property name Имя вызываемой функции
 * @property arguments JSON-строка с аргументами функции
 */
@Serializable
data class FunctionCall(
    @SerialName("name") val name: String,
    @SerialName("arguments") val arguments: String
)
