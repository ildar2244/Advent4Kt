package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.InlineKeyboard
import com.example.tgbot.domain.model.InlineKeyboardButton
import com.example.tgbot.domain.model.Message
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.repository.TelegramRepository

/**
 * Use case для обработки команд бота (сообщения, начинающиеся с /).
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
            command == "/models" -> handleModelsCommand(message.chatId)
            command == "/consultant" -> handleConsultantCommand(message.chatId)
            command == "/stop" -> handleStopCommand(message.chatId)
            else -> {
                // Игнорируем неизвестные команды
            }
        }
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
     * Обрабатывает команду /consultant.
     * Отправляет приветственное сообщение о выборе режима консультанта.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleConsultantCommand(chatId: Long) {
        repository.sendMessage(
            chatId = chatId,
            text = "Вы выбрали режим консультант"
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
}
