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
 * - "show_models" - Показать список AI-провайдеров (из команды /start)
 * - "model_gpt", "model_claude", "model_yandex" - Выбор конкретной AI-модели
 * - "model_huggingface" - Показать список моделей HuggingFace
 * - "hf_model:*" - Выбор конкретной модели HuggingFace
 * - "scenario_*" - Выбор сценария взаимодействия (динамически из Scenario enum)
 * - "set_temp:*" - Установка значения temperature
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
}
