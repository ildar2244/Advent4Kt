package com.example.tgbot.data.remote.ai

import com.example.tgbot.data.remote.dto.ai.huggingface.HuggingFaceChatResponse
import com.example.tgbot.data.remote.dto.ai.huggingface.HuggingFaceErrorDto
import com.example.tgbot.data.remote.dto.ai.mapper.toDomain
import com.example.tgbot.data.remote.dto.ai.mapper.toHuggingFaceDto
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import com.example.tgbot.domain.model.ai.HuggingFaceModel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay

/**
 * HTTP-клиент для работы с HuggingFace Inference API.
 *
 * Поддерживает автоматический retry при 503 ошибках (модель загружается),
 * замер времени выполнения запроса и обработку статистики токенов.
 *
 * @property client Настроенный HTTP-клиент Ktor
 * @property apiKey API-ключ HuggingFace (формат: hf_xxxxx)
 */
class HuggingFaceApiClient(
    private val client: HttpClient,
    private val apiKey: String
) : AiApiClient {

    companion object {
        /**
         * Максимальное количество попыток при 503 ошибке.
         */
        private const val MAX_RETRIES = 3

        /**
         * Задержка между попытками по умолчанию (10 секунд).
         */
        private const val DEFAULT_RETRY_DELAY_MS = 10000L
    }

    /**
     * Отправляет запрос к HuggingFace Router Chat Completions API.
     *
     * Автоматически:
     * - Выбирает нужную модель из пользовательской сессии
     * - Формирует OpenAI-совместимый запрос
     * - Замеряет время выполнения
     * - Повторяет запрос при 503 ошибке (модель загружается)
     * - Преобразует ответ в доменную модель с статистикой
     *
     * @param request Доменная модель запроса
     * @return Доменная модель ответа с временем выполнения и статистикой токенов
     * @throws IllegalStateException если модель не загрузилась после всех попыток или ответ пустой
     */
    override suspend fun sendMessage(request: AiRequest): AiResponse {
        // Получаем выбранную HuggingFace модель из запроса
        val selectedModel = request.huggingFaceModel
            ?: throw IllegalStateException("HuggingFace model not specified in request")

        // Router API использует фиксированный endpoint (model передается в теле запроса)
        val endpoint = request.model.endpoint

        val requestDto = request.toHuggingFaceDto(selectedModel)
        println("REQUEST to HuggingFace (${selectedModel.displayName}): $requestDto")

        var lastException: Exception? = null

        // Retry механизм для обработки 503 ошибок
        repeat(MAX_RETRIES) { attempt ->
            try {
                // Замер времени выполнения запроса
                val startTime = System.currentTimeMillis()

                val httpResponse: HttpResponse = client.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(requestDto)
                }

                val responseTimeMillis = System.currentTimeMillis() - startTime

                // Проверка на 503 Service Unavailable (модель загружается)
                if (httpResponse.status == HttpStatusCode.ServiceUnavailable) {
                    val errorDto: HuggingFaceErrorDto = try {
                        httpResponse.body()
                    } catch (e: Exception) {
                        // Если не удалось распарсить ошибку, используем дефолтное время
                        HuggingFaceErrorDto(
                            error = "Model is loading",
                            estimatedTime = DEFAULT_RETRY_DELAY_MS / 1000.0
                        )
                    }

                    val waitTimeMs = errorDto.estimatedTime?.toLong()?.times(1000) ?: DEFAULT_RETRY_DELAY_MS

                    println(
                        "HuggingFace: модель ${selectedModel.displayName} загружается. " +
                                "Ожидание ${waitTimeMs / 1000} секунд (попытка ${attempt + 1}/$MAX_RETRIES)"
                    )

                    if (attempt < MAX_RETRIES - 1) {
                        delay(waitTimeMs)
                        return@repeat // Повторяем попытку
                    } else {
                        throw IllegalStateException(
                            "Модель ${selectedModel.displayName} загружается. " +
                                    "Попробуйте через ${errorDto.estimatedTime?.toInt() ?: 10} секунд."
                        )
                    }
                }

                // Успешный ответ - парсим и возвращаем
                val response: HuggingFaceChatResponse = httpResponse.body()
                println("RESPONSE from HuggingFace: успех за ${responseTimeMillis}ms")

                return response.toDomain(request, responseTimeMillis)

            } catch (e: Exception) {
                lastException = e
                println("HuggingFace: ошибка при попытке ${attempt + 1}/$MAX_RETRIES: ${e.message}")

                // Повторяем только при ошибках, связанных с 503
                if (attempt < MAX_RETRIES - 1 && is503RelatedError(e)) {
                    delay(DEFAULT_RETRY_DELAY_MS)
                } else {
                    throw e
                }
            }
        }

        // Если все попытки исчерпаны, выбрасываем последнюю ошибку
        throw lastException ?: IllegalStateException("Unknown error after $MAX_RETRIES retries")
    }

    /**
     * Проверяет, связана ли ошибка с 503 статусом.
     *
     * @param exception Исключение для проверки
     * @return true, если ошибка связана с 503, false иначе
     */
    private fun is503RelatedError(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("503") ||
                message.contains("service unavailable") ||
                message.contains("model is loading")
    }
}
