package com.example.tgbot.data.remote.dto

import com.example.tgbot.domain.model.Update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateDto(
    @SerialName("update_id") val updateId: Long,
    @SerialName("message") val message: MessageDto? = null,
    @SerialName("callback_query") val callbackQuery: CallbackQueryDto? = null
) {
    fun toDomain(): Update = Update(
        updateId = updateId,
        message = message?.toDomain(),
        callbackQuery = callbackQuery?.toDomain()
    )
}

@Serializable
data class GetUpdatesResponse(
    @SerialName("ok") val ok: Boolean,
    @SerialName("result") val result: List<UpdateDto>
)
