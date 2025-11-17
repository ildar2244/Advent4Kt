package com.example.mcpweather.app

import com.example.mcpweather.data.remote.WeatherGovApi
import com.example.mcpweather.data.repository.WeatherRepositoryImpl
import com.example.mcpweather.domain.usecase.GetAlertsUseCase
import com.example.mcpweather.domain.usecase.GetForecastUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

suspend fun main() {
    // Initialize HTTP client
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
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

    // Create MCP server
    val server = Server(
        serverInfo = Implementation(
            name = "advent4kt-weather",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )

    // Register Weather Forecast Tool
    server.addTool(
        name = "get_forecast",
        description = "Get weather forecast for a location by latitude and longitude using weather.gov API",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "latitude" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("number"),
                            "description" to JsonPrimitive("Latitude coordinate (e.g., 40.7484)")
                        )
                    ),
                    "longitude" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("number"),
                            "description" to JsonPrimitive("Longitude coordinate (e.g., -73.9856)")
                        )
                    )
                )
            ),
            required = listOf("latitude", "longitude")
        )
    ) { request ->
        try {
            val latitude = request.arguments["latitude"]?.jsonPrimitive?.doubleOrNull
                ?: throw IllegalArgumentException("Missing or invalid latitude")
            val longitude = request.arguments["longitude"]?.jsonPrimitive?.doubleOrNull
                ?: throw IllegalArgumentException("Missing or invalid longitude")

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

            CallToolResult(
                content = listOf(
                    TextContent(
                        text = responseText
                    )
                ),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Error getting forecast: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    // Register Weather Alerts Tool
    server.addTool(
        name = "get_alerts",
        description = "Get active weather alerts for a US state using weather.gov API",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "state" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Two-letter US state code (e.g., 'CA', 'TX', 'NY')")
                        )
                    )
                )
            ),
            required = listOf("state")
        )
    ) { request ->
        try {
            val state = request.arguments["state"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("Missing state parameter")

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

            CallToolResult(
                content = listOf(
                    TextContent(
                        text = responseText
                    )
                ),
                isError = false
            )
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = "Error getting alerts: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    // Connect STDIO transport
    val transport = StdioServerTransport(
        inputStream = System.`in` as Source,
        outputStream = System.out as Sink
    )

    server.connect(transport)
}
