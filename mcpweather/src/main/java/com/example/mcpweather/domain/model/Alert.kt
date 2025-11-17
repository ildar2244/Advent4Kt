package com.example.mcpweather.domain.model

data class Alert(
    val event: String,
    val headline: String,
    val description: String,
    val severity: String,
    val urgency: String,
    val areaDesc: String
)
