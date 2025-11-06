package com.example.tgbot.domain.repository

import com.example.tgbot.domain.model.InlineKeyboard
import com.example.tgbot.domain.model.Update

/**
 * Репозиторий для работы с Telegram Bot API.
 */
interface TelegramRepository {
    /**
     * Отправляет текстовое сообщение в чат.
     *
     * @param chatId ID чата
     * @param text Текст сообщения
     */
    suspend fun sendMessage(chatId: Long, text: String)

    /**
     * Отправляет текстовое сообщение с инлайн-клавиатурой в чат.
     *
     * @param chatId ID чата
     * @param text Текст сообщения
     * @param keyboard Инлайн-клавиатура с кнопками
     */
    suspend fun sendMessageWithKeyboard(chatId: Long, text: String, keyboard: InlineKeyboard)

    /**
     * Редактирует текст существующего сообщения.
     * При редактировании инлайн-клавиатура удаляется.
     *
     * @param chatId ID чата
     * @param messageId ID сообщения
     * @param text Новый текст сообщения
     */
    suspend fun editMessageText(chatId: Long, messageId: Long, text: String)

    /**
     * Отправляет ответ на callback-запрос от инлайн-кнопки.
     * Убирает индикатор загрузки (часики) на кнопке.
     *
     * @param callbackQueryId ID callback-запроса
     */
    suspend fun answerCallbackQuery(callbackQueryId: String)

    /**
     * Получает список обновлений от Telegram через long polling.
     *
     * @param offset Идентификатор первого обновления, которое нужно получить (для пропуска уже обработанных)
     * @return Список обновлений
     */
    suspend fun getUpdates(offset: Long?): List<Update>
}
