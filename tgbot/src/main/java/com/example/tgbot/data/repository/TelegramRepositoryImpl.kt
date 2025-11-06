package com.example.tgbot.data.repository

import com.example.tgbot.data.remote.TelegramApi
import com.example.tgbot.data.remote.dto.*
import com.example.tgbot.domain.model.InlineKeyboard
import com.example.tgbot.domain.model.Update
import com.example.tgbot.domain.repository.TelegramRepository

/**
 * Реализация репозитория для работы с Telegram Bot API.
 * Использует TelegramApi для HTTP-запросов и преобразует DTO в доменные модели.
 */
class TelegramRepositoryImpl(
    private val api: TelegramApi
) : TelegramRepository {

    override suspend fun sendMessage(chatId: Long, text: String) {
        api.sendMessage(SendMessageRequest(chatId, text))
    }

    override suspend fun sendMessageWithKeyboard(chatId: Long, text: String, keyboard: InlineKeyboard) {
        api.sendMessage(
            SendMessageRequest(
                chatId = chatId,
                text = text,
                replyMarkup = keyboard.toDto() // Преобразуем доменную модель в DTO
            )
        )
    }

    override suspend fun editMessageText(chatId: Long, messageId: Long, text: String) {
        api.editMessageText(
            EditMessageTextRequest(
                chatId = chatId,
                messageId = messageId,
                text = text
            )
        )
    }

    override suspend fun answerCallbackQuery(callbackQueryId: String) {
        api.answerCallbackQuery(
            AnswerCallbackQueryRequest(callbackQueryId)
        )
    }

    override suspend fun getUpdates(offset: Long?): List<Update> {
        val response = api.getUpdates(offset)
        // Преобразуем DTO в доменные модели
        return response.result.map { it.toDomain() }
    }
}
