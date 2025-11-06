package com.example.tgbot.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendMessageRequest(
    @SerialName("chat_id") val chatId: Long,
    @SerialName("text") val text: String,
    @SerialName("reply_markup") val replyMarkup: InlineKeyboardMarkupDto? = null
)

@Serializable
data class SendMessageResponse(
    @SerialName("ok") val ok: Boolean,
    @SerialName("result") val result: MessageDto? = null
)
