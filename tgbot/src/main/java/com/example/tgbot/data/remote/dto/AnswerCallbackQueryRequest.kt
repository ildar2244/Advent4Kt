package com.example.tgbot.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnswerCallbackQueryRequest(
    @SerialName("callback_query_id") val callbackQueryId: String,
    @SerialName("text") val text: String? = null,
    @SerialName("show_alert") val showAlert: Boolean? = null
)

@Serializable
data class AnswerCallbackQueryResponse(
    @SerialName("ok") val ok: Boolean
)
