package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.InlineKeyboard
import com.example.tgbot.domain.model.InlineKeyboardButton
import com.example.tgbot.domain.model.Message
import com.example.tgbot.domain.model.Scenario
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.repository.TelegramRepository

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
 *
 * Команды сценариев обрабатываются динамически через Scenario.findByCommand(),
 * что позволяет легко добавлять новые сценарии без изменения логики обработки.
 */
class HandleCommandUseCase(
    private val repository: TelegramRepository
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
}
