package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.CallbackQuery
import com.example.tgbot.domain.repository.TelegramRepository

/**
 * Use case для обработки callback-запросов от инлайн-кнопок.
 */
class HandleCallbackUseCase(
    private val repository: TelegramRepository
) {
    /**
     * Обрабатывает нажатие пользователем на инлайн-кнопку.
     * Редактирует сообщение (убирает кнопки, меняет текст) и отвечает на callback.
     *
     * @param callback Callback-запрос от нажатой кнопки
     */
    suspend operator fun invoke(callback: CallbackQuery) {
        val data = callback.data ?: return
        val message = callback.message ?: return

        // Определяем текст на основе callback_data
        val buttonText = when (data) {
            "model_1" -> "Model 1"
            "model_2" -> "Model 2"
            else -> return
        }

        // Редактируем сообщение: убираем кнопки и меняем текст
        repository.editMessageText(
            chatId = message.chatId,
            messageId = message.messageId,
            text = "кнопка+$buttonText"
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }
}
