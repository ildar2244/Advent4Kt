package com.example.tgbot.data.remote.dto

import com.example.tgbot.domain.model.CallbackQuery
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallbackQueryDto(
    @SerialName("id") val id: String,
    @SerialName("from") val from: UserDto,
    @SerialName("message") val message: MessageDto? = null,
    @SerialName("data") val data: String? = null
) {
    fun toDomain(): CallbackQuery = CallbackQuery(
        id = id,
        from = from.toDomain(),
        message = message?.toDomain(),
        data = data
    )
}
