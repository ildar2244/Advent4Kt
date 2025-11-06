package com.example.tgbot.data.remote.ai

import com.example.tgbot.data.remote.dto.ai.mapper.toDomain
import com.example.tgbot.data.remote.dto.ai.mapper.toOpenAiDto
import com.example.tgbot.data.remote.dto.ai.openai.OpenAiChatResponse
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * HTTP-клиент для работы с OpenAI API через ProxyAPI.
 *
 * Использует ProxyAPI (api.proxyapi.ru) в качестве промежуточного сервера
 * для доступа к OpenAI Chat Completions API.
 *
 * @property client Настроенный HTTP-клиент Ktor
 * @property apiKey API-ключ для ProxyAPI (OPENAI_API_KEY)
 */
class OpenAiApiClient(
    private val client: HttpClient,
    private val apiKey: String
) : AiApiClient {

    /**
     * Отправляет запрос к OpenAI Chat Completions API.
     *
     * Аутентификация: используется заголовок "Authorization: Bearer {apiKey}"
     *
     * @param request Доменная модель запроса
     * @return Доменная модель ответа с сгенерированным текстом
     */
    override suspend fun sendMessage(request: AiRequest): AiResponse {
        val requestDto = request.toOpenAiDto()

        val response: OpenAiChatResponse = client.post(request.model.endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(requestDto)
        }.body()

        return response.toDomain(request)
    }
}
