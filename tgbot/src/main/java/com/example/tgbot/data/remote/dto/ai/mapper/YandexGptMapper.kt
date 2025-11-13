package com.example.tgbot.data.remote.dto.ai.mapper

import com.example.tgbot.data.remote.dto.ai.yandex.YandexGptCompletionOptionsDto
import com.example.tgbot.data.remote.dto.ai.yandex.YandexGptCompletionRequest
import com.example.tgbot.data.remote.dto.ai.yandex.YandexGptCompletionResponse
import com.example.tgbot.data.remote.dto.ai.yandex.YandexGptMessageDto
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.model.ai.TokenUsage

/**
 * Преобразует доменную модель AiRequest в DTO для YandexGPT Completion API.
 *
 * YandexGPT поддерживает все типы ролей (system, user, assistant) в едином массиве messages.
 * URI модели формируется как "gpt://{folder_id}/{model_id}".
 *
 * @param folderId ID каталога Yandex Cloud
 * @return DTO запроса для YandexGPT API
 */
fun AiRequest.toYandexGptDto(folderId: String): YandexGptCompletionRequest {
    return YandexGptCompletionRequest(
        modelUri = "gpt://$folderId/${model.modelId}",
        completionOptions = YandexGptCompletionOptionsDto(
            stream = false,
            temperature = temperature,
            maxTokens = "4000" //maxTokens?.toString()
        ),
        messages = messages.map { it.toYandexGptDto() }
    )
}

/**
 * Преобразует доменную модель AiMessage в DTO для YandexGPT.
 *
 * Конвертирует enum MessageRole в строковое представление роли для YandexGPT API.
 * Обратите внимание: поле называется "text", а не "content".
 *
 * @return DTO сообщения для YandexGPT API
 */
fun AiMessage.toYandexGptDto(): YandexGptMessageDto {
    return YandexGptMessageDto(
        role = when (role) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        },
        text = content
    )
}

/**
 * Преобразует DTO ответа от YandexGPT в доменную модель AiResponse.
 *
 * Извлекает текст из первого элемента массива alternatives.
 * YandexGPT обычно возвращает один вариант ответа.
 *
 * @param request Оригинальный запрос (используется для сохранения информации о модели)
 * @return Доменная модель ответа
 * @throws IllegalStateException если ответ не содержит альтернатив
 */
fun YandexGptCompletionResponse.toDomain(
    request: AiRequest,
    responseTimeMillis: Long,
): AiResponse {
    val text = result.alternatives.firstOrNull()?.message?.text
        ?: throw IllegalStateException("YandexGPT response has no alternatives")

    val tokenUsage = TokenUsage(
        promptTokens = result.usage.inputTextTokens.toIntDtoParam(),
        completionTokens = result.usage.completionTokens.toIntDtoParam(),
        totalTokens = result.usage.totalTokens.toIntDtoParam(),
    )

    return AiResponse(
        content = text,
        model = request.model,
        responseTimeMillis = responseTimeMillis,
        tokenUsage = tokenUsage,
    )
}

private fun String.toIntDtoParam(): Int {
    return try {
        this.toInt()
    } catch (e: Exception) {
        println("Error parsing YandexGptDto tokens: ${e.message}")
        0
    }
}
