package com.example.mcpgit.data.executor

import com.example.mcpgit.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GitCommandExecutor {
    private val workingDirectory = File(BuildConfig.GIT_REPO_PATH)

    suspend fun executeGitCommand(vararg command: String): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(*command)
            .directory(workingDirectory)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Git command failed (exit code $exitCode): $output")
        }

        output
    }

    suspend fun getCurrentBranch(): String {
        return executeGitCommand("git", "branch", "--show-current")
    }
}
