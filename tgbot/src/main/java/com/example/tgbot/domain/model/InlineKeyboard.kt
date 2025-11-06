package com.example.tgbot.domain.model

/**
 * Инлайн-кнопка, отображаемая под сообщением.
 *
 * @property text Текст, отображаемый на кнопке
 * @property callbackData Данные, которые будут отправлены боту при нажатии на кнопку
 */
data class InlineKeyboardButton(
    val text: String,
    val callbackData: String
)

/**
 * Инлайн-клавиатура - набор кнопок под сообщением.
 * Кнопки организованы в строки (rows).
 *
 * @property rows Список строк кнопок. Каждая строка - список кнопок.
 */
data class InlineKeyboard(
    val rows: List<List<InlineKeyboardButton>>
)
