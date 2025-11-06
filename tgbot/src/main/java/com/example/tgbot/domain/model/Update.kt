package com.example.tgbot.domain.model

/**
 * Обновление от Telegram Bot API.
 * Может содержать новое сообщение или callback-запрос от инлайн-кнопки.
 *
 * @property updateId Уникальный идентификатор обновления (используется для отслеживания обработанных обновлений)
 * @property message Новое входящее сообщение (если есть)
 * @property callbackQuery Callback-запрос от инлайн-кнопки (если есть)
 */
data class Update(
    val updateId: Long,
    val message: Message?,
    val callbackQuery: CallbackQuery?
)
