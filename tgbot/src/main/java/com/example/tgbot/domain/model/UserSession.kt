package com.example.tgbot.domain.model

import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiModel

/**
 * Сессия пользователя для отслеживания состояния диалога с AI.
 *
 * @property selectedModel Выбранная AI-модель (null, если модель не выбрана)
 * @property conversationHistory История сообщений в диалоге с AI
 * @property currentScenario Текущий сценарий взаимодействия с AI
 */
data class UserSession(
    val selectedModel: AiModel? = null,
    val conversationHistory: MutableList<AiMessage> = mutableListOf(),
    val currentScenario: Scenario = Scenario.DEFAULT
)

/**
 * Менеджер для управления сессиями пользователей.
 * Хранит состояние диалогов в памяти (in-memory).
 */
object SessionManager {
    private val sessions = mutableMapOf<Long, UserSession>()

    /**
     * Получает сессию пользователя. Если сессии нет, создает новую.
     *
     * @param chatId ID чата пользователя
     * @return Сессия пользователя
     */
    fun getSession(chatId: Long): UserSession {
        return sessions.getOrPut(chatId) { UserSession() }
    }

    /**
     * Устанавливает выбранную модель для пользователя.
     *
     * @param chatId ID чата пользователя
     * @param model Выбранная AI-модель
     */
    fun setModel(chatId: Long, model: AiModel) {
        val session = getSession(chatId)
        sessions[chatId] = session.copy(selectedModel = model)
    }

    /**
     * Устанавливает текущий сценарий для пользователя.
     *
     * @param chatId ID чата пользователя
     * @param scenario Выбранный сценарий
     */
    fun setScenario(chatId: Long, scenario: Scenario) {
        val session = getSession(chatId)
        sessions[chatId] = session.copy(currentScenario = scenario)
    }

    /**
     * Добавляет сообщение в историю диалога.
     *
     * @param chatId ID чата пользователя
     * @param message Сообщение для добавления в историю
     */
    fun addMessage(chatId: Long, message: AiMessage) {
        val session = getSession(chatId)
        session.conversationHistory.add(message)
    }

    /**
     * Очищает сессию пользователя (сбрасывает модель и историю).
     *
     * @param chatId ID чата пользователя
     */
    fun clearSession(chatId: Long) {
        sessions.remove(chatId)
    }
}
