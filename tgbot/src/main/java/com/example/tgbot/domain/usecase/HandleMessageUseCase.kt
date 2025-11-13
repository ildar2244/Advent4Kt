package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.Experts
import com.example.tgbot.domain.model.Message
import com.example.tgbot.domain.model.Scenario
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.SystemPrompts
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.repository.AiRepository
import com.example.tgbot.domain.repository.TelegramRepository
import com.example.tgbot.domain.service.HistoryCompressor
import com.example.tgbot.domain.util.TokenCounter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Use case для обработки обычных текстовых сообщений (не команд).
 *
 * Режимы работы:
 * - Если AI-модель не выбрана: работает в эхо-режиме (возвращает текст пользователя)
 * - Если модель выбрана: отправляет запрос к AI с учетом текущего сценария
 *
 * Поддержка сценариев:
 * - FREE_CHAT: одиночный запрос без истории и дополнительных промптов
 * - JSON_FORMAT: одиночный запрос с system prompt для JSON-ответов
 * - CONSULTANT: использует историю диалога (до 20 последних сообщений) для контекстных ответов
 * - STEP_BY_STEP: одиночный запрос с system prompt для пошагового решения
 * - COMPRESSION: использует историю с автоматической компрессией при превышении лимита токенов (YandexGPT Lite)
 * - EXPERTS: параллельные независимые запросы к нескольким экспертам (без истории)
 */
