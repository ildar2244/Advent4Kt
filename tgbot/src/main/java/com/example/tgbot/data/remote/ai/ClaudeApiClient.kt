package com.example.tgbot.data.remote.ai

import com.example.tgbot.data.remote.dto.ai.claude.ClaudeMessageResponse
import com.example.tgbot.data.remote.dto.ai.mapper.toClaudeDto
import com.example.tgbot.data.remote.dto.ai.mapper.toDomain
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * HTTP-клиент для работы с Claude API через ProxyAPI.
 *
 * Использует ProxyAPI (api.proxyapi.ru) в качестве промежуточного сервера
 * для доступа к Claude Messages API.
 *
 * @property client Настроенный HTTP-клиент Ktor
 * @property apiKey API-ключ для ProxyAPI (CLAUDE_API_KEY)
 */
class ClaudeApiClient(
    private val client: HttpClient,
    private val apiKey: String
) : AiApiClient {

    /**
     * Отправляет запрос к Claude Messages API.
     *
     * Аутентификация: используются специальные заголовки Claude:
     * - "x-api-key" - ключ API
     * - "anthropic-version" - версия API (2023-06-01)
     *
     * @param request Доменная модель запроса
     * @return Доменная модель ответа с сгенерированным текстом
     */
    override suspend fun sendMessage(request: AiRequest): AiResponse {
        val requestDto = request.toClaudeDto()

        val response: ClaudeMessageResponse = client.post(request.model.endpoint) {
            contentType(ContentType.Application.Json)
            // Claude требует специальные заголовки
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            setBody(requestDto)
        }.body()

        return response.toDomain(request)
    }
}
