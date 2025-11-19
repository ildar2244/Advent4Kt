package com.example.mcptasks.data.remote.dto.ai.yandex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для сообщения в YandexGPT API.
 *
 * Примечание: YandexGPT использует поле "text" вместо "content".
 *
 * @property role Роль отправителя: "system" (системное сообщение), "user" (пользователь), "assistant" (ассистент)
 * @property text Текстовое содержимое сообщения
 */
@Serializable
data class YandexGptMessageDto(
    @SerialName("role") val role: String,
    @SerialName("text") val text: String
)

/**
 * DTO для опций генерации в YandexGPT API.
 *
 * @property stream Использовать ли потоковую передачу (обычно false для синхронных запросов)
 * @property temperature Температура генерации (0.0 - 1.0, опционально). Управляет случайностью ответов
 * @property maxTokens Максимальное количество токенов в ответе (передается как строка!)
 */
@Serializable
data class YandexGptCompletionOptionsDto(
    @SerialName("stream") val stream: Boolean = false,
    @SerialName("temperature") val temperature: Double? = null,
    @SerialName("maxTokens") val maxTokens: String? = null
)

/**
 * DTO для запроса к YandexGPT Completion API.
 *
 * @property modelUri URI модели в формате "gpt://{folder_id}/{model_id}" (например, "gpt://b1g2abc3def4/yandexgpt-lite")
 * @property completionOptions Опции для генерации текста
 * @property messages Массив сообщений в диалоге
 */
@Serializable
data class YandexGptCompletionRequest(
    @SerialName("modelUri") val modelUri: String,
    @SerialName("completionOptions") val completionOptions: YandexGptCompletionOptionsDto,
    @SerialName("messages") val messages: List<YandexGptMessageDto>
)

/**
 * DTO для сообщения в ответе от YandexGPT.
 *
 * @property role Роль отправителя (обычно "assistant")
 * @property text Текстовое содержимое ответа
 */
@Serializable
data class YandexGptResponseMessageDto(
    @SerialName("role") val role: String,
    @SerialName("text") val text: String
)

/**
 * DTO для альтернативного варианта ответа в YandexGPT API.
 * API может возвращать несколько вариантов ответа, каждый представлен этим классом.
 *
 * @property message Сгенерированное сообщение от ассистента
 * @property status Статус альтернативы (например, "ALTERNATIVE_STATUS_FINAL")
 */
@Serializable
data class YandexGptAlternativeDto(
    @SerialName("message") val message: YandexGptResponseMessageDto,
    @SerialName("status") val status: String
)

/**
 * DTO для статистики использования токенов в YandexGPT API.
 *
 * Примечание: все поля передаются как строки, а не числа.
 *
 * @property inputTextTokens Количество токенов во входном тексте (строка)
 * @property completionTokens Количество токенов в сгенерированном ответе (строка)
 * @property totalTokens Общее количество токенов (строка)
 */
@Serializable
data class YandexGptUsageDto(
    @SerialName("inputTextTokens") val inputTextTokens: String,
    @SerialName("completionTokens") val completionTokens: String,
    @SerialName("totalTokens") val totalTokens: String
)

/**
 * DTO для результата в ответе от YandexGPT API.
 *
 * @property alternatives Массив альтернативных вариантов ответа (обычно содержит один элемент)
 * @property usage Статистика использования токенов
 * @property modelVersion Версия использованной модели
 */
@Serializable
data class YandexGptResultDto(
    @SerialName("alternatives") val alternatives: List<YandexGptAlternativeDto>,
    @SerialName("usage") val usage: YandexGptUsageDto,
    @SerialName("modelVersion") val modelVersion: String
)

/**
 * DTO для ответа от YandexGPT Completion API.
 *
 * @property result Объект результата с альтернативами, статистикой и версией модели
 */
@Serializable
data class YandexGptCompletionResponse(
    @SerialName("result") val result: YandexGptResultDto
)
