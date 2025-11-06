package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.CallbackQuery
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.repository.TelegramRepository

/**
 * Use case для обработки callback-запросов от инлайн-кнопок.
 */
class HandleCallbackUseCase(
    private val repository: TelegramRepository
) {
    /**
     * Обрабатывает нажатие пользователем на инлайн-кнопку.
     * При выборе AI-модели: сохраняет модель в сессию, скрывает кнопки и отправляет приветствие.
     *
     * @param callback Callback-запрос от нажатой кнопки
     */
    suspend operator fun invoke(callback: CallbackQuery) {
        val data = callback.data ?: return
        val message = callback.message ?: return

        // Определяем выбранную модель на основе callback_data
        val selectedModel = when (data) {
            "model_gpt" -> AiModel.GPT_4O_MINI
            "model_claude" -> AiModel.CLAUDE_HAIKU
            else -> return
        }

        // Сохраняем выбранную модель в сессии пользователя
        SessionManager.setModel(message.chatId, selectedModel)

        // Добавляем системное сообщение в начало диалога
        SessionManager.addMessage(
            message.chatId,
            AiMessage(
                role = MessageRole.SYSTEM,
                content = "Ты полезный AI-ассистент. Отвечай кратко и по делу на русском языке."
            )
        )

        // Редактируем сообщение: убираем кнопки и меняем текст на подтверждение выбора
        repository.editMessageText(
            chatId = message.chatId,
            messageId = message.messageId,
            text = "✓ Выбрана модель: ${selectedModel.displayName}"
        )

        // Отправляем приветственное сообщение с инструкциями
        repository.sendMessage(
            chatId = message.chatId,
            text = "Я готов ответить на ваши вопросы с помощью ${selectedModel.displayName}.\n\n" +
                    "Напишите ваше сообщение, и я отвечу.\n\n" +
                    "Используйте /stop для выхода из режима AI-консультации."
        )

        // Отвечаем на callback (убирает "часики" на кнопке в Telegram)
        repository.answerCallbackQuery(callback.id)
    }
}
