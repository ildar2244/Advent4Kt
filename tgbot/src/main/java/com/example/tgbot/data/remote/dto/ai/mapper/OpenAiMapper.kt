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
 * Если content null (например, при tool_calls), возвращает пустую строку или
 * сообщение о tool_calls.
 *
 * @param request Оригинальный запрос (используется для сохранения информации о модели)
 * @param responseTimeMillis Время выполнения запроса в миллисекундах
 * @param usedTools Список имен использованных MCP инструментов (опционально)
 * @return Доменная модель ответа
 * @throws IllegalStateException если ответ не содержит вариантов (choices)
 */
fun OpenAiChatResponse.toDomain(
    request: AiRequest,
    responseTimeMillis: Long,
    usedTools: List<String> = emptyList()
): AiResponse {
    val firstChoice = choices.firstOrNull()
        ?: throw IllegalStateException("OpenAI response has no choices")

    // Content может быть null если модель вызывает инструменты (tool_calls)
    var content = firstChoice.message.content
        ?: if (firstChoice.message.toolCalls != null) {
            "[AI is calling tools: ${firstChoice.message.toolCalls?.joinToString { it.function.name }}]"
        } else {
            ""
        }

    // Добавляем информацию об использованных инструментах, если они были
    if (usedTools.isNotEmpty()) {
        content += "\n\n🔧 Использованы: ${usedTools.joinToString(", ")}"
    }

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
