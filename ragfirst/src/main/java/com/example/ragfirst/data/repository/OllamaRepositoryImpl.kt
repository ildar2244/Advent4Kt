package com.example.ragfirst.data.repository

import com.example.ragfirst.data.remote.OllamaApiClient
import com.example.ragfirst.domain.repository.OllamaRepository

class OllamaRepositoryImpl(
    private val ollamaApiClient: OllamaApiClient
) : OllamaRepository {
    override suspend fun generateEmbedding(text: String): FloatArray {
        return ollamaApiClient.generateEmbedding(text)
    }
}
