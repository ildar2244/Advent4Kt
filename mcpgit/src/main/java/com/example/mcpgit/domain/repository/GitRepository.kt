package com.example.mcpgit.domain.repository

interface GitRepository {
    suspend fun getCurrentBranch(): String
}
