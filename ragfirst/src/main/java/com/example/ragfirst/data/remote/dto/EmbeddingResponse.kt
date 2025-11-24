package com.example.ragfirst.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class EmbeddingResponse(
    val embedding: List<Float>
)
