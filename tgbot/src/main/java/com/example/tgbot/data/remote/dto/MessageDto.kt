package com.example.tgbot.data.remote.dto

import com.example.tgbot.domain.model.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    @SerialName("message_id") val messageId: Long,
    @SerialName("from") val from: UserDto,
    @SerialName("chat") val chat: ChatDto,
    @SerialName("text") val text: String? = null,
    @SerialName("location") val location: LocationDto? = null
) {
    fun toDomain(): Message = Message(
        messageId = messageId,
        chatId = chat.id,
        from = from.toDomain(),
        text = text,
        location = location?.toDomain()
    )
}

@Serializable
data class ChatDto(
    @SerialName("id") val id: Long
)
