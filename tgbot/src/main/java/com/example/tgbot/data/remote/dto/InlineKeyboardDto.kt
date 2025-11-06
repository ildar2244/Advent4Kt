package com.example.tgbot.data.remote.dto

import com.example.tgbot.domain.model.InlineKeyboard
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для инлайн-кнопки согласно Telegram Bot API.
 */
@Serializable
data class InlineKeyboardButtonDto(
    @SerialName("text") val text: String,
    @SerialName("callback_data") val callbackData: String
)

/**
 * DTO для инлайн-клавиатуры согласно Telegram Bot API.
 */
@Serializable
data class InlineKeyboardMarkupDto(
    @SerialName("inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButtonDto>>
)

/**
 * Преобразует доменную модель InlineKeyboard в DTO для отправки в Telegram API.
 */
fun InlineKeyboard.toDto(): InlineKeyboardMarkupDto {
    return InlineKeyboardMarkupDto(
        inlineKeyboard = rows.map { row ->
            row.map { button ->
                InlineKeyboardButtonDto(
                    text = button.text,
                    callbackData = button.callbackData
                )
            }
        }
    )
}
