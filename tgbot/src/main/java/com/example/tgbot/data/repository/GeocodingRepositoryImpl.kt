package com.example.tgbot.data.repository

import com.example.tgbot.data.remote.dto.NominatimResultDto
import com.example.tgbot.data.remote.dto.toDomain
import com.example.tgbot.domain.model.Location
import com.example.tgbot.domain.repository.GeocodingRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.LoggerFactory

/**
 * Реализация GeocodingRepository с использованием Nominatim API (OpenStreetMap).
 * Nominatim - бесплатный геокодинг сервис, не требующий API ключа.
 *
 * @property client HTTP клиент Ktor для выполнения запросов
 */
class GeocodingRepositoryImpl(
    private val client: HttpClient
) : GeocodingRepository {

    private val logger = LoggerFactory.getLogger(GeocodingRepositoryImpl::class.java)

    companion object {
        private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org"
        private const val USER_AGENT = "TelegramBot-Advent4Kt/1.0" // Nominatim требует User-Agent
    }

    /**
     * Геокодинг через Nominatim API.
     * Преобразует название места (например, "Париж") в координаты.
     *
     * @param query Название места для поиска
     * @return Location с координатами или null если не найдено
     */
    override suspend fun geocode(query: String): Location? {
        return try {
            logger.info("Geocoding query: $query")

            val results: List<NominatimResultDto> = client.get("$NOMINATIM_BASE_URL/search") {
                header("User-Agent", USER_AGENT)
                parameter("q", query)
                parameter("format", "json")
                parameter("limit", 1) // Получаем только первый (наиболее релевантный) результат
                parameter("addressdetails", 0) // Не нужны детали адреса
            }.body()

            if (results.isEmpty()) {
                logger.warn("Geocoding failed: no results for query '$query'")
                null
            } else {
                val location = results.first().toDomain()
                logger.info("Geocoding success: '$query' -> (${location.latitude}, ${location.longitude})")
                location
            }
        } catch (e: Exception) {
            logger.error("Geocoding error for query '$query': ${e.message}", e)
            null
        }
    }
}
