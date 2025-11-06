package com.example.tgbot.domain.repository

import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse

/**
 * Репозиторий для работы с AI-моделями.
 */
interface AiRepository {
    /**
     * Отправляет запрос к AI-модели и получает ответ.
     *
     * @param request Запрос с моделью, сообщениями и параметрами
     * @return Ответ от AI-модели
     */
    suspend fun sendMessage(request: AiRequest): AiResponse
}
