package com.example.tgbot.data.remote.ai

import com.example.tgbot.data.remote.dto.ai.mapper.toDomain
import com.example.tgbot.data.remote.dto.ai.mapper.toYandexGptDto
import com.example.tgbot.data.remote.dto.ai.yandex.YandexGptCompletionResponse
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * HTTP-клиент для работы с YandexGPT API.
 *
 * Использует Yandex Cloud Foundation Models API для доступа к YandexGPT.
 * Поддерживает как YandexGPT Lite, так и YandexGPT Pro модели.
 *
 * @property client Настроенный HTTP-клиент Ktor
 * @property apiKey API-ключ для Yandex Cloud (может быть Api-Key или IAM токен)
 * @property folderId ID каталога Yandex Cloud, в котором размещена модель
 */
class YandexGptApiClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val folderId: String
) : AiApiClient {

    /**
     * Отправляет запрос к YandexGPT Completion API.
     *
     * Аутентификация: используется заголовок "Authorization: Api-Key {apiKey}"
     * Альтернативно можно использовать "Authorization: Bearer {IAM_TOKEN}"
     *
     * @param request Доменная модель запроса
     * @return Доменная модель ответа с сгенерированным текстом
     */
    override suspend fun sendMessage(request: AiRequest): AiResponse {
        val requestDto = request.toYandexGptDto(folderId)
        println("REQUEST to YANDEX_GPT: $requestDto")

        val response: YandexGptCompletionResponse = client.post(request.model.endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Api-Key $apiKey")
            setBody(requestDto)
        }.body()

        return response.toDomain(request)
    }
}
