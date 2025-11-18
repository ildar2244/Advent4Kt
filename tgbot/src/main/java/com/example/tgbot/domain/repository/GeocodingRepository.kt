package com.example.tgbot.domain.repository

import com.example.tgbot.domain.model.Location

/**
 * Repository для геокодинга - конвертации названий мест в географические координаты.
 * Используется для преобразования текстовых запросов (например, "Париж") в координаты (lat, lon).
 */
interface GeocodingRepository {
    /**
     * Получить координаты по названию места.
     *
     * @param query Название места (город, адрес, страна, и т.д.)
     * @return Location с координатами или null если место не найдено
     * @throws Exception если произошла ошибка сети или сервиса
     */
    suspend fun geocode(query: String): Location?
}
