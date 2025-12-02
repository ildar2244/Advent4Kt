package com.example.tgbot.domain.repository

interface McpGitRepository {
    suspend fun getCurrentBranch(): String
}
