package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.InlineKeyboard
import com.example.tgbot.domain.model.InlineKeyboardButton
import com.example.tgbot.domain.model.Message
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
            else -> {
                // Игнорируем неизвестные команды
            }
        }
    }

    /**
     * Обрабатывает команду /models.
     * Отправляет сообщение с двумя инлайн-кнопками для выбора модели.
     *
     * @param chatId ID чата, в который нужно отправить сообщение
     */
    private suspend fun handleModelsCommand(chatId: Long) {
        // Создаем клавиатуру с двумя кнопками в одной строке
        val keyboard = InlineKeyboard(
            rows = listOf(
                listOf(
                    InlineKeyboardButton(text = "Model 1", callbackData = "model_1"),
                    InlineKeyboardButton(text = "Model 2", callbackData = "model_2")
                )
            )
        )

        repository.sendMessageWithKeyboard(
            chatId = chatId,
            text = "Выберите модель:",
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
}
