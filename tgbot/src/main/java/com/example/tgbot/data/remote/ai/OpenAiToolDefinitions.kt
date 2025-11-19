package com.example.tgbot.data.remote.ai

import com.example.tgbot.data.remote.dto.ai.openai.FunctionDefinition
import com.example.tgbot.data.remote.dto.ai.openai.OpenAiTool
import kotlinx.serialization.json.*

/**
 * Определения инструментов MCP для OpenAI Function Calling API.
 * Содержит описания функций get_forecast, get_alerts (Weather MCP)
 * и add_task, get_recent_tasks, get_tasks_count_today (Tasks MCP).
 */
object OpenAiToolDefinitions {

    /**
     * Получить список всех доступных MCP инструментов для OpenAI.
     * @return Список OpenAiTool с определениями Weather и Tasks MCP инструментов
     */
    fun getMcpTools(): List<OpenAiTool> {
        return listOf(
            // Weather MCP tools
            createGetForecastTool(),
            createGetAlertsTool(),
            // Tasks MCP tools
            createAddTaskTool(),
            createGetRecentTasksTool(),
            createGetTasksCountTodayTool()
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

    /**
     * Создать определение инструмента add_task.
     * Создает новую задачу с названием и описанием.
     */
    private fun createAddTaskTool(): OpenAiTool {
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Название задачи (краткое описание)")
                }
                putJsonObject("description") {
                    put("type", "string")
                    put("description", "Подробное описание задачи")
                }
            }
            putJsonArray("required") {
                add("title")
                add("description")
            }
        }

        return OpenAiTool(
            type = "function",
            function = FunctionDefinition(
                name = "add_task",
                description = "Create a new task with title and description. " +
                        "Tasks are stored in SQLite database with automatic timestamp. " +
                        "Use this when user wants to create, add, or save a new task.",
                parameters = parameters
            )
        )
    }

    /**
     * Создать определение инструмента get_recent_tasks.
     * Получает последние 3 задачи, созданные сегодня.
     */
    private fun createGetRecentTasksTool(): OpenAiTool {
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                // No parameters needed
            }
        }

        return OpenAiTool(
            type = "function",
            function = FunctionDefinition(
                name = "get_recent_tasks",
                description = "Get the last 3 tasks created today. " +
                        "Returns task details including ID, title, description, and creation time. " +
                        "Use this when user wants to see recent tasks, what was added today, or review today's tasks.",
                parameters = parameters
            )
        )
    }

    /**
     * Создать определение инструмента get_tasks_count_today.
     * Получает количество задач, созданных сегодня.
     */
    private fun createGetTasksCountTodayTool(): OpenAiTool {
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                // No parameters needed
            }
        }

        return OpenAiTool(
            type = "function",
            function = FunctionDefinition(
                name = "get_tasks_count_today",
                description = "Get the total count of tasks created today. " +
                        "Returns a simple number indicating how many tasks were created today. " +
                        "Use this when user asks how many tasks were created, task statistics for today, or productivity metrics.",
                parameters = parameters
            )
        )
    }
}
