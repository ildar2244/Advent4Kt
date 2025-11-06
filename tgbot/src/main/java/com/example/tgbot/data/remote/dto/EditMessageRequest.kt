package com.example.tgbot.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EditMessageTextRequest(
    @SerialName("chat_id") val chatId: Long,
    @SerialName("message_id") val messageId: Long,
    @SerialName("text") val text: String
)

@Serializable
data class EditMessageResponse(
    @SerialName("ok") val ok: Boolean,
    @SerialName("result") val result: MessageDto? = null
)
