package com.example.mcptasks.app

import com.example.mcptasks.BuildConfig
import com.example.mcptasks.data.local.DatabaseFactory
import com.example.mcptasks.data.repository.TasksRepositoryImpl
import com.example.mcptasks.data.remote.TelegramApiClient
import com.example.mcptasks.data.remote.YandexGptApiClient
import com.example.mcptasks.domain.usecase.AddTaskUseCase
import com.example.mcptasks.domain.usecase.GenerateDailySummaryUseCase
import com.example.mcptasks.domain.usecase.GetRecentTasksTodayUseCase
import com.example.mcptasks.domain.usecase.GetTasksCountTodayUseCase
import com.example.mcptasks.domain.usecase.SearchTasksUseCase
import com.example.mcptasks.domain.usecase.SendTasksToTelegramUseCase
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.format.DateTimeFormatter
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

fun main(args: Array<String>) {
    runBlocking {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }

        startServer(json)
    }
}

suspend fun startServer(json: Json) {

    // Initialize HTTP Client for YandexGPT and Telegram
    val httpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    // Initialize SQLite database
    val dbPath = BuildConfig.MCP_TASKS_DB_PATH
    println("Initializing database at: $dbPath")
    DatabaseFactory.init(dbPath)

    // Initialize repository and use cases
    val tasksRepository = TasksRepositoryImpl()
    val addTaskUseCase = AddTaskUseCase(tasksRepository)
    val getRecentTasksTodayUseCase = GetRecentTasksTodayUseCase(tasksRepository)
    val getTasksCountTodayUseCase = GetTasksCountTodayUseCase(tasksRepository)
    val searchTasksUseCase = SearchTasksUseCase(tasksRepository)

    // Initialize YandexGPT API Client
    val yandexGptClient = YandexGptApiClient(
        client = httpClient,
        apiKey = BuildConfig.YANDEX_GPT_API_KEY,
        folderId = BuildConfig.YANDEX_CLOUD_FOLDER_ID
    )

    // Initialize Telegram API Client
    val telegramClient = TelegramApiClient(
        client = httpClient,
        botToken = BuildConfig.TELEGRAM_BOT_TOKEN
    )

    // Initialize Send Tasks to Telegram Use Case
    val sendTasksToTelegramUseCase = SendTasksToTelegramUseCase(
        repository = tasksRepository,
        telegramClient = telegramClient,
        channelChatId = BuildConfig.TELEGRAM_CHANNEL_CHAT_ID
    )

    // Initialize Daily Summary Use Case
    val generateSummaryUseCase = GenerateDailySummaryUseCase(
        tasksRepository = tasksRepository,
        yandexGptClient = yandexGptClient
    )

    // Initialize Daily Summary Scheduler
    val summaryScheduler = DailySummaryScheduler(
        generateSummaryUseCase = generateSummaryUseCase,
        telegramClient = telegramClient,
        channelChatId = BuildConfig.TELEGRAM_CHANNEL_CHAT_ID
    )

    val host = BuildConfig.MCP_TASKS_WS_HOST
    val port = BuildConfig.MCP_TASKS_WS_PORT

    println("Starting MCP Tasks WebSocket Server on ws://$host:$port/mcp")

    // Start Daily Summary Scheduler in background
    GlobalScope.launch {
        summaryScheduler.start()
    }

    // Start WebSocket server
    embeddedServer(CIO, port = port, host = host) {
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
                                        addTaskUseCase,
                                        getRecentTasksTodayUseCase,
                                        getTasksCountTodayUseCase,
                                        searchTasksUseCase,
                                        sendTasksToTelegramUseCase,
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
    addTaskUseCase: AddTaskUseCase,
    getRecentTasksTodayUseCase: GetRecentTasksTodayUseCase,
    getTasksCountTodayUseCase: GetTasksCountTodayUseCase,
    searchTasksUseCase: SearchTasksUseCase,
    sendTasksToTelegramUseCase: SendTasksToTelegramUseCase,
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
                "add_task" -> handleAddTask(params.arguments, addTaskUseCase, request.id)
                "get_recent_tasks" -> handleGetRecentTasks(getRecentTasksTodayUseCase, request.id)
                "get_tasks_count_today" -> handleGetTasksCountToday(getTasksCountTodayUseCase, request.id)
                "search_tasks" -> handleSearchTasks(params.arguments, searchTasksUseCase, request.id)
                "send_tasks_to_telegram" -> handleSendTasksToTelegram(params.arguments, sendTasksToTelegramUseCase, request.id)
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

suspend fun handleAddTask(
    arguments: JsonObject,
    addTaskUseCase: AddTaskUseCase,
    requestId: JsonElement
): JsonRpcResponse {
    return try {
        val title = arguments["title"]?.jsonPrimitive?.contentOrNull
            ?: return JsonRpcResponse(
                error = JsonRpcError(code = -32602, message = "Missing or invalid title"),
                id = requestId
            )
        val description = arguments["description"]?.jsonPrimitive?.contentOrNull
            ?: return JsonRpcResponse(
                error = JsonRpcError(code = -32602, message = "Missing or invalid description"),
                id = requestId
            )

        val task = addTaskUseCase(title, description)

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val responseText = buildString {
            appendLine("✅ Задача успешно создана!")
            appendLine()
            appendLine("ID: ${task.id}")
            appendLine("Название: ${task.title}")
            appendLine("Описание: ${task.description}")
            appendLine("Создано: ${task.createdAt.format(formatter)}")
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
            error = JsonRpcError(code = -32603, message = "Error adding task: ${e.message}"),
            id = requestId
        )
    }
}

suspend fun handleGetRecentTasks(
    getRecentTasksTodayUseCase: GetRecentTasksTodayUseCase,
    requestId: JsonElement
): JsonRpcResponse {
    return try {
        val tasks = getRecentTasksTodayUseCase(limit = 3)

        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val responseText = if (tasks.isEmpty()) {
            "📭 Нет задач, созданных сегодня"
        } else {
            buildString {
                appendLine("📋 Последние ${tasks.size} задач за сегодня:")
                appendLine()
                tasks.forEachIndexed { index, task ->
                    appendLine("${index + 1}. ${task.title}")
                    appendLine("   ID: ${task.id}")
                    appendLine("   Описание: ${task.description}")
                    appendLine("   Создано: ${task.createdAt.format(formatter)}")
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
            error = JsonRpcError(code = -32603, message = "Error getting recent tasks: ${e.message}"),
            id = requestId
        )
    }
}

suspend fun handleGetTasksCountToday(
    getTasksCountTodayUseCase: GetTasksCountTodayUseCase,
    requestId: JsonElement
): JsonRpcResponse {
    return try {
        val count = getTasksCountTodayUseCase()

        val responseText = "📊 Количество задач за сегодня: $count"

        val result = ToolCallResult(
            content = listOf(ContentItem(text = responseText))
        )

        JsonRpcResponse(
            result = Json.encodeToJsonElement(ToolCallResult.serializer(), result),
            id = requestId
        )
    } catch (e: Exception) {
        JsonRpcResponse(
            error = JsonRpcError(code = -32603, message = "Error getting tasks count: ${e.message}"),
            id = requestId
        )
    }
}

suspend fun handleSearchTasks(
    arguments: JsonObject,
    searchTasksUseCase: SearchTasksUseCase,
    requestId: JsonElement
): JsonRpcResponse {
    return try {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: return JsonRpcResponse(
                error = JsonRpcError(code = -32602, message = "Missing or invalid query"),
                id = requestId
            )

        val tasks = searchTasksUseCase(query)

        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val responseText = if (tasks.isEmpty()) {
            "🔍 По запросу \"$query\" задач не найдено (за последние 7 дней)"
        } else {
            buildString {
                appendLine("🔍 Найдено задач по запросу \"$query\": ${tasks.size}")
                appendLine()
                tasks.forEachIndexed { index, task ->
                    appendLine("${index + 1}. ${task.title}")
                    appendLine("   ID: ${task.id}")
                    appendLine("   Описание: ${task.description}")
                    appendLine("   Создано: ${task.createdAt.format(formatter)}")
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
            error = JsonRpcError(code = -32603, message = "Error searching tasks: ${e.message}"),
            id = requestId
        )
    }
}

suspend fun handleSendTasksToTelegram(
    arguments: JsonObject,
    sendTasksToTelegramUseCase: SendTasksToTelegramUseCase,
    requestId: JsonElement
): JsonRpcResponse {
    return try {
        val taskIdsArray = arguments["task_ids"]?.jsonPrimitive?.contentOrNull
            ?: return JsonRpcResponse(
                error = JsonRpcError(code = -32602, message = "Missing or invalid task_ids"),
                id = requestId
            )

        // Parse comma-separated IDs or JSON array format
        val taskIds = try {
            taskIdsArray.split(",").map { it.trim().toLong() }
        } catch (e: Exception) {
            return JsonRpcResponse(
                error = JsonRpcError(code = -32602, message = "Invalid task_ids format. Use comma-separated numbers (e.g., '1,2,3')"),
                id = requestId
            )
        }

        val resultMessage = sendTasksToTelegramUseCase(taskIds)

        val result = ToolCallResult(
            content = listOf(ContentItem(text = resultMessage))
        )

        JsonRpcResponse(
            result = Json.encodeToJsonElement(ToolCallResult.serializer(), result),
            id = requestId
        )
    } catch (e: Exception) {
        JsonRpcResponse(
            error = JsonRpcError(code = -32603, message = "Error sending tasks to Telegram: ${e.message}"),
            id = requestId
        )
    }
}
