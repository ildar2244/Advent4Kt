package com.example.tgbot.data.remote.dto.ai.mapper

import com.example.tgbot.data.remote.dto.ai.claude.ClaudeMessageDto
import com.example.tgbot.data.remote.dto.ai.claude.ClaudeMessageRequest
import com.example.tgbot.data.remote.dto.ai.claude.ClaudeMessageResponse
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import com.example.tgbot.domain.model.ai.MessageRole

/**
 * Преобразует доменную модель AiRequest в DTO для Claude Messages API.
 *
 * Особенность Claude: системные сообщения передаются через отдельный параметр "system",
 * а не в массиве messages. Эта функция автоматически разделяет системные сообщения
 * и обычные сообщения пользователя/ассистента.
 *
 * @return DTO запроса для Claude API
 */
fun AiRequest.toClaudeDto(): ClaudeMessageRequest {
    // Отделяем системные сообщения от user/assistant
    val systemMessage = messages.firstOrNull { it.role == MessageRole.SYSTEM }?.content
    val conversationMessages = messages.filter { it.role != MessageRole.SYSTEM }

    return ClaudeMessageRequest(
        model = model.modelId,
        messages = conversationMessages.map { it.toClaudeDto() },
        maxTokens = maxTokens ?: 1024,
        system = systemMessage,
        temperature = temperature
    )
}

/**
 * Преобразует доменную модель AiMessage в DTO для Claude.
 *
 * Конвертирует enum MessageRole в строковое представление роли для Claude API.
 * Системные сообщения не должны попадать в эту функцию, так как для Claude
 * они передаются отдельным параметром.
 *
 * @return DTO сообщения для Claude API
 * @throws IllegalArgumentException если передано системное сообщение
 */
fun AiMessage.toClaudeDto(): ClaudeMessageDto {
    return ClaudeMessageDto(
        role = when (role) {
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.SYSTEM -> throw IllegalArgumentException("System messages should be handled separately in Claude API")
        },
        content = content
    )
}

/**
 * Преобразует DTO ответа от Claude в доменную модель AiResponse.
 *
 * Извлекает текст из первого блока контента с типом "text".
 * Claude может возвращать контент разных типов, но для текстовых ответов
 * используется тип "text".
 *
 * @param request Оригинальный запрос (используется для сохранения информации о модели)
 * @return Доменная модель ответа
 * @throws IllegalStateException если ответ не содержит текстового контента
 */
fun ClaudeMessageResponse.toDomain(request: AiRequest): AiResponse {
    val text = content.firstOrNull { it.type == "text" }?.text
        ?: throw IllegalStateException("Claude response has no text content")

    return AiResponse(
        content = text,
        model = request.model
    )
}
