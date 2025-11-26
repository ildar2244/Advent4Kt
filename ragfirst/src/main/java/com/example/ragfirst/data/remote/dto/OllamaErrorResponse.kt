package com.example.ragfirst.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class OllamaErrorResponse(
    val error: String
)
