package com.example.ragfirst.data.remote

import com.example.ragfirst.BuildConfig
import com.example.ragfirst.data.remote.dto.EmbeddingRequest
import com.example.ragfirst.data.remote.dto.EmbeddingResponse
import com.example.ragfirst.data.remote.dto.OllamaErrorResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class OllamaApiClient(
    private val baseUrl: String = BuildConfig.OLLAMA_BASE_URL,
    private val model: String,
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay(base = 2.0, maxDelayMs = 10000)
            retryIf { _, response ->
                response.status.value == 500 || response.status.value == 503
            }
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
        val httpResponse = client.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(EmbeddingRequest(model = model, prompt = text))
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody = try {
                httpResponse.body<OllamaErrorResponse>()
            } catch (e: Exception) {
                OllamaErrorResponse("HTTP ${httpResponse.status.value}: ${httpResponse.status.description}")
            }
            throw OllamaApiException("Ollama API error: ${errorBody.error}", httpResponse.status.value)
        }

        val response: EmbeddingResponse = httpResponse.body()
        return response.embedding.toFloatArray()
    }

    fun close() {
        client.close()
    }
}

class OllamaApiException(
    message: String,
    val statusCode: Int
) : Exception(message)

