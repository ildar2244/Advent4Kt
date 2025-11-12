package com.example.tgbot.data.remote.ai

import com.example.tgbot.data.remote.dto.ai.claude.ClaudeMessageResponse
import com.example.tgbot.data.remote.dto.ai.mapper.toClaudeDto
import com.example.tgbot.data.remote.dto.ai.mapper.toDomain
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
     * Логирование: для отладки выводится полный JSON запроса и тело ошибки при неудаче.
     *
     * @param request Доменная модель запроса
     * @return Доменная модель ответа с сгенерированным текстом
     * @throws IllegalStateException если API вернул ошибку
     */
    override suspend fun sendMessage(request: AiRequest): AiResponse {
        val requestDto = request.toClaudeDto()
        println("REQUEST to CLAUDE: $requestDto")

        // Логируем JSON тела запроса для отладки
        val json = Json { prettyPrint = true }
        val requestJson = json.encodeToString(requestDto)
//        println("Claude API Request JSON:\n$requestJson")

        try {
            val startTime = System.currentTimeMillis()
            val httpResponse: HttpResponse = client.post(request.model.endpoint) {
                contentType(ContentType.Application.Json)
                // Claude требует специальные заголовки
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(requestDto)
            }
            val responseTimeMillis = System.currentTimeMillis() - startTime

            // Если ответ не успешный, выводим тело ошибки для диагностики
            if (!httpResponse.status.isSuccess()) {
                val errorBody = httpResponse.bodyAsText()
                println("Claude API Error Response (${httpResponse.status}):\n$errorBody")
                throw IllegalStateException("Claude API returned ${httpResponse.status}: $errorBody")
            }

            val response: ClaudeMessageResponse = httpResponse.body()
            return response.toDomain(request, responseTimeMillis)

        } catch (e: Exception) {
            println("Claude API Exception: ${e.message}")
            throw e
        }
    }
}
