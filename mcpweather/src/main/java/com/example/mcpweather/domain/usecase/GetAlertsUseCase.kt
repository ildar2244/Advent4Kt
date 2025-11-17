package com.example.mcpweather.domain.usecase

import com.example.mcpweather.domain.model.Alert
import com.example.mcpweather.domain.repository.WeatherRepository

class GetAlertsUseCase(
    private val weatherRepository: WeatherRepository
) {
    /**
     * Get active weather alerts for a US state
     * @param state Two-letter US state code (e.g., "CA", "TX")
     * @return List of active alerts
     */
    suspend operator fun invoke(state: String): List<Alert> {
        return weatherRepository.getAlerts(state)
    }
}
