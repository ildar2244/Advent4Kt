package com.example.mcpgit.app

import com.example.mcpgit.BuildConfig
import com.example.mcpgit.data.executor.GitCommandExecutor
import com.example.mcpgit.data.repository.GitRepositoryImpl
import com.example.mcpgit.domain.usecase.GetCurrentBranchUseCase
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
import kotlin.time.Duration.Companion.seconds

// JSON-RPC DTOs (переиспользуем паттерн из mcpweather)
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

    // Initialize Git command executor
    val gitCommandExecutor = GitCommandExecutor()
    val gitRepository = GitRepositoryImpl(gitCommandExecutor)

    // Initialize use case
    val getCurrentBranchUseCase = GetCurrentBranchUseCase(gitRepository)

    val host = BuildConfig.MCP_GIT_WS_HOST
    val port = BuildConfig.MCP_GIT_WS_PORT

    println("Starting MCP Git WebSocket Server on ws://$host:$port/mcp")
    println("Git repository path: ${BuildConfig.GIT_REPO_PATH}")

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
                                        getCurrentBranchUseCase,
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
    getCurrentBranchUseCase: GetCurrentBranchUseCase,
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
                "get_current_branch" -> handleGetCurrentBranch(getCurrentBranchUseCase, request.id)
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

suspend fun handleGetCurrentBranch(
    getCurrentBranchUseCase: GetCurrentBranchUseCase,
    requestId: JsonElement
): JsonRpcResponse {
    return try {
        val branchName = getCurrentBranchUseCase()

        val responseText = if (branchName.isEmpty()) {
            "detached HEAD"
        } else {
            branchName
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
            error = JsonRpcError(code = -32603, message = "Error getting current branch: ${e.message}"),
            id = requestId
        )
    }
}