class HandleMessageUseCase(
    private val telegramRepository: TelegramRepository,
    private val aiRepository: AiRepository,
    private val historyCompressor: HistoryCompressor
) {
    /**
     * Обрабатывает входящее сообщение.
     * Проверяет наличие активной AI-сессии и направляет обработку соответствующим образом.
     *
     * @param message Входящее сообщение от пользователя
     */
    suspend operator fun invoke(message: Message) {
        val userText = message.text ?: return
        val session = SessionManager.getSession(message.chatId)

        // Если модель не выбрана, работаем в эхо-режиме
        if (session.selectedModel == null) {
            telegramRepository.sendMessage(message.chatId, userText)
            return
        }

        // Проверяем текущий сценарий
        when (session.currentScenario) {
            Scenario.EXPERTS -> handleExpertsScenario(message.chatId, userText, session.selectedModel!!)
            else -> handleAiMessage(message.chatId, userText, session.selectedModel!!, session.currentScenario)
        }
    }

    /**
     * Обрабатывает сообщение в режиме AI-консультации.
     * Отправляет запрос к AI-модели и возвращает ответ пользователю.
     * Применяет system prompt в зависимости от выбранного сценария.
     * Для сценариев CONSULTANT и COMPRESSION используется история сообщений,
     * для остальных - одиночные запросы.
     *
     * Для COMPRESSION применяется гибридный метод подсчёта токенов:
     * - Используются точные promptTokens из предыдущего ответа API
     * - Оценивается только новое сообщение пользователя
     * - При превышении лимита (~7372 токена для YandexGPT Lite) выполняется компрессия
     *
     * @param chatId ID чата
     * @param userText Текст сообщения от пользователя
     * @param model Выбранная AI-модель
     * @param scenario Текущий сценарий взаимодействия
     */
    private suspend fun handleAiMessage(
        chatId: Long,
        userText: String,
        model: AiModel,
        scenario: Scenario
    ) {
        try {
            val session = SessionManager.getSession(chatId)
            val isConsultantMode = scenario == Scenario.CONSULTANT
            val isCompressionMode = scenario == Scenario.COMPRESSION

            // Создаем сообщение пользователя
            val userMessage = AiMessage(role = MessageRole.USER, content = userText)

            // Проверка на необходимость компрессии (ДО добавления в историю)
            if (isCompressionMode && model == AiModel.YANDEX_GPT_LITE) {
                println("CHECK COMPRESSION")
                val historyTokens = session.lastPromptTokens
                val newMessageTokens = TokenCounter.estimateTokens(userText)

                val historySize = session.conversationHistory.size
                println("HISTORY SIZE: $historySize")

//                if (TokenCounter.shouldCompress(historyTokens, newMessageTokens)) {
                if (historySize >= 10) {
                    println("SHOULD COMPRESSION")
                    // Выполняем компрессию истории
                    val summary = historyCompressor.compressHistory(
                        history = session.conversationHistory,
                        model = model,
                        temperature = session.temperature,
                        huggingFaceModel = session.selectedHuggingFaceModel
                    )

                    // Заменяем историю на summary
                    SessionManager.replaceHistory(chatId, listOf(summary))
                    SessionManager.incrementCompressionCount(chatId)

                    // Уведомляем пользователя о компрессии
                    telegramRepository.sendMessage(
                        chatId,
                        "🗜️ История сжата (было ~$historyTokens токенов, превышен лимит ${TokenCounter.TOKEN_LIMIT})"
                    )
                }
            }

            // Определяем: сохранять ли в историю
            val conversationHistory: MutableList<AiMessage> = if (isConsultantMode || isCompressionMode) {
                // CONSULTANT или COMPRESSION: добавляем в историю и используем её
                SessionManager.addMessage(chatId, userMessage)
                session.conversationHistory.toMutableList()
            } else {
                // Остальные сценарии: создаем временный список только для этого запроса
                mutableListOf(userMessage)
            }

            // Добавляем system prompt в зависимости от сценария (если нужно)
            val systemPrompt = getSystemPromptForScenario(scenario)
            if (systemPrompt != null) {
                if (isConsultantMode || isCompressionMode) {
                    // Для CONSULTANT и COMPRESSION: обновляем/добавляем в начало истории
                    val firstSystemIndex = conversationHistory.indexOfFirst { it.role == MessageRole.SYSTEM }
                    if (firstSystemIndex != -1) {
                        // Заменяем существующий system prompt
                        conversationHistory[firstSystemIndex] = AiMessage(
                            role = MessageRole.SYSTEM,
                            content = systemPrompt
                        )
                    } else {
                        // Добавляем новый system prompt в начало
                        conversationHistory.add(0, AiMessage(
                            role = MessageRole.SYSTEM,
                            content = systemPrompt
                        ))
                    }
                } else {
                    // Для остальных сценариев: просто добавляем в начало временного списка
                    conversationHistory.add(0, AiMessage(
                        role = MessageRole.SYSTEM,
                        content = systemPrompt
                    ))
                }
            }

            // Создаем запрос к AI с температурой из сессии
            val aiRequest = AiRequest(
                model = model,
                messages = conversationHistory,
                temperature = session.temperature,
                huggingFaceModel = if (model == com.example.tgbot.domain.model.ai.AiModel.HUGGING_FACE) {
                    session.selectedHuggingFaceModel
                } else {
                    null
                }
            )

            // Отправляем запрос к AI
            val aiResponse = aiRepository.sendMessage(aiRequest)

            // Сохраняем точное значение promptTokens для гибридного метода (COMPRESSION)
            if (isCompressionMode) {
                aiResponse.tokenUsage?.let { usage ->
                    SessionManager.updatePromptTokens(chatId, usage.promptTokens)
                }
            }

            // Добавляем ответ AI в историю для CONSULTANT и COMPRESSION
            if (isConsultantMode || isCompressionMode) {
                SessionManager.addMessage(
                    chatId,
                    AiMessage(role = MessageRole.ASSISTANT, content = aiResponse.content)
                )
            }

            // Формируем ответ с информацией о модели, temperature и статистикой
            val responseText = buildString {
                append(aiResponse.content)
                append("\n\n")

                append("========================\n")
                // Добавляем статистику времени выполнения
                aiResponse.responseTimeMillis?.let { time ->
                    append("\uD83D\uDD52 Время ответа: ${time} мс\n")
                }

                // Добавляем статистику токенов
                aiResponse.tokenUsage?.let { usage ->
                    append("\uD83D\uDD22 Токены:\n")
                    append("  - запрос: ${usage.promptTokens}\n")
                    append("  - ответ: ${usage.completionTokens}\n")
                    append("  - всего: ${usage.totalTokens}\n")
                } ?: run {
                    append("\uD83D\uDD22 Токены: n/a\n")
                }

                // Для COMPRESSION показываем статистику истории
                if (isCompressionMode) {
                    val updatedSession = SessionManager.getSession(chatId)
                    append("\uD83D\uDCCA Статистика истории:\n")
                    append("  - Токенов в истории: ~${updatedSession.lastPromptTokens} / ${TokenCounter.TOKEN_LIMIT}\n")
                    append("  - Использование контекста: ${TokenCounter.calculateUsagePercent(updatedSession.lastPromptTokens)}%\n")
                    append("  - Сжатий выполнено: ${updatedSession.compressionCount}\n\n")
                }

                append("\n")
                // Для HuggingFace показываем конкретную модель
                val modelName = if (model == AiModel.HUGGING_FACE) {
                    session.selectedHuggingFaceModel?.displayName ?: model.displayName
                } else {
                    model.displayName
                }
                append("model: $modelName\n")
                append("temperature: ${session.temperature}\n")
            }

            // Отправляем ответ пользователю
            telegramRepository.sendMessage(chatId, responseText)

        } catch (e: Exception) {
            // Обрабатываем ошибки и отправляем понятное сообщение пользователю
            telegramRepository.sendMessage(
                chatId,
                "Произошла ошибка при обращении к AI:\n${e.message}\n\nПопробуйте еще раз или используйте /stop для выхода."
            )
        }
    }

    /**
     * Возвращает system prompt для указанного сценария.
     *
     * @param scenario Сценарий взаимодействия
     * @return System prompt или null для FREE_CHAT и EXPERTS
     */
    private fun getSystemPromptForScenario(scenario: Scenario): String? {
        return when (scenario) {
            Scenario.FREE_CHAT -> null
            Scenario.JSON_FORMAT -> SystemPrompts.JSON_FORMAT
            Scenario.CONSULTANT -> SystemPrompts.CONSULTANT
            Scenario.STEP_BY_STEP -> SystemPrompts.STEP_BY_STEP
            Scenario.COMPRESSION -> SystemPrompts.COMPRESSION
            Scenario.EXPERTS -> null // Этот сценарий обрабатывается отдельно
        }
    }

    /**
     * Обрабатывает сообщение в сценарии "Эксперты".
     * Отправляет несколько параллельных запросов к AI с разными system prompts.
     * Количество запросов определяется динамически размером списка Experts.list.
     * Каждый запрос независим (без истории сообщений).
     *
     * @param chatId ID чата
     * @param userText Текст сообщения от пользователя
     * @param model Выбранная AI-модель
     */
    private suspend fun handleExpertsScenario(
        chatId: Long,
        userText: String,
        model: com.example.tgbot.domain.model.ai.AiModel
    ) {
        try {
            val session = SessionManager.getSession(chatId)

            // Запускаем параллельные запросы к AI для каждого эксперта
            coroutineScope {
                val deferredResponses = Experts.list.map { expert ->
                    async {
                        try {
                            // Создаем независимый запрос для каждого эксперта
                            val expertHistory = mutableListOf<AiMessage>()

                            // Добавляем system prompt эксперта
                            expertHistory.add(
                                AiMessage(
                                    role = MessageRole.SYSTEM,
                                    content = expert.systemPrompt
                                )
                            )

                            // Добавляем только ТЕКУЩЕЕ сообщение пользователя
                            expertHistory.add(
                                AiMessage(
                                    role = MessageRole.USER,
                                    content = userText
                                )
                            )

                            // Создаем запрос к AI с температурой из сессии
                            val aiRequest = AiRequest(
                                model = model,
                                messages = expertHistory,
                                temperature = session.temperature,
                                huggingFaceModel = if (model == com.example.tgbot.domain.model.ai.AiModel.HUGGING_FACE) {
                                    session.selectedHuggingFaceModel
                                } else {
                                    null
                                }
                            )

                            // Отправляем запрос к AI
                            val aiResponse = aiRepository.sendMessage(aiRequest)

                            // Возвращаем тройку: имя эксперта, ответ и статистику
                            Triple(expert.name, aiResponse.content, aiResponse)
                        } catch (e: Exception) {
                            // В случае ошибки возвращаем null для aiResponse
                            Triple(expert.name, "Ошибка: ${e.message}", null)
                        }
                    }
                }

                // Получаем ответы по мере их поступления
                deferredResponses.forEach { deferred ->
                    val (expertName, response, aiResponse) = deferred.await()

                    // Формируем ответ с информацией о модели, temperature и статистикой
                    val responseText = buildString {
                        append("$expertName:\n\n")
                        append(response)
                        append("\n\n")

                        append("========================\n")
                        // Добавляем статистику, если она доступна
                        aiResponse?.let { resp ->
                            // Статистика времени выполнения
                            resp.responseTimeMillis?.let { time ->
                                append("\uD83D\uDD52 Время ответа: ${time} мс\n")
                            }

                            // Статистика токенов
                            resp.tokenUsage?.let { usage ->
                                append("\uD83D\uDD22 Токены:\n")
                                append("  - запрос: ${usage.promptTokens}\n")
                                append("  - ответ: ${usage.completionTokens}\n")
                                append("  - всего: ${usage.totalTokens}\n")
                            } ?: run {
                                append("\uD83D\uDD22 Токены: n/a\n")
                            }

                            append("\n")
                        }

                        append("\n")
                        // Для HuggingFace показываем конкретную модель
                        val modelName = if (model == com.example.tgbot.domain.model.ai.AiModel.HUGGING_FACE) {
                            session.selectedHuggingFaceModel?.displayName ?: model.displayName
                        } else {
                            model.displayName
                        }
                        append("model: $modelName\n")
                        append("temperature: ${session.temperature}\n")
                    }

                    // Отправляем ответ каждого эксперта отдельным сообщением
                    telegramRepository.sendMessage(chatId, responseText)
                }
            }

            // Сценарий EXPERTS не использует историю - каждый запрос независим

        } catch (e: Exception) {
            // Обрабатываем ошибки и отправляем понятное сообщение пользователю
            telegramRepository.sendMessage(
                chatId,
                "Произошла ошибка при обращении к экспертам:\n${e.message}\n\nПопробуйте еще раз или используйте /stop для выхода."
            )
        }
    }
}
