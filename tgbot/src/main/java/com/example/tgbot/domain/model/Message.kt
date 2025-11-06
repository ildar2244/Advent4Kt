package com.example.tgbot.domain.model

/**
 * Сообщение в Telegram.
 *
 * @property messageId Уникальный идентификатор сообщения в чате
 * @property chatId Идентификатор чата, в котором было отправлено сообщение
 * @property from Пользователь, отправивший сообщение
 * @property text Текст сообщения (если есть)
 */
data class Message(
    val messageId: Long,
    val chatId: Long,
    val from: User,
    val text: String?
)
