package com.example.mcpweather.data.repository

import com.example.mcpweather.data.remote.WeatherGovApi
import com.example.mcpweather.data.remote.dto.toDomain
import com.example.mcpweather.domain.model.Alert
import com.example.mcpweather.domain.model.Forecast
import com.example.mcpweather.domain.repository.WeatherRepository

class WeatherRepositoryImpl(
    private val weatherGovApi: WeatherGovApi
) : WeatherRepository {

    override suspend fun getForecast(latitude: Double, longitude: Double): List<Forecast> {
        // Step 1: Get points metadata
        val pointsDto = weatherGovApi.getPoints(latitude, longitude)

        // Step 2: Get forecast from forecast URL
        val forecastUrl = pointsDto.properties.forecast
        val forecastDto = weatherGovApi.getForecast(forecastUrl)

        // Step 3: Map DTOs to domain models
        return forecastDto.properties.periods.map { it.toDomain() }
    }

    override suspend fun getAlerts(state: String): List<Alert> {
        val alertDto = weatherGovApi.getAlerts(state)
        return alertDto.features.map { it.properties.toDomain() }
    }
}
