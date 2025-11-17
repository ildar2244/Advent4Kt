package com.example.tgbot.data.repository

import com.example.mcpweather.domain.model.Alert
import com.example.mcpweather.domain.model.Forecast
import com.example.mcpweather.domain.usecase.GetAlertsUseCase
import com.example.mcpweather.domain.usecase.GetForecastUseCase
import com.example.tgbot.domain.repository.McpRepository

class McpRepositoryImpl(
    private val getForecastUseCase: GetForecastUseCase,
    private val getAlertsUseCase: GetAlertsUseCase
) : McpRepository {

    override fun getAvailableTools(): Map<String, String> {
        return mapOf(
            "get_forecast" to "Get weather forecast for a location by latitude and longitude using weather.gov API",
            "get_alerts" to "Get active weather alerts for a US state using weather.gov API"
        )
    }

    override suspend fun getForecast(latitude: Double, longitude: Double): List<Forecast> {
        return getForecastUseCase(latitude, longitude)
    }

    override suspend fun getAlerts(state: String): List<Alert> {
        return getAlertsUseCase(state)
    }
}
