package com.example.tgbot.data.remote

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.atomic.AtomicInteger

class TasksWebSocketClient(
    private val httpClient: HttpClient,
    private val wsUrl: String
) {
    private var session: DefaultClientWebSocketSession? = null
    private val requestIdCounter = AtomicInteger(1)
    private val responseChannel = Channel<String>(Channel.UNLIMITED)
    private var connectionJob: Job? = null

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    suspend fun connect() {
        if (session != null && connectionJob?.isActive == true) {
            return // Already connected
        }

        val connectionReady = CompletableDeferred<Unit>()

        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                httpClient.webSocket(wsUrl) {
                    session = this
                    println("[Tasks MCP Client] Connected to $wsUrl")

                    // Signal that connection is ready
                    connectionReady.complete(Unit)

                    try {
                        // Listen for incoming messages
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()

                                    // Handle ping/pong
                                    if (text.contains("\"type\"") && text.contains("\"ping\"")) {
                                        val pong = json.encodeToString(TasksPongMessage.serializer(), TasksPongMessage())
                                        send(Frame.Text(pong))
                                        continue
                                    }

                                    // Send response to channel for processing
                                    responseChannel.send(text)
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        println("[Tasks MCP Client] Error in message loop: ${e.message}")
                    } finally {
                        println("[Tasks MCP Client] WebSocket connection closed")
                        session = null
                    }
                }
            } catch (e: Exception) {
                println("[Tasks MCP Client] Connection error: ${e.message}")
                session = null
                connectionReady.completeExceptionally(e)
            }
        }

        // Wait for connection to be established with timeout
        try {
            withTimeout(5000) {
                connectionReady.await()
            }
        } catch (e: TimeoutCancellationException) {
            connectionJob?.cancel()
            throw IllegalStateException("Failed to establish WebSocket connection within 5 seconds")
        }
    }

    suspend fun callTool(name: String, arguments: Map<String, Any>): TasksToolCallResult {
        val requestId = requestIdCounter.getAndIncrement()

        val argumentsJson = JsonObject(arguments.mapValues { (_, value) ->
            when (value) {
                is Double -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        })

        val request = TasksJsonRpcRequest(
            method = "tools/call",
            params = JsonObject(mapOf(
                "name" to JsonPrimitive(name),
                "arguments" to argumentsJson
            )),
            id = JsonPrimitive(requestId)
        )

        val requestJson = json.encodeToString(TasksJsonRpcRequest.serializer(), request)

        val currentSession = session
            ?: throw IllegalStateException("WebSocket not connected. Call connect() first.")

        try {
            // Send request
            currentSession.send(Frame.Text(requestJson))

            // Wait for response from channel (with timeout)
            return withTimeout(30000) {
                while (true) {
                    val responseText = responseChannel.receive()

                    val response = json.decodeFromString<TasksJsonRpcResponse>(responseText)

                    // Check if this is the response to our request
                    if (response.id == request.id) {
                        if (response.error != null) {
                            throw RuntimeException("Tasks MCP Error: ${response.error.message}")
                        }

                        val result = response.result?.jsonObject
                            ?: throw IllegalStateException("No result in response")

                        return@withTimeout json.decodeFromJsonElement(TasksToolCallResult.serializer(), result)
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                throw IllegalStateException("Unreachable")
            }
        } catch (e: TimeoutCancellationException) {
            throw RuntimeException("Timeout waiting for Tasks MCP response")
        } catch (e: Exception) {
            println("[Tasks MCP Client] Error calling tool '$name': ${e.message}")
            throw e
        }
    }

    suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null

        session?.close()
        session = null

        responseChannel.close()
        println("[Tasks MCP Client] Disconnected")
    }

    fun isConnected(): Boolean {
        return session != null && connectionJob?.isActive == true
    }
}

// JSON-RPC DTOs для Tasks
@Serializable
data class TasksJsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonObject? = null,
    val id: JsonElement
)

@Serializable
data class TasksJsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: JsonElement? = null,
    val error: TasksJsonRpcError? = null,
    val id: JsonElement
)

@Serializable
data class TasksJsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class TasksToolCallResult(
    val content: List<TasksContentItem>
)

@Serializable
data class TasksContentItem(
    val type: String = "text",
    val text: String
)

@Serializable
data class TasksPongMessage(
    val type: String = "pong"
)
