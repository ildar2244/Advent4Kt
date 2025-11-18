package com.example.mcpweather.app

import com.example.mcpweather.BuildConfig
import com.example.mcpweather.data.remote.WeatherGovApi
import com.example.mcpweather.data.repository.WeatherRepositoryImpl
import com.example.mcpweather.domain.usecase.GetAlertsUseCase
import com.example.mcpweather.domain.usecase.GetForecastUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

// JSON-RPC DTOs
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonObject? = null,
    val id: JsonElement
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val id: JsonElement
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class ToolCallParams(
    val name: String,
    val arguments: JsonObject
)

@Serializable
data class ToolCallResult(
    val content: List<ContentItem>
)

@Serializable
data class ContentItem(
    val type: String = "text",
    val text: String
)

@Serializable
data class PingMessage(
    val type: String = "ping"
)

@Serializable
data class PongMessage(
    val type: String = "pong"
)

fun main() {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    // Initialize HTTP client for weather.gov API
    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    // Initialize Weather API client
    val weatherApi = WeatherGovApi(httpClient)
    val weatherRepository = WeatherRepositoryImpl(weatherApi)

    // Initialize use cases
    val getForecastUseCase = GetForecastUseCase(weatherRepository)
    val getAlertsUseCase = GetAlertsUseCase(weatherRepository)

    val host = BuildConfig.MCP_WEATHER_WS_HOST
    val port = BuildConfig.MCP_WEATHER_WS_PORT

    println("Starting MCP Weather WebSocket Server on ws://$host:$port/mcp")

    // Start WebSocket server
    embeddedServer(ServerCIO, port = port, host = host) {
        install(WebSockets) {
            pingPeriod = 30.seconds
            timeout = 15.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket("/mcp") {
                println("Client connected: ${call.request.local.remoteHost}")

                try {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()

                                // Handle ping/pong
                                if (text.contains("\"type\"") && text.contains("\"ping\"")) {
                                    val pong = json.encodeToString(PongMessage.serializer(), PongMessage())
                                    send(Frame.Text(pong))
                                    continue
                                }

                                try {
                                    val request = json.decodeFromString<JsonRpcRequest>(text)
                                    val response = handleJsonRpcRequest(
                                        request,
                                        getForecastUseCase,
                                        getAlertsUseCase,
                                        json
                                    )
                                    val responseJson = json.encodeToString(JsonRpcResponse.serializer(), response)
                                    send(Frame.Text(responseJson))
                                } catch (e: Exception) {
                                    println("Error processing request: ${e.message}")
                                    e.printStackTrace()
                                    val errorResponse = JsonRpcResponse(
                                        error = JsonRpcError(
                                            code = -32700,
                                            message = "Parse error: ${e.message}"
                                        ),
                                        id = JsonPrimitive(-1)
                                    )
                                    val errorJson = json.encodeToString(JsonRpcResponse.serializer(), errorResponse)
                                    send(Frame.Text(errorJson))
                                }
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    println("WebSocket error: ${e.message}")
                } finally {
                    println("Client disconnected")
                }
            }
        }
    }.start(wait = true)
}

suspend fun handleJsonRpcRequest(
    request: JsonRpcRequest,
    getForecastUseCase: GetForecastUseCase,
    getAlertsUseCase: GetAlertsUseCase,
    json: Json
): JsonRpcResponse {
    return when (request.method) {
        "tools/call" -> {
            val params = request.params?.let { json.decodeFromJsonElement(ToolCallParams.serializer(), it) }
                ?: return JsonRpcResponse(
                    error = JsonRpcError(code = -32602, message = "Invalid params"),
                    id = request.id
                )

            when (params.name) {
                "get_forecast" -> handleGetForecast(params.arguments, getForecastUseCase, request.id)
                "get_alerts" -> handleGetAlerts(params.arguments, getAlertsUseCase, request.id)
                else -> JsonRpcResponse(
                    error = JsonRpcError(code = -32601, message = "Tool not found: ${params.name}"),
                    id = request.id
                )
            }
        }
        else -> JsonRpcResponse(
            error = JsonRpcError(code = -32601, message = "Method not found: ${request.method}"),
            id = request.id
        )
    }
}

suspend fun handleGetForecast(
    arguments: JsonObject,
    getForecastUseCase: GetForecastUseCase,
    requestId: JsonElement
): JsonRpcResponse {
    return try {
        val latitude = arguments["latitude"]?.jsonPrimitive?.doubleOrNull
            ?: return JsonRpcResponse(
                error = JsonRpcError(code = -32602, message = "Missing or invalid latitude"),
                id = requestId
            )
        val longitude = arguments["longitude"]?.jsonPrimitive?.doubleOrNull
            ?: return JsonRpcResponse(
                error = JsonRpcError(code = -32602, message = "Missing or invalid longitude"),
                id = requestId
            )

        val forecasts = getForecastUseCase(latitude, longitude)

        val responseText = buildString {
            appendLine("Weather Forecast for ($latitude, $longitude):")
            appendLine()
            forecasts.take(7).forEach { forecast ->
                appendLine("=== ${forecast.name} ===")
                appendLine("Temperature: ${forecast.temperature}°${forecast.temperatureUnit}")
                appendLine("Wind: ${forecast.windSpeed} ${forecast.windDirection}")
                appendLine("Conditions: ${forecast.shortForecast}")
                appendLine("Details: ${forecast.detailedForecast}")
                appendLine()
            }
        }

        val result = ToolCallResult(
            content = listOf(ContentItem(text = responseText))
        )

        JsonRpcResponse(
            result = Json.encodeToJsonElement(ToolCallResult.serializer(), result),
            id = requestId
        )
    } catch (e: Exception) {
        JsonRpcResponse(
            error = JsonRpcError(code = -32603, message = "Error getting forecast: ${e.message}"),
            id = requestId
        )
    }
}

suspend fun handleGetAlerts(
    arguments: JsonObject,
    getAlertsUseCase: GetAlertsUseCase,
    requestId: JsonElement
): JsonRpcResponse {
    return try {
        val state = arguments["state"]?.jsonPrimitive?.contentOrNull
            ?: return JsonRpcResponse(
                error = JsonRpcError(code = -32602, message = "Missing state parameter"),
                id = requestId
            )

        val alerts = getAlertsUseCase(state)

        val responseText = if (alerts.isEmpty()) {
            "No active weather alerts for $state"
        } else {
            buildString {
                appendLine("Active Weather Alerts for $state:")
                appendLine()
                alerts.forEach { alert ->
                    appendLine("=== ${alert.event} ===")
                    appendLine("Severity: ${alert.severity}")
                    appendLine("Urgency: ${alert.urgency}")
                    appendLine("Area: ${alert.areaDesc}")
                    appendLine("Headline: ${alert.headline}")
                    appendLine("Description: ${alert.description}")
                    appendLine()
                }
            }
        }

        val result = ToolCallResult(
            content = listOf(ContentItem(text = responseText))
        )

        JsonRpcResponse(
            result = Json.encodeToJsonElement(ToolCallResult.serializer(), result),
            id = requestId
        )
    } catch (e: Exception) {
        JsonRpcResponse(
            error = JsonRpcError(code = -32603, message = "Error getting alerts: ${e.message}"),
            id = requestId
        )
    }
}
