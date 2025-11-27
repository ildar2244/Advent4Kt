package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.CallbackQuery
import com.example.tgbot.domain.model.InlineKeyboard
import com.example.tgbot.domain.model.InlineKeyboardButton
import com.example.tgbot.domain.model.RagInteractiveState
import com.example.tgbot.domain.model.RagSearchResult
import com.example.tgbot.domain.model.Scenario
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.repository.AiRepository
import com.example.tgbot.domain.repository.McpRepository
import com.example.tgbot.domain.repository.RagRepository
import com.example.tgbot.domain.repository.TelegramRepository

/**
 * Use case для обработки callback-запросов от инлайн-кнопок.
 *
 * Обрабатываемые типы callback'ов:
 * - "show_models" - Показать список AI-провайдеров (из команды /start)
 * - "model_gpt", "model_claude", "model_yandex" - Выбор конкретной AI-модели
 * - "model_huggingface" - Показать список моделей HuggingFace
 * - "hf_model:*" - Выбор конкретной модели HuggingFace
 * - "scenario_*" - Выбор сценария взаимодействия (динамически из Scenario enum)
 * - "set_temp:*" - Установка значения temperature
 * - "mcp_weather_tools" - Показать список доступных MCP инструментов
 * - "mcp_weather_location" - Запросить геолокацию для прогноза погоды
 *
 * При выборе модели:
 * - Сохраняется выбранная модель в сессию пользователя
 * - Сценарий автоматически сбрасывается на FREE_CHAT
 * - Добавляется системное приветственное сообщение в историю диалога
 *
 * При выборе сценария:
 * - Устанавливается выбранный сценарий для пользователя
 * - Отправляется подтверждение активации сценария
 */
