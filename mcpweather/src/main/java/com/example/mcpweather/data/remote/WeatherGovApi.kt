package com.example.mcpweather.data.remote

import com.example.mcpweather.data.remote.dto.AlertDto
import com.example.mcpweather.data.remote.dto.ForecastDto
import com.example.mcpweather.data.remote.dto.PointsDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class WeatherGovApi(private val httpClient: HttpClient) {

    companion object {
        private const val BASE_URL = "https://api.weather.gov"
        private const val USER_AGENT = "Advent4Kt-Weather-MCP/1.0"
    }

    /**
     * Get point metadata for a location (lat, lon)
     * Returns forecast URLs for the location
     */
    suspend fun getPoints(latitude: Double, longitude: Double): PointsDto {
        return httpClient.get("$BASE_URL/points/$latitude,$longitude") {
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.body()
    }

    /**
     * Get forecast from a forecast URL
     * Usually obtained from getPoints() response
     */
    suspend fun getForecast(forecastUrl: String): ForecastDto {
        return httpClient.get(forecastUrl) {
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.body()
    }

    /**
     * Get active weather alerts for a US state
     * @param state Two-letter US state code (e.g., "CA", "TX")
     */
    suspend fun getAlerts(state: String): AlertDto {
        return httpClient.get("$BASE_URL/alerts/active") {
            header(HttpHeaders.UserAgent, USER_AGENT)
            parameter("area", state.uppercase())
        }.body()
    }
}
