package com.example.mcptasks.data.remote

import com.example.mcptasks.data.remote.dto.ai.yandex.YandexGptCompletionOptionsDto
import com.example.mcptasks.data.remote.dto.ai.yandex.YandexGptCompletionRequest
import com.example.mcptasks.data.remote.dto.ai.yandex.YandexGptCompletionResponse
import com.example.mcptasks.data.remote.dto.ai.yandex.YandexGptMessageDto
import com.example.mcptasks.domain.model.ai.AiMessage
import com.example.mcptasks.domain.model.ai.AiModel
import com.example.mcptasks.domain.model.ai.AiRequest
import com.example.mcptasks.domain.model.ai.AiResponse
import com.example.mcptasks.domain.model.ai.MessageRole
import com.example.mcptasks.domain.model.ai.TokenUsage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * HTTP-клиент для работы с YandexGPT API.
 *
 * @property client Настроенный HTTP-клиент Ktor
 * @property apiKey API-ключ для Yandex Cloud
 * @property folderId ID каталога Yandex Cloud
 */
class YandexGptApiClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val folderId: String
) {
    /**
     * Отправляет запрос к YandexGPT Completion API.
     */
    suspend fun sendMessage(request: AiRequest): AiResponse {
        val requestDto = YandexGptCompletionRequest(
            modelUri = "gpt://$folderId/${request.model.modelId}",
            completionOptions = YandexGptCompletionOptionsDto(
                stream = false,
                temperature = request.temperature,
                maxTokens = request.maxTokens?.toString()
            ),
            messages = request.messages.map { it.toDto() }
        )

        val startTime = System.currentTimeMillis()
        val response: YandexGptCompletionResponse = client.post(request.model.endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Api-Key $apiKey")
            setBody(requestDto)
        }.body()
        val responseTimeMillis = System.currentTimeMillis() - startTime

        val alternative = response.result.alternatives.firstOrNull()
            ?: throw IllegalStateException("No alternatives in YandexGPT response")

        return AiResponse(
            content = alternative.message.text,
            model = request.model,
            responseTimeMillis = responseTimeMillis,
            tokenUsage = TokenUsage(
                promptTokens = response.result.usage.inputTextTokens.toIntOrNull() ?: 0,
                completionTokens = response.result.usage.completionTokens.toIntOrNull() ?: 0,
                totalTokens = response.result.usage.totalTokens.toIntOrNull() ?: 0
            )
        )
    }

    private fun AiMessage.toDto(): YandexGptMessageDto {
        return YandexGptMessageDto(
            role = when (role) {
                MessageRole.SYSTEM -> "system"
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
            },
            text = content
        )
    }
}
