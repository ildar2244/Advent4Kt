package com.example.tgbot.domain.model

import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.model.ai.HuggingFaceModel

/**
 * Сессия пользователя для отслеживания состояния диалога с AI.
 *
 * @property selectedModel Выбранная AI-модель (null, если модель не выбрана)
 * @property selectedHuggingFaceModel Выбранная модель HuggingFace (используется когда selectedModel == HUGGING_FACE)
 * @property conversationHistory История сообщений в диалоге с AI.
 *                               Используется для сценариев CONSULTANT и COMPRESSION.
 *                               Для остальных сценариев каждый запрос независим.
 *                               Автоматически ограничивается 20 последними сообщениями.
 * @property currentScenario Текущий сценарий взаимодействия с AI
 * @property temperature Параметр temperature для генерации ответов AI (0.0 - 1.0)
 * @property lastPromptTokens Точное количество токенов из последнего ответа API (promptTokens).
 *                            Используется для гибридного метода подсчёта токенов.
 * @property compressionCount Количество выполненных компрессий истории в текущей сессии
 * @property ragInteractiveState Состояние интерактивного RAG-поиска (query, результаты, текущая попытка).
 *                               Используется в сценарии RAG_INTERACTIVE для отслеживания прогресса поиска.
 */
data class UserSession(
    val selectedModel: AiModel? = null,
    val selectedHuggingFaceModel: HuggingFaceModel = HuggingFaceModel.DEFAULT,
    val conversationHistory: MutableList<AiMessage> = mutableListOf(),
    val currentScenario: Scenario = Scenario.DEFAULT,
    val temperature: Double = 0.7,
    val lastPromptTokens: Int = 0,
    val compressionCount: Int = 0,
    val ragInteractiveState: RagInteractiveState? = null
)

/**
 * Менеджер для управления сессиями пользователей.
 * Хранит состояние диалогов в памяти (in-memory).
 */
object SessionManager {
    private val sessions = mutableMapOf<Long, UserSession>()

    /**
     * Максимальное количество сообщений в истории.
     * Используется для ограничения размера истории в сценарии CONSULTANT.
     */
    private const val MAX_HISTORY_SIZE = 20

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
     * При смене модели температура сбрасывается на значение по умолчанию (0.7),
     * история сообщений очищается, а счётчики токенов обнуляются.
     *
     * @param chatId ID чата пользователя
     * @param model Выбранная AI-модель
     */
    fun setModel(chatId: Long, model: AiModel) {
        val session = getSession(chatId)
        session.conversationHistory.clear()
        sessions[chatId] = session.copy(
            selectedModel = model,
            temperature = 0.7,
            lastPromptTokens = 0,
            compressionCount = 0
        )
    }

    /**
     * Устанавливает текущий сценарий для пользователя.
     * Если переходим НЕ на CONSULTANT или COMPRESSION, история очищается.
     * Если переходим НЕ на RAG_INTERACTIVE, очищается состояние RAG-поиска.
     *
     * @param chatId ID чата пользователя
     * @param scenario Выбранный сценарий
     */
    fun setScenario(chatId: Long, scenario: Scenario) {
        val session = getSession(chatId)

        // Очищаем RAG Interactive state при смене сценария
        val newRagState = if (scenario != Scenario.RAG_INTERACTIVE) null else session.ragInteractiveState

        // Очищаем историю при переходе на любой сценарий кроме CONSULTANT и COMPRESSION
        if (scenario != Scenario.CONSULTANT && scenario != Scenario.COMPRESSION) {
            session.conversationHistory.clear()
        }

        sessions[chatId] = session.copy(
            currentScenario = scenario,
            ragInteractiveState = newRagState
        )
    }

    /**
     * Устанавливает значение temperature для пользователя.
     *
     * @param chatId ID чата пользователя
     * @param temperature Значение температуры (0.0 - 1.0)
     */
    fun setTemperature(chatId: Long, temperature: Double) {
        val session = getSession(chatId)
        sessions[chatId] = session.copy(temperature = temperature)
    }

    /**
     * Устанавливает выбранную модель HuggingFace для пользователя.
     * История сообщений очищается при смене модели.
     *
     * @param chatId ID чата пользователя
     * @param hfModel Выбранная модель HuggingFace
     */
    fun setHuggingFaceModel(chatId: Long, hfModel: HuggingFaceModel) {
        val session = getSession(chatId)
        session.conversationHistory.clear()
        sessions[chatId] = session.copy(selectedHuggingFaceModel = hfModel)
    }

    /**
     * Добавляет сообщение в историю диалога.
     * Автоматически ограничивает историю последними 20 сообщениями.
     * Используется для сценариев CONSULTANT и COMPRESSION.
     *
     * @param chatId ID чата пользователя
     * @param message Сообщение для добавления в историю
     */
    fun addMessage(chatId: Long, message: AiMessage) {
        val session = getSession(chatId)
        session.conversationHistory.add(message)

        // Ограничиваем историю последними 20 сообщениями
        if (session.conversationHistory.size > MAX_HISTORY_SIZE) {
            // Удаляем самые старые сообщения, оставляя только последние MAX_HISTORY_SIZE
            val toRemove = session.conversationHistory.size - MAX_HISTORY_SIZE
            repeat(toRemove) {
                session.conversationHistory.removeAt(0)
            }
        }
    }

    /**
     * Сохраняет точное количество токенов из последнего ответа API.
     * Используется для гибридного метода подсчёта токенов.
     *
     * @param chatId ID чата пользователя
     * @param promptTokens Точное количество токенов из поля `promptTokens` в ответе API
     */
    fun updatePromptTokens(chatId: Long, promptTokens: Int) {
        val session = getSession(chatId)
        sessions[chatId] = session.copy(lastPromptTokens = promptTokens)
    }

    /**
     * Увеличивает счётчик выполненных компрессий истории.
     *
     * @param chatId ID чата пользователя
     */
    fun incrementCompressionCount(chatId: Long) {
        val session = getSession(chatId)
        sessions[chatId] = session.copy(compressionCount = session.compressionCount + 1)
    }

    /**
     * Заменяет всю историю диалога новым списком сообщений.
     * Используется при компрессии истории (замена на summary).
     * Счётчик токенов сбрасывается, так как история изменена.
     *
     * @param chatId ID чата пользователя
     * @param newHistory Новая история (обычно содержит только summary)
     */
    fun replaceHistory(chatId: Long, newHistory: List<AiMessage>) {
        val session = getSession(chatId)
        sessions[chatId] = session.copy(
            conversationHistory = newHistory.toMutableList(),
            lastPromptTokens = 0  // Сбросить, пересчитается при следующем запросе
        )
    }

    /**
     * Очищает сессию пользователя (сбрасывает модель и историю).
     *
     * @param chatId ID чата пользователя
     */
    fun clearSession(chatId: Long) {
        sessions.remove(chatId)
    }

    /**
     * Устанавливает состояние RAG-поиска для пользователя.
     *
     * @param chatId ID чата пользователя
     * @param state Новое состояние RAG-поиска (null для очистки)
     */
    fun setRagInteractiveState(chatId: Long, state: RagInteractiveState?) {
        val session = getSession(chatId)
        sessions[chatId] = session.copy(ragInteractiveState = state)
    }

    /**
     * Получает состояние RAG-поиска для пользователя.
     *
     * @param chatId ID чата пользователя
     * @return Состояние RAG-поиска или null если не установлено
     */
    fun getRagInteractiveState(chatId: Long): RagInteractiveState? {
        return getSession(chatId).ragInteractiveState
    }
}
