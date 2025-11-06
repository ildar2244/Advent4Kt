package com.example.tgbot.data.remote.ai

import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse

/**
 * Интерфейс для работы с AI API.
 */
interface AiApiClient {
    /**
     * Отправляет запрос к AI-модели и получает ответ.
     *
     * @param request Запрос с сообщениями и параметрами
     * @return Ответ от AI-модели
     */
    suspend fun sendMessage(request: AiRequest): AiResponse
}
