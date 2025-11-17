package com.example.mcpweather.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PointsDto(
    val properties: PointPropertiesDto
)

@Serializable
data class PointPropertiesDto(
    val forecast: String,
    val forecastHourly: String
)
