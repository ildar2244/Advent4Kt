package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.CallbackQuery
import com.example.tgbot.domain.model.InlineKeyboard
import com.example.tgbot.domain.model.InlineKeyboardButton
import com.example.tgbot.domain.model.Scenario
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.repository.TelegramRepository

/**
 * Use case для обработки callback-запросов от инлайн-кнопок.
 *
 * Обрабатываемые типы callback'ов:
 * - "show_models" - Показать список AI-моделей (из команды /start)
 * - "model_gpt", "model_claude", "model_yandex" - Выбор конкретной AI-модели
 * - "scenario_*" - Выбор сценария взаимодействия (динамически из Scenario enum)
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
    private val repository: TelegramRepository
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
}
