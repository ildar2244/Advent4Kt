package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.Experts
import com.example.tgbot.domain.model.InlineKeyboard
import com.example.tgbot.domain.model.InlineKeyboardButton
import com.example.tgbot.domain.model.Message
import com.example.tgbot.domain.model.RagInteractiveState
import com.example.tgbot.domain.model.Scenario
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.SystemPrompts
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.repository.AiRepository
import com.example.tgbot.domain.repository.McpRepository
import com.example.tgbot.domain.repository.RagRepository
import com.example.tgbot.domain.repository.SummaryRepository
import com.example.tgbot.domain.repository.TelegramRepository
import com.example.tgbot.domain.service.HistoryCompressor
import com.example.tgbot.domain.util.TokenCounter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Use case для обработки обычных текстовых сообщений (не команд) и location messages.
 *
 * Режимы работы:
 * - Если AI-модель не выбрана: работает в эхо-режиме (возвращает текст пользователя)
 * - Если модель выбрана: отправляет запрос к AI с учетом текущего сценария
 * - Если получена геолокация: получает прогноз погоды через MCP Weather Server
 *
 * Поддержка сценариев:
 * - FREE_CHAT: одиночный запрос без истории и дополнительных промптов
 * - JSON_FORMAT: одиночный запрос с system prompt для JSON-ответов
 * - CONSULTANT: использует историю диалога (до 20 последних сообщений) для контекстных ответов
 * - STEP_BY_STEP: одиночный запрос с system prompt для пошагового решения
 * - COMPRESSION: использует историю с автоматической компрессией при превышении лимита токенов (YandexGPT Lite)
 * - EXPERTS: параллельные независимые запросы к нескольким экспертам (без истории)
 * - RAG_INTERACTIVE: интерактивный RAG-поиск с возможностью пробовать разные группы чанков (по 3 шт.)
 */
