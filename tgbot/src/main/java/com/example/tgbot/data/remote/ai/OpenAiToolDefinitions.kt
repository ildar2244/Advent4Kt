package com.example.tgbot.data.remote.ai

import com.example.tgbot.data.remote.dto.ai.openai.FunctionDefinition
import com.example.tgbot.data.remote.dto.ai.openai.OpenAiTool
import kotlinx.serialization.json.*

/**
 * Определения инструментов MCP для OpenAI Function Calling API.
 * Содержит описания функций get_forecast и get_alerts в формате OpenAI tools.
 */
object OpenAiToolDefinitions {

    /**
     * Получить список всех доступных MCP инструментов для OpenAI.
     * @return Список OpenAiTool с определениями get_forecast и get_alerts
     */
    fun getMcpTools(): List<OpenAiTool> {
        return listOf(
            createGetForecastTool(),
            createGetAlertsTool()
        )
    }

    /**
     * Создать определение инструмента get_forecast.
     * Получает прогноз погоды по координатам (latitude, longitude).
     */
    private fun createGetForecastTool(): OpenAiTool {
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("latitude") {
                    put("type", "number")
                    put("description", "Широта местоположения (в градусах, от -90 до 90)")
                }
                putJsonObject("longitude") {
                    put("type", "number")
                    put("description", "Долгота местоположения (в градусах, от -180 до 180)")
                }
            }
            putJsonArray("required") {
                add("latitude")
                add("longitude")
            }
        }

        return OpenAiTool(
            type = "function",
            function = FunctionDefinition(
                name = "get_forecast",
                description = "Get weather forecast for a specific location using latitude and longitude coordinates. " +
                        "Returns a 7-day forecast with temperature, wind, and detailed conditions from weather.gov API. " +
                        "Use this when user asks about weather at specific coordinates or after geocoding a city name.",
                parameters = parameters
            )
        )
    }

    /**
     * Создать определение инструмента get_alerts.
     * Получает активные погодные предупреждения для штата США.
     */
    private fun createGetAlertsTool(): OpenAiTool {
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("state") {
                    put("type", "string")
                    put("description", "Двухбуквенный код штата США (например: CA, TX, NY, FL). " +
                            "Должен быть в верхнем регистре.")
                    put("pattern", "^[A-Z]{2}$")
                    putJsonArray("examples") {
                        add("CA")
                        add("TX")
                        add("NY")
                        add("FL")
                    }
                }
            }
            putJsonArray("required") {
                add("state")
            }
        }

        return OpenAiTool(
            type = "function",
            function = FunctionDefinition(
                name = "get_alerts",
                description = "Get active weather alerts for a US state. " +
                        "Returns severe weather warnings, watches, and advisories issued by the National Weather Service. " +
                        "Use this when user asks about weather alerts, warnings, or severe weather in a US state.",
                parameters = parameters
            )
        )
    }
}
