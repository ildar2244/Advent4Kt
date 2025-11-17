package com.example.mcpweather.data.remote.dto

import com.example.mcpweather.domain.model.Forecast
import kotlinx.serialization.Serializable

@Serializable
data class ForecastDto(
    val properties: ForecastPropertiesDto
)

@Serializable
data class ForecastPropertiesDto(
    val periods: List<ForecastPeriodDto>
)

@Serializable
data class ForecastPeriodDto(
    val name: String,
    val temperature: Int,
    val temperatureUnit: String,
    val windSpeed: String,
    val windDirection: String,
    val shortForecast: String,
    val detailedForecast: String
)

// Mapper extension function
fun ForecastPeriodDto.toDomain(): Forecast = Forecast(
    name = name,
    temperature = temperature,
    temperatureUnit = temperatureUnit,
    windSpeed = windSpeed,
    windDirection = windDirection,
    shortForecast = shortForecast,
    detailedForecast = detailedForecast
)
