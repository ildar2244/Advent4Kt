package com.example.tgbot.data.remote.dto

import com.example.tgbot.domain.model.Location
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO для ответа от Nominatim Geocoding API (OpenStreetMap).
 * API возвращает массив результатов, каждый содержит координаты и метаданные.
 *
 * @property lat Широта в виде строки (например, "48.8566")
 * @property lon Долгота в виде строки (например, "2.3522")
 * @property displayName Полное отформатированное название места
 * @property type Тип места (city, town, village, и т.д.)
 * @property importance Важность результата (0.0 - 1.0)
 */
@Serializable
data class NominatimResultDto(
    @SerialName("lat") val lat: String,
    @SerialName("lon") val lon: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("type") val type: String? = null,
    @SerialName("importance") val importance: Double? = null
)

/**
 * Преобразование DTO в domain модель Location.
 */
fun NominatimResultDto.toDomain(): Location {
    return Location(
        latitude = lat.toDouble(),
        longitude = lon.toDouble()
    )
}
