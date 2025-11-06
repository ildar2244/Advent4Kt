package com.example.tgbot.data.repository

import com.example.tgbot.data.remote.ai.AiApiClient
import com.example.tgbot.data.remote.ai.ClaudeApiClient
import com.example.tgbot.data.remote.ai.OpenAiApiClient
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import com.example.tgbot.domain.repository.AiRepository

/**
 * Реализация репозитория для работы с AI-моделями.
 *
 * Выполняет маршрутизацию запросов к соответствующему API клиенту
 * в зависимости от выбранной модели. Инкапсулирует детали работы
 * с различными AI-провайдерами (OpenAI, Claude).
 *
 * @property openAiClient Клиент для работы с моделями OpenAI
 * @property claudeClient Клиент для работы с моделями Claude
 */
class AiRepositoryImpl(
    private val openAiClient: OpenAiApiClient,
    private val claudeClient: ClaudeApiClient
) : AiRepository {

    /**
     * Отправляет запрос к AI-модели.
     *
     * Автоматически выбирает нужный API клиент на основе модели в запросе.
     *
     * @param request Запрос с моделью, сообщениями и параметрами
     * @return Ответ от AI-модели
     */
    override suspend fun sendMessage(request: AiRequest): AiResponse {
        val client: AiApiClient = when (request.model) {
            AiModel.GPT_4O_MINI -> openAiClient
            AiModel.CLAUDE_HAIKU -> claudeClient
        }

        return client.sendMessage(request)
    }
}
