package com.example.tgbot.domain.util

import com.example.mcpweather.domain.model.Forecast

object WeatherFormatter {
    /**
     * Format forecast in brief format (first 2 periods)
     * @param forecasts List of forecast periods
     * @return Formatted string for Telegram message
     */
    fun formatBrief(forecasts: List<Forecast>): String {
        if (forecasts.isEmpty()) {
            return "No forecast data available"
        }

        return buildString {
            appendLine("Weather Forecast:")
            appendLine()
            forecasts.take(2).forEach { forecast ->
                appendLine("${getWeatherEmoji(forecast.shortForecast)} ${forecast.name}")
                appendLine("Temperature: ${forecast.temperature}°${forecast.temperatureUnit}")
                appendLine("Wind: ${forecast.windSpeed} ${forecast.windDirection}")
                appendLine("Conditions: ${forecast.shortForecast}")
                appendLine()
            }
        }.trim()
    }

    /**
     * Get emoji based on weather conditions
     */
    private fun getWeatherEmoji(conditions: String): String {
        return when {
            conditions.contains("Clear", ignoreCase = true) -> "☀️"
            conditions.contains("Sunny", ignoreCase = true) -> "☀️"
            conditions.contains("Partly Cloudy", ignoreCase = true) -> "⛅"
            conditions.contains("Cloudy", ignoreCase = true) -> "☁️"
            conditions.contains("Rain", ignoreCase = true) -> "🌧️"
            conditions.contains("Showers", ignoreCase = true) -> "🌦️"
            conditions.contains("Thunderstorm", ignoreCase = true) -> "⛈️"
            conditions.contains("Snow", ignoreCase = true) -> "❄️"
            conditions.contains("Fog", ignoreCase = true) -> "🌫️"
            conditions.contains("Wind", ignoreCase = true) -> "💨"
            else -> "🌤️"
        }
    }
}
