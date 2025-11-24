package com.example.ragfirst.data.remote

import com.example.ragfirst.BuildConfig
import com.example.ragfirst.data.remote.dto.EmbeddingRequest
import com.example.ragfirst.data.remote.dto.EmbeddingResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class OllamaApiClient(
    private val baseUrl: String = BuildConfig.OLLAMA_BASE_URL,
    private val model: String = "nomic-embed-text"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 10000
        }
    }

    suspend fun generateEmbedding(text: String): FloatArray {
        val response: EmbeddingResponse = client.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(EmbeddingRequest(model = model, prompt = text))
        }.body()

        return response.embedding.toFloatArray()
    }

    fun close() {
        client.close()
    }
}
