package com.example.tgbot.data.remote.dto.ai.mapper

import com.example.tgbot.data.remote.dto.ai.openai.OpenAiChatRequest
import com.example.tgbot.data.remote.dto.ai.openai.OpenAiChatResponse
import com.example.tgbot.data.remote.dto.ai.openai.OpenAiMessageDto
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.model.ai.TokenUsage

/**
 * Преобразует доменную модель AiRequest в DTO для OpenAI Chat Completions API.
 *
 * OpenAI поддерживает все типы ролей (system, user, assistant) в едином массиве messages.
 *
 * @return DTO запроса для OpenAI API
 */
fun AiRequest.toOpenAiDto(): OpenAiChatRequest {
    return OpenAiChatRequest(
        model = model.modelId,
        messages = messages.map { it.toOpenAiDto() },
        temperature = temperature
    )
}

/**
 * Преобразует доменную модель AiMessage в DTO для OpenAI.
 *
 * Конвертирует enum MessageRole в строковое представление роли для OpenAI API.
 *
 * @return DTO сообщения для OpenAI API
 */
fun AiMessage.toOpenAiDto(): OpenAiMessageDto {
    return OpenAiMessageDto(
        role = when (role) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        },
        content = content
    )
}

/**
 * Преобразует DTO ответа от OpenAI в доменную модель AiResponse.
 *
 * Извлекает текст из первого элемента массива choices.
 * OpenAI обычно возвращает один вариант ответа.
 *
 * @param request Оригинальный запрос (используется для сохранения информации о модели)
 * @return Доменная модель ответа
 * @throws IllegalStateException если ответ не содержит вариантов (choices)
 */
fun OpenAiChatResponse.toDomain(
    request: AiRequest,
    responseTimeMillis: Long,
): AiResponse {
    val content = choices.firstOrNull()?.message?.content
        ?: throw IllegalStateException("OpenAI response has no choices")

    val tokenUsage = TokenUsage(
        promptTokens = usage?.promptTokens ?: 0,
        completionTokens = usage?.completionTokens ?: 0,
        totalTokens = usage?.totalTokens ?: 0,
    )

    return AiResponse(
        content = content,
        model = request.model,
        responseTimeMillis = responseTimeMillis,
        tokenUsage = tokenUsage,
    )
}
