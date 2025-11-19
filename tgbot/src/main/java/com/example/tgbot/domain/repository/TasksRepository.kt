package com.example.tgbot.domain.repository

interface TasksRepository {
    /**
     * Get list of available Tasks MCP tools
     * @return Map of tool name to tool description
     */
    fun getAvailableTools(): Map<String, String>

    /**
     * Add a new task
     * @param title Task title
     * @param description Task description
     * @return Formatted response with created task details
     */
    suspend fun addTask(title: String, description: String): String

    /**
     * Get recent tasks created today (last 3)
     * @return Formatted list of recent tasks
     */
    suspend fun getRecentTasks(): String

    /**
     * Get count of tasks created today
     * @return Formatted count of tasks
     */
    suspend fun getTasksCountToday(): String
}