class HandleMessageUseCase(
    private val telegramRepository: TelegramRepository,
    private val aiRepository: AiRepository,
    private val historyCompressor: HistoryCompressor,
    private val summaryRepository: SummaryRepository,
    private val mcpRepository: McpRepository,
    private val ragRepository: RagRepository
) {
    /**
     * Обрабатывает входящее сообщение.
     * Проверяет наличие активной AI-сессии и направляет обработку соответствующим образом.
     * Обрабатывает location messages для получения прогноза погоды.
     *
     * @param message Входящее сообщение от пользователя
     */
    suspend operator fun invoke(message: Message) {
        println("📨 HandleMessageUseCase: message.location = ${message.location}")
        println("📨 HandleMessageUseCase: message.text = ${message.text}")

        // Проверяем, есть ли геолокация
        if (message.location != null) {
            println("🗺 Location message detected, handling...")
            handleLocationMessage(message.chatId, message.location!!)
            return
        }

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
            Scenario.RAG_INTERACTIVE -> handleRagInteractiveScenario(message.chatId, userText, session.selectedModel!!)
            else -> handleAiMessage(message.chatId, userText, session.selectedModel!!, session.currentScenario)
        }
    }

    /**
     * Обрабатывает location message.
     * Получает прогноз погоды через MCP Repository и отправляет пользователю.
     *
     * @param chatId ID чата пользователя
     * @param location Геолокация пользователя
     */
    private suspend fun handleLocationMessage(chatId: Long, location: com.example.tgbot.domain.model.Location) {
        try {
            println("📍 Location received: lat=${location.latitude}, lon=${location.longitude}")
            telegramRepository.sendMessage(chatId, "Fetching weather forecast...")

            println("🌤 Calling MCP Weather WebSocket...")
            val forecastText = mcpRepository.getForecast(location.latitude, location.longitude)
            println("✅ Got forecast from MCP server")
            println("📤 Sending formatted forecast to user")

            telegramRepository.sendMessage(chatId, forecastText)
        } catch (e: Exception) {
            println("❌ Error getting weather forecast: ${e.message}")
            e.printStackTrace()
            telegramRepository.sendMessage(
                chatId,
                "Error getting weather forecast: ${e.message}\n\nStack trace: ${e.stackTraceToString().take(500)}"
            )
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
                if (historySize >= 6) {
                    println("SHOULD COMPRESSION")
                    // Выполняем компрессию истории
                    val summary = historyCompressor.compressHistory(
                        history = session.conversationHistory,
                        model = model,
                        temperature = session.temperature,
                        huggingFaceModel = session.selectedHuggingFaceModel
                    )

                    // Сохраняем результат суммаризации в БД
                    try {
                        val summaryId = summaryRepository.saveSummary(summary.content)
                        println("✓ Суммаризация сохранена в БД (ID: $summaryId)")
                    } catch (e: Exception) {
                        println("⚠️ Ошибка сохранения суммаризации в БД: ${e.message}")
                    }

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
            Scenario.RAG_INTERACTIVE -> null // Этот сценарий обрабатывается отдельно
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

    /**
     * Обрабатывает сообщения в сценарии RAG_INTERACTIVE.
     *
     * Логика:
     * 1. Выполняет RAG-поиск (topK=9, threshold=0.7)
     * 2. Определяет количество попыток на основе результатов
     * 3. Отправляет топ-3 чанка в LLM
     * 4. Показывает inline кнопки для продолжения поиска
     * 5. Сохраняет состояние в UserSession
     *
     * @param chatId ID чата пользователя
     * @param query Поисковый запрос
     * @param selectedModel Выбранная AI-модель
     */
    private suspend fun handleRagInteractiveScenario(
        chatId: Long,
        query: String,
        selectedModel: AiModel
    ) {
        try {
            println("🔍 handleRagInteractiveScenario started: query='$query', model=$selectedModel")

            // 1. RAG-поиск
            telegramRepository.sendMessage(chatId, "🔍 Ищу релевантную информацию...")
            val ragResults = ragRepository.searchSimilar(query, topK = 9)

            println("📊 RAG search completed: found ${ragResults.size} results")

            // 2. Edge case: нет результатов
            if (ragResults.isEmpty()) {
                telegramRepository.sendMessage(
                    chatId,
                    "😕 Релевантная информация не найдена.\n\n" +
                    "Попробуйте:\n" +
                    "• Переформулировать вопрос\n" +
                    "• Проверить индекс: /rag_stats\n" +
                    "• Индексировать документы через CLI"
                )
                return
            }

            // 3. Edge case: слишком мало результатов (1-2)
            if (ragResults.size < 3) {
                telegramRepository.sendMessage(
                    chatId,
                    "⚠️ Найдено только ${ragResults.size} релевантных фрагментов.\n\n" +
                    "Рекомендую использовать команду /ask для простого запроса."
                )
                return
            }

            // 4. Определяем максимальное количество попыток (округление вверх: ceil(size / 3))
            // Примеры: 3→1, 4→2, 5→2, 6→2, 7→3, 8→3, 9→3
            val maxAttempts = (ragResults.size + 2) / 3

            // 5. Создаем и сохраняем состояние
            val state = RagInteractiveState(
                query = query,
                allResults = ragResults,
                currentAttempt = 0,
                maxAttempts = maxAttempts
            )
            SessionManager.setRagInteractiveState(chatId, state)

            println("💾 State saved: maxAttempts=$maxAttempts, resultsCount=${ragResults.size}")

            // 6. Отправляем первый LLM запрос
            println("🚀 Calling sendRagLlmRequest...")
            sendRagLlmRequest(chatId, state, selectedModel)
            println("✅ sendRagLlmRequest completed")

        } catch (e: Exception) {
            println("❌ Exception in handleRagInteractiveScenario: ${e.message}")
            e.printStackTrace()
            telegramRepository.sendMessage(
                chatId,
                "❌ Ошибка при обработке запроса:\n${e.message}\n\n" +
                "Возможные причины:\n" +
                "• Ollama не запущен (проверьте http://localhost:11434)\n" +
                "• Проблемы с AI API\n" +
                "• База данных RAG не инициализирована"
            )
            SessionManager.setRagInteractiveState(chatId, null)
        }
    }

    /**
     * Отправляет LLM запрос с текущими чанками из RAG-поиска.
     * Показывает inline кнопки для продолжения поиска (если доступно).
     *
     * @param chatId ID чата пользователя
     * @param state Состояние RAG-поиска
     * @param selectedModel Выбранная AI-модель
     */
    private suspend fun sendRagLlmRequest(
        chatId: Long,
        state: RagInteractiveState,
        selectedModel: AiModel
    ) {
        try {
            println("📤 sendRagLlmRequest: attempt=${state.currentAttempt}, maxAttempts=${state.maxAttempts}")

            val session = SessionManager.getSession(chatId)
            val currentChunks = state.getCurrentChunks()
            println("📝 Current chunks count: ${currentChunks.size}")

            if (currentChunks.isEmpty()) {
                println("❌ ERROR: currentChunks is empty!")
                telegramRepository.sendMessage(chatId, "❌ Ошибка: нет чанков для обработки")
                SessionManager.setRagInteractiveState(chatId, null)
                return
            }

            val (maxSim, minSim) = state.getCurrentSimilarityRange()
            println("📊 Similarity range: $maxSim - $minSim")

        // 1. Сборка контекста из чанков
        val contextText = buildString {
            appendLine("Релевантная информация из документов:")
            appendLine()
            currentChunks.forEachIndexed { index, result ->
                appendLine("【Источник ${index + 1}】")
                appendLine("Документ: ${result.documentPath.substringAfterLast("/")}")
                appendLine("Релевантность: ${"%.1f".format(result.similarity * 100)}%")
                appendLine()
                appendLine(result.content)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }

        // 2. Системный промпт
        val systemPrompt = """
Вы - ассистент, который отвечает на вопросы на основе предоставленного контекста.

ВАЖНО:
1. Используйте ТОЛЬКО информацию из предоставленных источников
2. Если ответ не найден в источниках, скажите об этом явно
3. Цитируйте источники при формулировании ответа (например, "Согласно Источнику 2...")
4. Не придумывайте информацию, которой нет в источниках
5. Если источники содержат противоречивую информацию, укажите на это

Отвечайте кратко и по существу.
""".trimIndent()

        // 3. Формирование сообщений для LLM (stateless - без conversationHistory)
        val messages = listOf(
            AiMessage(role = MessageRole.SYSTEM, content = systemPrompt),
            AiMessage(
                role = MessageRole.USER,
                content = buildString {
                    appendLine(contextText)
                    appendLine()
                    appendLine("Вопрос: ${state.query}")
                }
            )
        )

        // 4. Отправка запроса к LLM
        println("🤖 Sending request to AI: model=$selectedModel, temp=${session.temperature}")
        telegramRepository.sendMessage(chatId, "🤖 Генерирую ответ на основе найденной информации...")

        val aiResponse = try {
            aiRepository.sendMessage(
                AiRequest(
                    model = selectedModel,
                    messages = messages,
                    temperature = session.temperature,
                    huggingFaceModel = if (selectedModel == AiModel.HUGGING_FACE) {
                        session.selectedHuggingFaceModel
                    } else null
                )
            )
        } catch (e: Exception) {
            println("❌ AI request failed: ${e.message}")
            e.printStackTrace()
            throw e
        }

        println("✅ AI response received: ${aiResponse.content.take(100)}...")

        // 5. Формирование ответа пользователю
        val chunkRange = "${state.currentAttempt * 3 + 1}-${state.currentAttempt * 3 + currentChunks.size}"
        val responseText = buildString {
            append("💡 Ответ:\n\n")
            append(aiResponse.content)
            append("\n\n━━━━━━━━━━━━━━━━━━━━\n")
            append("📊 Использованные чанки: $chunkRange из ${state.allResults.size}\n")
            append("📈 Similarity: ${"%.2f".format(maxSim)}-${"%.2f".format(minSim)}\n")
            append("\n📚 Источники (${currentChunks.size}):\n\n")

            currentChunks.forEachIndexed { index, result ->
                append("${index + 1}. ${result.documentPath.substringAfterLast("/")}\n")
                append("   Фрагмент #${result.chunkIndex + 1} ")
                append("(релевантность: ${"%.1f".format(result.similarity * 100)}%)\n")
            }

            append("\n━━━━━━━━━━━━━━━━━━━━\n")
            append("📊 Статистика:\n")
            aiResponse.responseTimeMillis?.let { append("⏱️  Время: ${it} мс\n") }
            aiResponse.tokenUsage?.let { usage ->
                append("🔢 Токены: ${usage.promptTokens} + ${usage.completionTokens} = ${usage.totalTokens}\n")
            }

            val modelName = if (selectedModel == AiModel.HUGGING_FACE) {
                session.selectedHuggingFaceModel?.displayName ?: selectedModel.displayName
            } else selectedModel.displayName
            append("🤖 Модель: $modelName (temp: ${session.temperature})")
        }

            // 6. Отправка ответа с inline кнопками (если доступна следующая попытка)
            if (state.hasNextAttempt()) {
                println("🔘 Sending response with buttons (remaining attempts: ${state.maxAttempts - state.currentAttempt - 1})")
                val remainingAttempts = state.maxAttempts - state.currentAttempt - 1
                val keyboard = InlineKeyboard(
                    rows = listOf(
                        listOf(
                            InlineKeyboardButton(
                                text = "🔄 Ещё ($remainingAttempts)",
                                callbackData = "rag_interactive:next"
                            ),
                            InlineKeyboardButton(
                                text = "✅ Достаточно",
                                callbackData = "rag_interactive:done"
                            )
                        )
                    )
                )

                println("📞 Calling telegramRepository.sendMessageWithKeyboard...")
                telegramRepository.sendMessageWithKeyboard(chatId, responseText, keyboard)
                println("✉️ Response with keyboard sent successfully")
            } else {
                println("📨 Sending final response without buttons")
                println("📞 Calling telegramRepository.sendMessage...")
                // Последняя попытка - кнопки не показываем
                telegramRepository.sendMessage(chatId, responseText)
                SessionManager.setRagInteractiveState(chatId, null)  // Очистка состояния
                println("✉️ Final response sent successfully, state cleared")
            }
        } catch (e: Exception) {
            println("❌ EXCEPTION in sendRagLlmRequest: ${e.message}")
            e.printStackTrace()
            try {
                telegramRepository.sendMessage(chatId, "❌ Критическая ошибка в sendRagLlmRequest: ${e.message}")
            } catch (sendError: Exception) {
                println("❌ FATAL: Could not send error message: ${sendError.message}")
                sendError.printStackTrace()
            }
            SessionManager.setRagInteractiveState(chatId, null)
            throw e
        }
    }
}
