package com.example.tgbot.domain.model

/**
 * Callback-запрос, получаемый при нажатии пользователем на инлайн-кнопку.
 *
 * @property id Уникальный идентификатор callback-запроса
 * @property from Пользователь, нажавший на кнопку
 * @property message Сообщение, к которому была прикреплена нажатая кнопка (может быть null)
 * @property data Данные callback_data из нажатой кнопки (может быть null)
 */
data class CallbackQuery(
    val id: String,
    val from: User,
    val message: Message?,
    val data: String?
)