class HandleCallbackUseCase(
    private val repository: TelegramRepository,
    private val mcpRepository: McpRepository,
    private val ragRepository: RagRepository,
    private val aiRepository: AiRepository
) {
    /**
     * Обрабатывает нажатие пользователем на инлайн-кнопку.
     * При выборе AI-модели: сохраняет модель в сессию, сбрасывает сценарий, скрывает кнопки и отправляет приветствие.
     * При выборе сценария: устанавливает выбранный сценарий и отправляет подтверждение.
     *
     * @param callback Callback-запрос от нажатой кнопки
     */
    suspend operator fun invoke(callback: CallbackQuery) {
        val data = callback.data ?: return
        val message = callback.message ?: return

        // Проверяем callback для просмотра источников /ask
        if (data.startsWith("ask_source:")) {
            handleAskSourceCallback(callback, data, message.chatId)
            return
        }

        // Проверяем RAG Interactive callbacks
        if (data.startsWith("rag_interactive:")) {
            handleRagInteractiveCallback(callback, data, message.chatId, message.messageId)
            return
        }

        // Проверяем, является ли callback MCP команд ой
        if (data == "mcp_weather_tools") {
            handleMcpWeatherToolsCallback(callback, message.chatId)
            return
        }

        if (data == "mcp_weather_location") {
            handleMcpWeatherLocationCallback(callback, message.chatId)
            return
        }

        // Проверяем, является ли callback нажатием на кнопку "Модели"
        if (data == "show_models") {
            handleShowModelsCallback(callback, message.chatId, message.messageId)
            return
        }

        // Проверяем, является ли callback выбором сценария
        if (data.startsWith("scenario_")) {
            handleScenarioCallback(callback, data, message.chatId, message.messageId)
            return
        }

        // Проверяем, является ли callback изменением temperature
        if (data.startsWith("set_temp:")) {
            handleTemperatureCallback(callback, data, message.chatId, message.messageId)
            return
        }

        // Проверяем, является ли callback выбором провайдера HuggingFace
        if (data == "model_huggingface") {
            handleHuggingFaceProviderCallback(callback, message.chatId, message.messageId)
            return
        }

        // Проверяем, является ли callback выбором конкретной модели HuggingFace
        if (data.startsWith("hf_model:")) {
            handleHuggingFaceModelCallback(callback, data, message.chatId, message.messageId)
            return
        }

        // Определяем выбранную модель на основе callback_data
        val selectedModel = when (data) {
            "model_gpt" -> AiModel.GPT_4O_MINI
            "model_claude" -> AiModel.CLAUDE_HAIKU
            "model_yandex" -> AiModel.YANDEX_GPT_LITE
            else -> return
        }

        // Сохраняем выбранную модель в сессии пользователя
        SessionManager.setModel(message.chatId, selectedModel)

        // Сбрасываем сценарий на "Просто чат" при выборе модели
        SessionManager.setScenario(message.chatId, Scenario.DEFAULT)

        // Получаем текущее значение temperature из сессии
        val updatedSession = SessionManager.getSession(message.chatId)
        val currentTemperature = updatedSession.temperature

        // Редактируем сообщение: убираем кнопки и меняем текст на подтверждение выбора
        repository.editMessageText(
            chatId = message.chatId,
            messageId = message.messageId,
            text = "✓ Выбрана модель: ${selectedModel.displayName}\ntemperature: $currentTemperature (/temperature)"
        )

        // Отправляем приветственное сообщение с инструкциями
        repository.sendMessage(
            chatId = message.chatId,
            text = "Я готов ответить на ваши вопросы с помощью ${selectedModel.displayName}.\n\n" +
                    "Напишите ваше сообщение, и я отвечу.\n\n" +
                    "Используйте /temperature для изменения параметра генерации.\n" +
                    "Используйте /stop для выхода из режима AI-консультации."
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }

    /**
     * Обрабатывает выбор сценария через callback.
     *
     * @param callback Callback-запрос
     * @param data Callback данные
     * @param chatId ID чата
     * @param messageId ID сообщения с кнопками
     */
    private suspend fun handleScenarioCallback(
        callback: CallbackQuery,
        data: String,
        chatId: Long,
        messageId: Long
    ) {
        // Находим сценарий по callback данным
        val scenario = Scenario.findByCallbackData(data) ?: return

        // Устанавливаем выбранный сценарий
        SessionManager.setScenario(chatId, scenario)

        // Редактируем сообщение: убираем кнопки и меняем текст на подтверждение выбора
        repository.editMessageText(
            chatId = chatId,
            messageId = messageId,
            text = "✓ Выбран сценарий: ${scenario.displayName}"
        )

        // Отправляем дополнительное сообщение с подтверждением
        repository.sendMessage(
            chatId = chatId,
            text = "Сценарий \"${scenario.displayName}\" активирован.\n\nТеперь все ваши сообщения будут обрабатываться в этом режиме."
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }

    /**
     * Обрабатывает нажатие на кнопку "Модели" из команды /start.
     * Редактирует сообщение и показывает список доступных AI-моделей.
     *
     * @param callback Callback-запрос
     * @param chatId ID чата
     * @param messageId ID сообщения с кнопкой
     */
    private suspend fun handleShowModelsCallback(
        callback: CallbackQuery,
        chatId: Long,
        messageId: Long
    ) {
        // Создаем клавиатуру с кнопками AI-провайдеров
        val keyboard = InlineKeyboard(
            rows = listOf(
                listOf(
                    InlineKeyboardButton(
                        text = AiModel.GPT_4O_MINI.displayName,
                        callbackData = "model_gpt"
                    ),
                    InlineKeyboardButton(
                        text = AiModel.CLAUDE_HAIKU.displayName,
                        callbackData = "model_claude"
                    )
                ),
                listOf(
                    InlineKeyboardButton(
                        text = AiModel.YANDEX_GPT_LITE.displayName,
                        callbackData = "model_yandex"
                    ),
                    InlineKeyboardButton(
                        text = AiModel.HUGGING_FACE.displayName,
                        callbackData = "model_huggingface"
                    )
                )
            )
        )

        // Редактируем сообщение: меняем текст и кнопки
        repository.editMessageText(
            chatId = chatId,
            messageId = messageId,
            text = "Выберите AI-модель для диалога:"
        )

        // Отправляем новое сообщение с клавиатурой моделей
        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "Выберите AI-модель для диалога:",
            keyboard = keyboard
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }

    /**
     * Обрабатывает выбор значения temperature через callback.
     *
     * @param callback Callback-запрос
     * @param data Callback данные в формате "set_temp:0.0"
     * @param chatId ID чата
     * @param messageId ID сообщения с кнопками
     */
    private suspend fun handleTemperatureCallback(
        callback: CallbackQuery,
        data: String,
        chatId: Long,
        messageId: Long
    ) {
        // Извлекаем значение temperature из callback данных
        val temperatureValue = data.removePrefix("set_temp:").toDoubleOrNull() ?: return

        // Устанавливаем новое значение temperature
        SessionManager.setTemperature(chatId, temperatureValue)

        // Получаем информацию о выбранной модели
        val session = SessionManager.getSession(chatId)
        val modelName = session.selectedModel?.displayName ?: "не выбрана"

        // Редактируем сообщение: убираем кнопки и обновляем текст
        repository.editMessageText(
            chatId = chatId,
            messageId = messageId,
            text = "✓ Выбрана модель: $modelName\ntemperature: $temperatureValue (/temperature)"
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }

    /**
     * Обрабатывает выбор провайдера HuggingFace.
     * Показывает список доступных моделей HuggingFace для выбора.
     *
     * @param callback Callback-запрос
     * @param chatId ID чата
     * @param messageId ID сообщения с кнопками
     */
    private suspend fun handleHuggingFaceProviderCallback(
        callback: CallbackQuery,
        chatId: Long,
        messageId: Long
    ) {
        // Импортируем HuggingFaceModel
        val hfModels = com.example.tgbot.domain.model.ai.HuggingFaceModel.values()

        // Создаем кнопки для каждой модели HuggingFace
        val buttons = hfModels.map { model ->
            InlineKeyboardButton(
                text = model.displayName,
                callbackData = "hf_model:${model.modelId}"
            )
        }

        // Размещаем по 1 кнопке в ряд для лучшей читаемости
        val rows = buttons.map { listOf(it) }

        val keyboard = InlineKeyboard(rows = rows)

        // Редактируем сообщение: меняем текст и показываем модели HuggingFace
        repository.editMessageText(
            chatId = chatId,
            messageId = messageId,
            text = "Выберите модель HuggingFace:"
        )

        // Отправляем новое сообщение с клавиатурой моделей
        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "Выберите модель HuggingFace:",
            keyboard = keyboard
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }

    /**
     * Обрабатывает выбор конкретной модели HuggingFace.
     * Устанавливает выбранную модель HF в сессию и выбирает HUGGING_FACE как провайдера.
     *
     * @param callback Callback-запрос
     * @param data Callback данные в формате "hf_model:{modelId}"
     * @param chatId ID чата
     * @param messageId ID сообщения с кнопками
     */
    private suspend fun handleHuggingFaceModelCallback(
        callback: CallbackQuery,
        data: String,
        chatId: Long,
        messageId: Long
    ) {
        // Извлекаем modelId из callback данных
        val modelId = data.removePrefix("hf_model:")

        // Находим модель по modelId
        val hfModel = com.example.tgbot.domain.model.ai.HuggingFaceModel.findByModelId(modelId)

        if (hfModel == null) {
            // Если модель не найдена, отвечаем на callback и выходим
            repository.answerCallbackQuery(callback.id)
            return
        }

        // Устанавливаем провайдера HUGGING_FACE
        SessionManager.setModel(chatId, AiModel.HUGGING_FACE)

        // Устанавливаем конкретную модель HuggingFace
        SessionManager.setHuggingFaceModel(chatId, hfModel)

        // Сбрасываем сценарий на "Просто чат" при выборе модели
        SessionManager.setScenario(chatId, Scenario.DEFAULT)

        // Получаем текущее значение temperature из сессии
        val updatedSession = SessionManager.getSession(chatId)
        val currentTemperature = updatedSession.temperature

        // Редактируем сообщение: убираем кнопки и меняем текст на подтверждение выбора
        repository.editMessageText(
            chatId = chatId,
            messageId = messageId,
            text = "✓ Выбрана модель: ${AiModel.HUGGING_FACE.displayName} - ${hfModel.displayName}\ntemperature: $currentTemperature (/temperature)"
        )

        // Отправляем приветственное сообщение с инструкциями
        repository.sendMessage(
            chatId = chatId,
            text = "Я готов ответить на ваши вопросы с помощью ${hfModel.displayName} (HuggingFace).\n\n" +
                    "Напишите ваше сообщение, и я отвечу.\n\n" +
                    "⚠️ Первый запрос может занять до 30 секунд (модель загружается).\n\n" +
                    "Используйте /hf_models для смены модели HuggingFace.\n" +
                    "Используйте /temperature для изменения параметра генерации.\n" +
                    "Используйте /stop для выхода из режима AI-консультации."
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }

    /**
     * Обрабатывает callback "mcp_weather_tools".
     * Показывает список доступных MCP инструментов.
     *
     * @param callback Callback-запрос
     * @param chatId ID чата
     */
    private suspend fun handleMcpWeatherToolsCallback(
        callback: CallbackQuery,
        chatId: Long
    ) {
        val tools = mcpRepository.getAvailableTools()

        val responseText = buildString {
            appendLine("Available MCP Tools:")
            appendLine()
            tools.forEach { (name, description) ->
                appendLine("🔧 $name")
                appendLine("   $description")
                appendLine()
            }
        }.trim()

        repository.sendMessage(
            chatId = chatId,
            text = responseText
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }

    /**
     * Обрабатывает callback "mcp_weather_location".
     * Запрашивает у пользователя отправку геолокации.
     *
     * @param callback Callback-запрос
     * @param chatId ID чата
     */
    private suspend fun handleMcpWeatherLocationCallback(
        callback: CallbackQuery,
        chatId: Long
    ) {
        repository.sendMessage(
            chatId = chatId,
            text = "📍 Please send your location to get weather forecast.\n\nUse the 📎 (attach) button and select Location."
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }

    /**
     * Обрабатывает callback от inline кнопок в сценарии RAG_INTERACTIVE.
     *
     * Поддерживаемые действия:
     * - "rag_interactive:next" - следующие 3 чанка
     * - "rag_interactive:done" - завершить поиск
     *
     * @param callback Callback-запрос
     * @param data Callback данные
     * @param chatId ID чата
     * @param messageId ID сообщения с кнопками
     */
    private suspend fun handleRagInteractiveCallback(
        callback: CallbackQuery,
        data: String,
        chatId: Long,
        messageId: Long
    ) {
        val action = data.removePrefix("rag_interactive:")

        when (action) {
            "next" -> handleRagInteractiveNext(chatId, messageId)
            "done" -> handleRagInteractiveDone(chatId, messageId)
        }

        // Отвечаем на callback (убирает "часики" на кнопке)
        repository.answerCallbackQuery(callback.id)
    }

    /**
     * Обрабатывает action "next" - переход к следующим 3 чанкам.
     *
     * @param chatId ID чата
     * @param messageId ID сообщения с кнопками
     */
    private suspend fun handleRagInteractiveNext(chatId: Long, messageId: Long) {
        println("🔄 handleRagInteractiveNext called")

        val session = SessionManager.getSession(chatId)
        val state = session.ragInteractiveState

        if (state == null) {
            println("❌ State is null")
            repository.editMessageText(
                chatId = chatId,
                messageId = messageId,
                text = "❌ Состояние RAG-поиска утеряно. Отправьте новый запрос."
            )
            return
        }

        // Проверяем, что есть следующая попытка
        if (!state.hasNextAttempt()) {
            println("❌ No more attempts available")
            repository.editMessageText(
                chatId = chatId,
                messageId = messageId,
                text = "❌ Больше нет доступных чанков."
            )
            SessionManager.setRagInteractiveState(chatId, null)
            return
        }

        // Обновляем состояние (increment attempt)
        val updatedState = state.copy(currentAttempt = state.currentAttempt + 1)
        SessionManager.setRagInteractiveState(chatId, updatedState)

        println("⬆️ State updated: attempt ${state.currentAttempt} -> ${updatedState.currentAttempt}")

        // Отправляем новый LLM запрос с следующими чанками (в отдельном сообщении)
        println("🚀 Calling sendRagLlmRequest from callback...")
        try {
            sendRagLlmRequest(chatId, updatedState, session.selectedModel!!)
            println("✅ sendRagLlmRequest from callback completed")
        } catch (e: Exception) {
            println("❌ Exception in sendRagLlmRequest from callback: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Обрабатывает action "done" - завершение RAG-поиска.
     *
     * @param chatId ID чата
     * @param messageId ID сообщения с кнопками
     */
    private suspend fun handleRagInteractiveDone(chatId: Long, messageId: Long) {
        // Очищаем состояние
        SessionManager.setRagInteractiveState(chatId, null)

        // Удаляем кнопки и обновляем сообщение
        repository.editMessageText(
            chatId = chatId,
            messageId = messageId,
            text = "✅ RAG-поиск завершен.\n\nОтправьте новый запрос для следующего поиска."
        )
    }

    /**
     * Отправляет LLM запрос с текущими чанками из RAG-поиска.
     * Показывает inline кнопки для продолжения поиска (если доступно).
     *
     * Метод дублируется из HandleMessageUseCase для обработки callback'ов.
     * TODO: Рефакторинг - вынести в отдельный use case для переиспользования.
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
            println("📤 [Callback] sendRagLlmRequest: attempt=${state.currentAttempt}, maxAttempts=${state.maxAttempts}")

            val session = SessionManager.getSession(chatId)
            val currentChunks = state.getCurrentChunks()
            println("📝 [Callback] Current chunks count: ${currentChunks.size}")

            if (currentChunks.isEmpty()) {
                println("❌ [Callback] ERROR: currentChunks is empty!")
                repository.sendMessage(chatId, "❌ Ошибка: нет чанков для обработки")
                SessionManager.setRagInteractiveState(chatId, null)
                return
            }

            val (maxSim, minSim) = state.getCurrentSimilarityRange()
            println("📊 [Callback] Similarity range: $maxSim - $minSim")

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
        println("🤖 [Callback] Sending request to AI: model=$selectedModel, temp=${session.temperature}")
        repository.sendMessage(chatId, "🤖 Генерирую ответ на основе найденной информации...")

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
            println("❌ [Callback] AI request failed: ${e.message}")
            e.printStackTrace()
            throw e
        }

        println("✅ [Callback] AI response received: ${aiResponse.content.take(100)}...")

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
                println("🔘 [Callback] Sending response with buttons (remaining attempts: ${state.maxAttempts - state.currentAttempt - 1})")
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

                println("📞 [Callback] Calling repository.sendMessageWithKeyboard...")
                repository.sendMessageWithKeyboard(chatId, responseText, keyboard)
                println("✉️ [Callback] Response with keyboard sent successfully")
            } else {
                println("📨 [Callback] Sending final response without buttons")
                println("📞 [Callback] Calling repository.sendMessage...")
                // Последняя попытка - кнопки не показываем
                repository.sendMessage(chatId, responseText)
                SessionManager.setRagInteractiveState(chatId, null)  // Очистка состояния
                println("✉️ [Callback] Final response sent successfully, state cleared")
            }
        } catch (e: Exception) {
            println("❌ [Callback] EXCEPTION in sendRagLlmRequest: ${e.message}")
            e.printStackTrace()
            try {
                repository.sendMessage(chatId, "❌ Критическая ошибка в sendRagLlmRequest (callback): ${e.message}")
            } catch (sendError: Exception) {
                println("❌ [Callback] FATAL: Could not send error message: ${sendError.message}")
                sendError.printStackTrace()
            }
            SessionManager.setRagInteractiveState(chatId, null)
            throw e
        }
    }

    /**
     * Обрабатывает callback для просмотра содержимого источника из команды /ask.
     * Показывает popup с preview фрагмента (150 символов).
     *
     * @param callback Callback-запрос
     * @param data Callback данные в формате "ask_source:{documentId}:{chunkIndex}"
     * @param chatId ID чата
     */
    private suspend fun handleAskSourceCallback(
        callback: CallbackQuery,
        data: String,
        chatId: Long
    ) {
        try {
            // 1. Парсинг callback_data
            val parts = data.removePrefix("ask_source:").split(":")
            if (parts.size != 2) {
                repository.answerCallbackQuery(
                    callback.id,
                    "❌ Неверный формат данных",
                    showAlert = true
                )
                return
            }

            val documentId = parts[0].toIntOrNull()
            val chunkIndex = parts[1].toIntOrNull()

            if (documentId == null || chunkIndex == null) {
                repository.answerCallbackQuery(
                    callback.id,
                    "❌ Неверные параметры",
                    showAlert = true
                )
                return
            }

            // 2. Получение chunk из БД
            val result = ragRepository.getChunkByDocumentAndIndex(documentId, chunkIndex)

            if (result == null) {
                repository.answerCallbackQuery(
                    callback.id,
                    "❌ Фрагмент не найден в базе данных.\nВозможно, документ был удален.",
                    showAlert = true
                )
                return
            }

            // 3. Форматирование preview
            val previewText = formatSourcePreview(result)

            // 4. Отправка popup
            repository.answerCallbackQuery(
                callbackQueryId = callback.id,
                text = previewText,
                showAlert = true
            )

        } catch (e: Exception) {
            println("❌ Error in handleAskSourceCallback: ${e.message}")
            e.printStackTrace()
            repository.answerCallbackQuery(
                callback.id,
                "❌ Ошибка при загрузке фрагмента: ${e.message}",
                showAlert = true
            )
        }
    }

    /**
     * Форматирует preview содержимого фрагмента для popup.
     * Возвращает только текст цитаты (до 200 символов).
     *
     * @param result Результат поиска с данными фрагмента
     * @return Текст цитаты для popup
     */
    private fun formatSourcePreview(result: RagSearchResult): String {
        // Telegram popup строгий лимит: 200 символов (включая "...")
        // Оставляем только текст цитаты без метаданных
        val maxContentLength = 197  // 197 + "..." = 200

        return if (result.content.length > maxContentLength) {
            result.content.take(maxContentLength).trim() + "..."
        } else {
            result.content
        }
    }
}
