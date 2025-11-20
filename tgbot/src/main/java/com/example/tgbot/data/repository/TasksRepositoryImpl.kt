package com.example.tgbot.data.repository

import com.example.tgbot.data.remote.TasksWebSocketClient
import com.example.tgbot.domain.repository.TasksRepository

class TasksRepositoryImpl(
    private val tasksClient: TasksWebSocketClient
) : TasksRepository {

    override fun getAvailableTools(): Map<String, String> {
        return mapOf(
            "add_task" to "Create a new task with title and description. Tasks are stored in SQLite database.",
            "get_recent_tasks" to "Get the last 3 tasks created today. Returns task details including ID, title, description, and creation time.",
            "get_tasks_count_today" to "Get the total count of tasks created today.",
            "search_tasks" to "Search tasks by keywords in title and description (last 7 days). Returns list of matching tasks with IDs.",
            "send_tasks_to_telegram" to "Send specified tasks to Telegram channel. Requires task IDs as parameter."
        )
    }

    override suspend fun addTask(title: String, description: String): String {
        val result = tasksClient.callTool(
            name = "add_task",
            arguments = mapOf(
                "title" to title,
                "description" to description
            )
        )

        // Extract text from content items
        return result.content.joinToString("\n") { it.text }
    }

    override suspend fun getRecentTasks(): String {
        val result = tasksClient.callTool(
            name = "get_recent_tasks",
            arguments = emptyMap()
        )

        // Extract text from content items
        return result.content.joinToString("\n") { it.text }
    }

    override suspend fun getTasksCountToday(): String {
        val result = tasksClient.callTool(
            name = "get_tasks_count_today",
            arguments = emptyMap()
        )

        // Extract text from content items
        return result.content.joinToString("\n") { it.text }
    }

    override suspend fun searchTasks(query: String): String {
        val result = tasksClient.callTool(
            name = "search_tasks",
            arguments = mapOf("query" to query)
        )

        // Extract text from content items
        return result.content.joinToString("\n") { it.text }
    }

    override suspend fun sendTasksToTelegram(taskIds: List<Long>): String {
        val result = tasksClient.callTool(
            name = "send_tasks_to_telegram",
            arguments = mapOf("task_ids" to taskIds.joinToString(","))
        )

        // Extract text from content items
        return result.content.joinToString("\n") { it.text }
    }
}
