package com.example.mcpgit.data.repository

import com.example.mcpgit.data.executor.GitCommandExecutor
import com.example.mcpgit.domain.repository.GitRepository

class GitRepositoryImpl(
    private val gitCommandExecutor: GitCommandExecutor
) : GitRepository {
    override suspend fun getCurrentBranch(): String {
        return try {
            gitCommandExecutor.getCurrentBranch()
        } catch (e: Exception) {
            throw RuntimeException("Failed to get current git branch: ${e.message}", e)
        }
    }
}
