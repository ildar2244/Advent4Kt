package com.example.tgbot.domain.model

/**
 * Пользователь Telegram.
 *
 * @property id Уникальный идентификатор пользователя
 * @property firstName Имя пользователя
 * @property lastName Фамилия пользователя (опционально)
 * @property username Username пользователя без @ (опционально)
 */
data class User(
    val id: Long,
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null
)
