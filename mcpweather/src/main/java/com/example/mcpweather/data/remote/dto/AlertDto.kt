package com.example.mcpweather.data.remote.dto

import com.example.mcpweather.domain.model.Alert
import kotlinx.serialization.Serializable

@Serializable
data class AlertDto(
    val features: List<AlertFeatureDto>
)

@Serializable
data class AlertFeatureDto(
    val properties: AlertPropertiesDto
)

@Serializable
data class AlertPropertiesDto(
    val event: String,
    val headline: String,
    val description: String,
    val severity: String,
    val urgency: String,
    val areaDesc: String
)

// Mapper extension function
fun AlertPropertiesDto.toDomain(): Alert = Alert(
    event = event,
    headline = headline,
    description = description,
    severity = severity,
    urgency = urgency,
    areaDesc = areaDesc
)
