package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.Message
import com.example.tgbot.domain.repository.TelegramRepository

/**
 * Use case для обработки обычных текстовых сообщений (не команд).
 * Реализует эхо-функционал - отправляет тот же текст обратно пользователю.
 */
class HandleMessageUseCase(
    private val repository: TelegramRepository
) {
    /**
     * Обрабатывает входящее сообщение.
     *
     * @param message Входящее сообщение от пользователя
     */
    suspend operator fun invoke(message: Message) {
        val responseText = message.text ?: return
        repository.sendMessage(message.chatId, responseText)
    }
}
