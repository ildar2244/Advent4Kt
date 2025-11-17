package com.example.tgbot.domain.repository

import com.example.mcpweather.domain.model.Alert
import com.example.mcpweather.domain.model.Forecast

interface McpRepository {
    /**
     * Get list of available MCP tools
     * @return Map of tool name to tool description
     */
    fun getAvailableTools(): Map<String, String>

    /**
     * Get weather forecast for a location
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @return List of forecast periods
     */
    suspend fun getForecast(latitude: Double, longitude: Double): List<Forecast>

    /**
     * Get active weather alerts for a US state
     * @param state Two-letter US state code (e.g., "CA", "TX")
     * @return List of active alerts
     */
    suspend fun getAlerts(state: String): List<Alert>
}
