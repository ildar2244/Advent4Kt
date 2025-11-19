package com.example.mcptasks.data.remote.dto.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для запроса отправки сообщения в Telegram.
 *
 * @property chatId Идентификатор чата или канала для отправки сообщения
 * @property text Текст сообщения (максимум 4096 символов)
 * @property parseMode Режим парсинга текста: "Markdown" или "HTML" (опционально)
 */
@Serializable
data class SendMessageRequest(
    @SerialName("chat_id") val chatId: String,  // Может быть числовой ID или @username
    @SerialName("text") val text: String,
    @SerialName("parse_mode") val parseMode: String? = null
)

/**
 * DTO для ответа от Telegram API при отправке сообщения.
 *
 * @property ok Статус успешности запроса
 * @property description Описание ошибки (если ok = false)
 */
@Serializable
data class SendMessageResponse(
    @SerialName("ok") val ok: Boolean,
    @SerialName("description") val description: String? = null
)
