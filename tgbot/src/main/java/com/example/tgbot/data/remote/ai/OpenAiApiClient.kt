package com.example.tgbot.data.remote.ai

import com.example.tgbot.data.remote.dto.ai.mapper.toDomain
import com.example.tgbot.data.remote.dto.ai.mapper.toOpenAiDto
import com.example.tgbot.data.remote.dto.ai.openai.OpenAiChatRequest
import com.example.tgbot.data.remote.dto.ai.openai.OpenAiChatResponse
import com.example.tgbot.data.remote.dto.ai.openai.OpenAiMessageDto
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.AiResponse
import com.example.tgbot.domain.repository.GeocodingRepository
import com.example.tgbot.domain.repository.McpRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * HTTP-клиент для работы с OpenAI API через ProxyAPI.
 *
 * Использует ProxyAPI (api.proxyapi.ru) в качестве промежуточного сервера
 * для доступа к OpenAI Chat Completions API.
 *
 * Поддерживает OpenAI Function Calling для интеграции с MCP инструментами (get_forecast, get_alerts, add_task, get_recent_tasks, get_tasks_count_today).
 *
 * @property client Настроенный HTTP-клиент Ktor
 * @property apiKey API-ключ для ProxyAPI (OPENAI_API_KEY)
 * @property mcpRepository Repository для вызова MCP инструментов (weather tools)
 * @property tasksRepository Repository для вызова Tasks MCP инструментов
 * @property geocodingRepository Repository для геокодинга (конвертация городов в координаты)
 */
class OpenAiApiClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val mcpRepository: McpRepository,
    private val tasksRepository: com.example.tgbot.domain.repository.TasksRepository,
    private val geocodingRepository: GeocodingRepository
) : AiApiClient {

    private val logger = LoggerFactory.getLogger(OpenAiApiClient::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true // Важно: включаем сериализацию default values (например, type = "function")
    }

    companion object {
        private const val MAX_TOOL_ITERATIONS = 5 // Максимальное количество итераций tool calls
    }

    /**
     * Отправляет запрос к OpenAI Chat Completions API с поддержкой Function Calling.
     *
     * Процесс обработки:
     * 1. Отправить запрос с tools (get_forecast, get_alerts)
     * 2. Если AI вернула tool_calls -> выполнить их через MCP
     * 3. Отправить результаты обратно в AI
     * 4. Получить финальный ответ пользователю
     *
     * Аутентификация: используется заголовок "Authorization: Bearer {apiKey}"
     *
     * @param request Доменная модель запроса
     * @return Доменная модель ответа с сгенерированным текстом
     */
    override suspend fun sendMessage(request: AiRequest): AiResponse {
        val startTime = System.currentTimeMillis()
        var requestDto = request.toOpenAiDto()

        // Добавляем MCP tools в запрос
        requestDto = requestDto.copy(
            tools = OpenAiToolDefinitions.getMcpTools(),
            toolChoice = "auto" // AI сама решает когда вызывать инструменты
        )

        logger.info("Sending OpenAI request with tools: ${requestDto.tools?.map { it.function.name }}")

        var currentMessages = requestDto.messages.toMutableList()
        var iterationCount = 0

        while (iterationCount < MAX_TOOL_ITERATIONS) {
            iterationCount++

            // Отправляем запрос к OpenAI
            val requestBody = OpenAiChatRequest(
                model = requestDto.model,
                messages = currentMessages,
                temperature = requestDto.temperature,
                tools = requestDto.tools,
                toolChoice = requestDto.toolChoice
            )

            logger.info("Sending request to OpenAI (iteration $iterationCount)")

            // Отладочный вывод тела запроса
            try {
                val requestJson = json.encodeToString(OpenAiChatRequest.serializer(), requestBody)
                logger.info("Request JSON: $requestJson")
            } catch (e: Exception) {
                logger.error("Failed to serialize request: ${e.message}", e)
            }

            val response: OpenAiChatResponse = try {
                val httpResponse = client.post(request.model.endpoint) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(requestBody)
                }

                // Логируем статус код
                logger.info("OpenAI response status: ${httpResponse.status}")

                // Получаем тело ответа как строку для отладки
                val responseText = httpResponse.body<String>()
                logger.info("OpenAI raw response: $responseText")

                // Если ошибка - выводим и бросаем исключение
                if (httpResponse.status.value >= 400) {
                    logger.error("OpenAI API error (${httpResponse.status.value}): $responseText")
                    throw IllegalStateException("OpenAI API returned error ${httpResponse.status.value}: $responseText")
                }

                // Парсим в объект
                json.decodeFromString<OpenAiChatResponse>(responseText)
            } catch (e: Exception) {
                logger.error("Failed to parse OpenAI response: ${e.message}", e)
                throw e
            }

            val responseMessage = response.choices.firstOrNull()?.message
            val finishReason = response.choices.firstOrNull()?.finishReason

            logger.info("OpenAI response finish_reason: $finishReason, has_tool_calls: ${responseMessage?.toolCalls != null}")

            // Если нет tool_calls или finish_reason == "stop", возвращаем финальный ответ
            if (responseMessage?.toolCalls.isNullOrEmpty() || finishReason == "stop") {
                val responseTimeMillis = System.currentTimeMillis() - startTime
                return response.toDomain(request, responseTimeMillis)
            }

            // Обрабатываем tool calls
            val toolCalls = responseMessage?.toolCalls ?: emptyList()
            logger.info("Processing ${toolCalls.size} tool call(s)")

            // Добавляем сообщение ассистента с tool_calls в историю
            currentMessages.add(
                OpenAiMessageDto(
                    role = "assistant",
                    content = null,
                    toolCalls = toolCalls
                )
            )

            // Выполняем каждый tool call и добавляем результаты
            for (toolCall in toolCalls) {
                val toolResult = executeToolCall(
                    toolName = toolCall.function.name,
                    argumentsJson = toolCall.function.arguments
                )

                currentMessages.add(
                    OpenAiMessageDto(
                        role = "tool",
                        content = toolResult,
                        toolCallId = toolCall.id,
                        name = toolCall.function.name
                    )
                )

                logger.info("Tool '${toolCall.function.name}' executed, result length: ${toolResult.length}")
            }
        }

        // Если достигли лимита итераций
        logger.warn("Reached max tool iterations ($MAX_TOOL_ITERATIONS), returning last response")
        val responseTimeMillis = System.currentTimeMillis() - startTime

        // Создаем финальный запрос без tools для получения ответа
        val finalRequestBody = OpenAiChatRequest(
            model = requestDto.model,
            messages = currentMessages,
            temperature = requestDto.temperature
        )

        logger.info("Sending final request to OpenAI (without tools)")
        logger.debug("Final request body: $finalRequestBody")

        val finalResponse: OpenAiChatResponse = try {
            val httpResponse = client.post(request.model.endpoint) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(finalRequestBody)
            }

            logger.info("Final OpenAI response status: ${httpResponse.status}")
            val responseText = httpResponse.body<String>()
            logger.info("Final OpenAI raw response: $responseText")

            if (httpResponse.status.value >= 400) {
                logger.error("Final OpenAI API error (${httpResponse.status.value}): $responseText")
                throw IllegalStateException("OpenAI API returned error ${httpResponse.status.value}: $responseText")
            }

            json.decodeFromString<OpenAiChatResponse>(responseText)
        } catch (e: Exception) {
            logger.error("Failed to parse final OpenAI response: ${e.message}", e)
            throw e
        }

        return finalResponse.toDomain(request, responseTimeMillis)
    }

    /**
     * Выполнить вызов MCP инструмента.
     *
     * @param toolName Имя инструмента (get_forecast или get_alerts)
     * @param argumentsJson JSON-строка с аргументами
     * @return Результат выполнения инструмента (текст прогноза или alerts)
     */
    private suspend fun executeToolCall(toolName: String, argumentsJson: String): String {
        return try {
            logger.info("Executing tool: $toolName with args: $argumentsJson")

            when (toolName) {
                "get_forecast" -> {
                    val args = json.parseToJsonElement(argumentsJson).jsonObject
                    val latitude = args["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    val longitude = args["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()

                    if (latitude == null || longitude == null) {
                        "Error: Invalid coordinates. Latitude and longitude must be valid numbers."
                    } else {
                        mcpRepository.getForecast(latitude, longitude)
                    }
                }

                "get_alerts" -> {
                    val args = json.parseToJsonElement(argumentsJson).jsonObject
                    val state = args["state"]?.jsonPrimitive?.content

                    if (state.isNullOrBlank()) {
                        "Error: State code is required (2-letter US state code, e.g., 'CA', 'TX')."
                    } else {
                        mcpRepository.getAlerts(state.uppercase())
                    }
                }

                "add_task" -> {
                    val args = json.parseToJsonElement(argumentsJson).jsonObject
                    val title = args["title"]?.jsonPrimitive?.content
                    val description = args["description"]?.jsonPrimitive?.content

                    if (title.isNullOrBlank() || description.isNullOrBlank()) {
                        "Error: Both 'title' and 'description' are required to create a task."
                    } else {
                        tasksRepository.addTask(title, description)
                    }
                }

                "get_recent_tasks" -> {
                    tasksRepository.getRecentTasks()
                }

                "get_tasks_count_today" -> {
                    tasksRepository.getTasksCountToday()
                }

                else -> {
                    logger.warn("Unknown tool: $toolName")
                    "Error: Unknown tool '$toolName'"
                }
            }
        } catch (e: Exception) {
            logger.error("Tool execution error for '$toolName': ${e.message}", e)
            "Error executing tool '$toolName': ${e.message}"
        }
    }
}