package com.example.tgbot.domain.repository

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
     * @return Formatted weather forecast text
     */
    suspend fun getForecast(latitude: Double, longitude: Double): String

    /**
     * Get active weather alerts for a US state
     * @param state Two-letter US state code (e.g., "CA", "TX")
     * @return Formatted weather alerts text
     */
    suspend fun getAlerts(state: String): String
}
