package com.example.tgbot.data.repository

import com.example.tgbot.data.remote.McpWebSocketClient
import com.example.tgbot.domain.repository.McpRepository

class McpRepositoryImpl(
    private val mcpClient: McpWebSocketClient
) : McpRepository {

    override fun getAvailableTools(): Map<String, String> {
        return mapOf(
            "get_forecast" to "Get weather forecast for a location by latitude and longitude using weather.gov API",
            "get_alerts" to "Get active weather alerts for a US state using weather.gov API"
        )
    }

    override suspend fun getForecast(latitude: Double, longitude: Double): String {
        val result = mcpClient.callTool(
            name = "get_forecast",
            arguments = mapOf(
                "latitude" to latitude,
                "longitude" to longitude
            )
        )

        // Extract text from content items
        return result.content.joinToString("\n") { it.text }
    }

    override suspend fun getAlerts(state: String): String {
        val result = mcpClient.callTool(
            name = "get_alerts",
            arguments = mapOf(
                "state" to state
            )
        )

        // Extract text from content items
        return result.content.joinToString("\n") { it.text }
    }
}
