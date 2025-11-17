package com.example.tgbot.domain.model

/**
 * Местоположение (геолокация) в Telegram.
 *
 * @property latitude Широта
 * @property longitude Долгота
 */
data class Location(
    val latitude: Double,
    val longitude: Double
)
