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
 * - /models - Выбор AI-модели (GPT-4o Mini, Claude 3.5 Haiku)
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
     * Отправляет сообщение с двумя инлайн-кнопками для выбора AI-модели.
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
                )
            )
        )

        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "Выберите AI-модель для диалога:",
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
