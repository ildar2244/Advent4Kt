package com.example.tgbot.data.remote.dto.ai.mapper

import com.example.tgbot.data.remote.dto.ai.huggingface.HuggingFaceChatRequest
import com.example.tgbot.data.remote.dto.ai.huggingface.HuggingFaceChatResponse
import com.example.tgbot.data.remote.dto.ai.huggingface.HuggingFaceMessageDto
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import com.example.tgbot.domain.model.ai.HuggingFaceModel
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.model.ai.TokenUsage

/**
 * Преобразует доменную модель AiRequest в DTO для HuggingFace Router Chat Completions API.
 * OpenAI-совместимый формат.
 *
 * @param selectedModel Выбранная модель HuggingFace (для получения modelId)
 * @return DTO запроса для HuggingFace Router API
 */
fun AiRequest.toHuggingFaceDto(selectedModel: HuggingFaceModel): HuggingFaceChatRequest {
    return HuggingFaceChatRequest(
        model = selectedModel.modelId,
        messages = messages.map { it.toHuggingFaceDto() },
        temperature = temperature,
        maxTokens = maxTokens
    )
}

/**
 * Преобразует доменную модель AiMessage в DTO для HuggingFace Router API.
 *
 * @return DTO сообщения для HuggingFace API
 */
fun AiMessage.toHuggingFaceDto(): HuggingFaceMessageDto {
    return HuggingFaceMessageDto(
        role = when (role) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        },
        content = content
    )
}

/**
 * Преобразует DTO ответа от HuggingFace Router API в доменную модель AiResponse.
 *
 * Извлекает текст из первого элемента массива choices и добавляет
 * статистику времени выполнения и использования токенов.
 *
 * @param request Оригинальный запрос (используется для сохранения информации о модели)
 * @param responseTimeMillis Время выполнения запроса в миллисекундах
 * @return Доменная модель ответа с статистикой
 * @throws IllegalStateException если ответ не содержит вариантов (choices)
 */
fun HuggingFaceChatResponse.toDomain(
    request: AiRequest,
    responseTimeMillis: Long
): AiResponse {
    val content = choices.firstOrNull()?.message?.content
        ?: throw IllegalStateException("HuggingFace response has no choices")

    // Преобразование статистики токенов
    val tokenUsage = TokenUsage(
        promptTokens = usage.promptTokens,
        completionTokens = usage.completionTokens,
        totalTokens = usage.totalTokens
    )

    return AiResponse(
        content = content,
        model = request.model,
        responseTimeMillis = responseTimeMillis,
        tokenUsage = tokenUsage
    )
}
