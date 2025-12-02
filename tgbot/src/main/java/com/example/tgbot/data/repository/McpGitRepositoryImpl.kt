package com.example.tgbot.data.repository

import com.example.tgbot.data.remote.McpWebSocketClient
import com.example.tgbot.domain.repository.McpGitRepository

class McpGitRepositoryImpl(
    private val mcpClient: McpWebSocketClient
) : McpGitRepository {
    override suspend fun getCurrentBranch(): String {
        return try {
            val result = mcpClient.callTool("get_current_branch", emptyMap())
            result.content.joinToString("\n") { it.text }
        } catch (e: Exception) {
            throw RuntimeException("Failed to get current git branch from MCP: ${e.message}", e)
        }
    }
}
