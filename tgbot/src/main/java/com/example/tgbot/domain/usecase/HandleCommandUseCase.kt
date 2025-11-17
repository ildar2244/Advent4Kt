package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.InlineKeyboard
import com.example.tgbot.domain.model.InlineKeyboardButton
import com.example.tgbot.domain.model.Message
import com.example.tgbot.domain.model.Scenario
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.repository.McpRepository
import com.example.tgbot.domain.repository.SummaryRepository
import com.example.tgbot.domain.repository.TelegramRepository
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Use case для обработки команд бота (сообщения, начинающиеся с /).
 *
 * Поддерживаемые команды:
 * - /start - Приветственное сообщение с кнопкой выбора модели
 * - /models - Выбор AI-провайдера (GPT-4o Mini, Claude 3.5 Haiku, YandexGPT Lite, HuggingFace)
 * - /hf_models - Выбор конкретной модели HuggingFace (DialoGPT, Bloomz, Mistral, Llama, Phi-3)
 * - /temperature - Настройка параметра temperature для генерации ответов AI
 * - /scenario - Выбор сценария взаимодействия с AI
 * - /free-chat, /json-format, /consultant, /step-by-step, /experts - Прямая активация сценариев
 * - /stop - Завершение AI-консультации и очистка сессии
 * - /db_stats - Статистика базы данных суммаризаций
 * - /db_history - Последние 3 записи из БД
 * - /db_clear - Очистка всех записей из БД
 * - /mcp - MCP команды (Weather Tools, Weather Location)
 * - /weather_tools - Список доступных MCP инструментов
 * - /weather_location - Запрос геолокации для прогноза погоды
 *
 * Команды сценариев обрабатываются динамически через Scenario.findByCommand(),
 * что позволяет легко добавлять новые сценарии без изменения логики обработки.
 */
