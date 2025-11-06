package com.example.tgbot.data.remote.dto

import com.example.tgbot.domain.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("id") val id: Long,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("username") val username: String? = null
) {
    fun toDomain(): User = User(
        id = id,
        firstName = firstName,
        lastName = lastName,
        username = username
    )
}
