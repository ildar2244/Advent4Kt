package com.example.ragfirst.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class EmbeddingRequest(
    val model: String,
    val prompt: String
)
