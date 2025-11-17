package com.example.mcpweather.domain.model

data class Forecast(
    val name: String,
    val temperature: Int,
    val temperatureUnit: String,
    val windSpeed: String,
    val windDirection: String,
    val shortForecast: String,
    val detailedForecast: String
)
