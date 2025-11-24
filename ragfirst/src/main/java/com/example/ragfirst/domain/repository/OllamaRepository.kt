package com.example.ragfirst.domain.repository

interface OllamaRepository {
    suspend fun generateEmbedding(text: String): FloatArray
}
