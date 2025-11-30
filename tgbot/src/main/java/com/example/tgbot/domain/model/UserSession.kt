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
 * @property ragChatState Состояние RAG-чата (последние результаты RAG-поиска).
 *                        Используется в сценарии RAG_CHAT для отображения источников.
 */
data class UserSession(
    val selectedModel: AiModel? = null,
    val selectedHuggingFaceModel: HuggingFaceModel = HuggingFaceModel.DEFAULT,
    val conversationHistory: MutableList<AiMessage> = mutableListOf(),
    val currentScenario: Scenario = Scenario.DEFAULT,
    val temperature: Double = 0.7,
    val lastPromptTokens: Int = 0,
    val compressionCount: Int = 0,
    val ragInteractiveState: RagInteractiveState? = null,
    val ragChatState: RagChatState? = null
)

/**
 * Менеджер для управления сессиями пользователей.
 * Хранит состояние диалогов в памяти (in-memory).
 */
object SessionManager {
    private val sessions = mutableMapOf<Long, UserSession>()

    /**
     * Максимальное количество сообщений в истории.
     * Используется для ограничения размера истории в сценарии CONSULTANT и COMPRESSION.
     */
    private const val MAX_HISTORY_SIZE = 20

    /**
     * Максимальное количество сообщений в истории для сценария RAG_CHAT.
     * Меньший лимит учитывает дополнительный RAG контекст (3 чанка при каждом запросе).
     */
    private const val MAX_RAG_CHAT_HISTORY = 10

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
     * Если переходим НЕ на CONSULTANT, COMPRESSION или RAG_CHAT, история очищается.
     * Если переходим НЕ на RAG_INTERACTIVE, очищается состояние RAG-поиска.
     * Если переходим НЕ на RAG_CHAT, очищается состояние RAG-чата.
     *
     * @param chatId ID чата пользователя
     * @param scenario Выбранный сценарий
     */
    fun setScenario(chatId: Long, scenario: Scenario) {
        val session = getSession(chatId)

        // Очищаем RAG Interactive state при смене сценария
        val newRagInteractiveState = if (scenario != Scenario.RAG_INTERACTIVE) null else session.ragInteractiveState

        // Очищаем RAG Chat state при смене сценария
        val newRagChatState = if (scenario != Scenario.RAG_CHAT) null else session.ragChatState

        // Очищаем историю при переходе на любой сценарий кроме CONSULTANT, COMPRESSION и RAG_CHAT
        if (scenario !in listOf(Scenario.CONSULTANT, Scenario.COMPRESSION, Scenario.RAG_CHAT)) {
            session.conversationHistory.clear()
        }

        sessions[chatId] = session.copy(
            currentScenario = scenario,
            ragInteractiveState = newRagInteractiveState,
            ragChatState = newRagChatState
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
     * Автоматически ограничивает историю в зависимости от сценария:
     * - RAG_CHAT: 10 сообщений (меньший лимит из-за дополнительного RAG контекста)
     * - CONSULTANT, COMPRESSION: 20 сообщений
     *
     * @param chatId ID чата пользователя
     * @param message Сообщение для добавления в историю
     */
    fun addMessage(chatId: Long, message: AiMessage) {
        val session = getSession(chatId)
        session.conversationHistory.add(message)

        // Определяем лимит в зависимости от сценария
        val limit = if (session.currentScenario == Scenario.RAG_CHAT) {
            MAX_RAG_CHAT_HISTORY
        } else {
            MAX_HISTORY_SIZE
        }

        // Ограничиваем историю последними N сообщениями
        if (session.conversationHistory.size > limit) {
            // Удаляем самые старые сообщения, оставляя только последние N
            val toRemove = session.conversationHistory.size - limit
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

    /**
     * Устанавливает состояние RAG-чата для пользователя.
     *
     * @param chatId ID чата пользователя
     * @param state Новое состояние RAG-чата (null для очистки)
     */
    fun setRagChatState(chatId: Long, state: RagChatState?) {
        val session = getSession(chatId)
        sessions[chatId] = session.copy(ragChatState = state)
    }

    /**
     * Получает состояние RAG-чата для пользователя.
     *
     * @param chatId ID чата пользователя
     * @return Состояние RAG-чата или null если не установлено
     */
    fun getRagChatState(chatId: Long): RagChatState? {
        return getSession(chatId).ragChatState
    }
}
