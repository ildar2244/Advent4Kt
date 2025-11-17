package com.example.tgbot.data.remote.dto

import com.example.tgbot.domain.model.Location
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationDto(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double
) {
    fun toDomain(): Location = Location(
        latitude = latitude,
        longitude = longitude
    )
}