class HandleCommandUseCase(
    private val repository: TelegramRepository,
    private val summaryRepository: SummaryRepository,
    private val mcpRepository: McpRepository
) {
    /**
     * Обрабатывает команду из сообщения и вызывает соответствующий обработчик.
     *
     * @param message Сообщение с командой
     */
    suspend operator fun invoke(message: Message) {
        val command = message.text?.trim() ?: return

        when {
            command == "/start" -> handleStartCommand(message.chatId)
            command == "/models" -> handleModelsCommand(message.chatId)
            command == "/hf_models" -> handleHuggingFaceModelsCommand(message.chatId)
            command == "/temperature" -> handleTemperatureCommand(message.chatId)
            command == "/scenario" -> handleScenarioCommand(message.chatId)
            command == "/stop" -> handleStopCommand(message.chatId)
            command == "/db_stats" -> handleDbStatsCommand(message.chatId)
            command == "/db_history" -> handleDbHistoryCommand(message.chatId)
            command == "/db_clear" -> handleDbClearCommand(message.chatId)
            command == "/mcp" -> handleMcpCommand(message.chatId)
            command == "/weather_tools" -> handleWeatherToolsCommand(message.chatId)
            command == "/weather_location" -> handleWeatherLocationCommand(message.chatId)
            else -> {
                // Проверяем, является ли команда командой сценария
                val scenario = Scenario.findByCommand(command)
                if (scenario != null) {
                    handleScenarioSelection(message.chatId, scenario)
                }
                // Игнорируем неизвестные команды
            }
        }
    }

    /**
     * Обрабатывает команду /start.
     * Отправляет приветственное сообщение с кнопкой для выбора модели.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleStartCommand(chatId: Long) {
        val keyboard = InlineKeyboard(
            rows = listOf(
                listOf(
                    InlineKeyboardButton(
                        text = "Модели",
                        callbackData = "show_models"
                    )
                )
            )
        )

        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "Выберите модель ИИ-агента для ваших запросов. Список агентов доступен по кнопке \"Модели\".",
            keyboard = keyboard
        )
    }

    /**
     * Обрабатывает команду /models.
     * Отправляет сообщение с инлайн-кнопками для выбора AI-модели.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleModelsCommand(chatId: Long) {
        // Создаем клавиатуру с кнопками AI-моделей
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

        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "Выберите AI-провайдера для диалога:",
            keyboard = keyboard
        )
    }

    /**
     * Обрабатывает команду /hf_models.
     * Отправляет сообщение с инлайн-кнопками для выбора конкретной модели HuggingFace.
     * Кнопки генерируются динамически на основе HuggingFaceModel enum.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleHuggingFaceModelsCommand(chatId: Long) {
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

        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "Выберите модель HuggingFace:",
            keyboard = keyboard
        )
    }

    /**
     * Обрабатывает команду /temperature.
     * Отправляет сообщение с инлайн-кнопками для выбора значения temperature.
     * Доступные значения: 0.0, 0.6, 1.0
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleTemperatureCommand(chatId: Long) {
        // Получаем текущее значение temperature
        val session = SessionManager.getSession(chatId)
        val currentTemperature = session.temperature
        val modelName = session.selectedModel?.displayName ?: "не выбрана"

        // Создаем клавиатуру с тремя кнопками temperature
        val keyboard = InlineKeyboard(
            rows = listOf(
                listOf(
                    InlineKeyboardButton(
                        text = "0.0",
                        callbackData = "set_temp:0.0"
                    ),
                    InlineKeyboardButton(
                        text = "0.6",
                        callbackData = "set_temp:0.6"
                    ),
                    InlineKeyboardButton(
                        text = "1.0",
                        callbackData = "set_temp:1.0"
                    )
                )
            )
        )

        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "✓ Выбрана модель: $modelName\ntemperature: $currentTemperature (/temperature)\n\nВыберите новое значение temperature:",
            keyboard = keyboard
        )
    }

    /**
     * Обрабатывает команду /stop.
     * Завершает режим AI-консультации и очищает сессию пользователя.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleStopCommand(chatId: Long) {
        SessionManager.clearSession(chatId)

        repository.sendMessage(
            chatId = chatId,
            text = "Режим AI-консультации завершен. Вы вернулись в обычный режим.\n\nИспользуйте /models для выбора новой модели."
        )
    }

    /**
     * Обрабатывает команду /scenario.
     * Отправляет сообщение с инлайн-кнопками для выбора сценария взаимодействия с AI.
     * Кнопки генерируются динамически на основе enum Scenario.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleScenarioCommand(chatId: Long) {
        // Создаем кнопки динамически из enum Scenario
        val buttons = Scenario.values().map { scenario ->
            InlineKeyboardButton(
                text = scenario.displayName,
                callbackData = scenario.callbackData
            )
        }

        // Размещаем кнопки по 2 в ряд
        val rows = buttons.chunked(2)

        val keyboard = InlineKeyboard(rows = rows)

        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "Выберите сценарий взаимодействия с AI:",
            keyboard = keyboard
        )
    }

    /**
     * Обрабатывает выбор сценария (через команду или callback).
     * Устанавливает выбранный сценарий для пользователя и отправляет подтверждение.
     *
     * @param chatId ID чата пользователя
     * @param scenario Выбранный сценарий
     */
    private suspend fun handleScenarioSelection(chatId: Long, scenario: Scenario) {
        SessionManager.setScenario(chatId, scenario)

        repository.sendMessage(
            chatId = chatId,
            text = "Выбран сценарий: ${scenario.displayName}"
        )
    }

    /**
     * Обрабатывает команду /db_stats.
     * Показывает общее количество записей в БД суммаризаций.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleDbStatsCommand(chatId: Long) {
        try {
            val totalCount = summaryRepository.getCount()

            repository.sendMessage(
                chatId = chatId,
                text = "📊 Статистика базы данных:\n\nВсего записей суммаризаций: $totalCount"
            )
        } catch (e: Exception) {
            repository.sendMessage(
                chatId = chatId,
                text = "❌ Ошибка при получении статистики БД:\n${e.message}"
            )
        }
    }

    /**
     * Обрабатывает команду /db_history.
     * Показывает последние 3 записи суммаризаций из БД.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleDbHistoryCommand(chatId: Long) {
        try {
            val lastRecords = summaryRepository.getLastSummaries(limit = 3)

            if (lastRecords.isEmpty()) {
                repository.sendMessage(
                    chatId = chatId,
                    text = "📭 База данных пуста. Записи суммаризаций пока отсутствуют."
                )
                return
            }

            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(ZoneId.systemDefault())

            val responseText = buildString {
                append("📜 Последние ${lastRecords.size} записи суммаризаций:\n\n")

                lastRecords.forEachIndexed { index, record ->
                    append("─────────────────────\n")
                    append("🆔 ID: ${record.id}\n")
                    append("📅 Дата: ${formatter.format(record.timestamp)}\n")
                    append("📝 Текст:\n")

                    // Обрезаем текст до 200 символов для компактности
                    val previewText = if (record.text.length > 200) {
                        record.text.take(200) + "..."
                    } else {
                        record.text
                    }
                    append(previewText)
                    append("\n\n")
                }
            }

            repository.sendMessage(chatId = chatId, text = responseText)

        } catch (e: Exception) {
            repository.sendMessage(
                chatId = chatId,
                text = "❌ Ошибка при получении истории из БД:\n${e.message}"
            )
        }
    }

    /**
     * Обрабатывает команду /db_clear.
     * Удаляет все записи суммаризаций из БД.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleDbClearCommand(chatId: Long) {
        try {
            val countBefore = summaryRepository.getCount()

            if (countBefore == 0L) {
                repository.sendMessage(
                    chatId = chatId,
                    text = "📭 База данных уже пуста."
                )
                return
            }

            summaryRepository.clearAll()

            repository.sendMessage(
                chatId = chatId,
                text = "✅ База данных очищена.\n\nУдалено записей: $countBefore"
            )
        } catch (e: Exception) {
            repository.sendMessage(
                chatId = chatId,
                text = "❌ Ошибка при очистке БД:\n${e.message}"
            )
        }
    }

    /**
     * Обрабатывает команду /mcp.
     * Отправляет сообщение с инлайн-кнопками для MCP команд.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleMcpCommand(chatId: Long) {
        val keyboard = InlineKeyboard(
            rows = listOf(
                listOf(
                    InlineKeyboardButton(
                        text = "Weather Tools",
                        callbackData = "mcp_weather_tools"
                    ),
                    InlineKeyboardButton(
                        text = "Weather Location",
                        callbackData = "mcp_weather_location"
                    )
                )
            )
        )

        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "MCP Weather Server Commands:",
            keyboard = keyboard
        )
    }

    /**
     * Обрабатывает команду /weather_tools.
     * Отправляет список доступных MCP инструментов.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleWeatherToolsCommand(chatId: Long) {
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
    }

    /**
     * Обрабатывает команду /weather_location.
     * Запрашивает у пользователя отправку геолокации для получения прогноза погоды.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleWeatherLocationCommand(chatId: Long) {
        repository.sendMessage(
            chatId = chatId,
            text = "📍 Please send your location to get weather forecast.\n\nUse the 📎 (attach) button and select Location."
        )
    }
}
