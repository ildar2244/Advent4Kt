package com.example.mcpweather.domain.usecase

import com.example.mcpweather.domain.model.Forecast
import com.example.mcpweather.domain.repository.WeatherRepository

class GetForecastUseCase(
    private val weatherRepository: WeatherRepository
) {
    /**
     * Get weather forecast for a location
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @return List of forecast periods
     */
    suspend operator fun invoke(latitude: Double, longitude: Double): List<Forecast> {
        return weatherRepository.getForecast(latitude, longitude)
    }
}
