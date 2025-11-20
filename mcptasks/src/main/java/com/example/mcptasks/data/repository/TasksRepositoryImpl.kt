package com.example.mcptasks.data.repository

import com.example.mcptasks.data.local.DatabaseFactory.dbQuery
import com.example.mcptasks.data.local.TasksTable
import com.example.mcptasks.domain.model.Task
import com.example.mcptasks.domain.repository.TasksRepository
import org.jetbrains.exposed.sql.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Реализация репозитория задач с использованием SQLite через Exposed ORM
 */
class TasksRepositoryImpl : TasksRepository {

    /**
     * Создать новую задачу
     */
    override suspend fun createTask(title: String, description: String): Task = dbQuery {
        val insertStatement = TasksTable.insert {
            it[TasksTable.title] = title
            it[TasksTable.description] = description
            it[TasksTable.createdAt] = LocalDateTime.now()
        }

        val resultRow = insertStatement.resultedValues?.firstOrNull()
            ?: throw IllegalStateException("Failed to create task")

        resultRow.toTask()
    }

    /**
     * Получить последние задачи за текущий день
     */
    override suspend fun getRecentTasksToday(limit: Int): List<Task> = dbQuery {
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay()
        val endOfDay = today.plusDays(1).atStartOfDay()

        TasksTable
            .selectAll()
            .where {
                (TasksTable.createdAt greaterEq startOfDay) and
                        (TasksTable.createdAt less endOfDay)
            }
            .orderBy(TasksTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toTask() }
    }

    /**
     * Получить количество задач за текущий день
     */
    override suspend fun getTasksCountToday(): Int = dbQuery {
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay()
        val endOfDay = today.plusDays(1).atStartOfDay()

        TasksTable
            .selectAll()
            .where {
                (TasksTable.createdAt greaterEq startOfDay) and
                        (TasksTable.createdAt less endOfDay)
            }
            .count()
            .toInt()
    }

    /**
     * Поиск задач по ключевым словам за последние 7 дней
     */
    override suspend fun searchTasks(query: String): List<Task> = dbQuery {
        val sevenDaysAgo = LocalDateTime.now().minusDays(7)
        val queryLowerCase = query.lowercase()

        TasksTable
            .selectAll()
            .where {
                (TasksTable.createdAt greaterEq sevenDaysAgo) and
                        ((LowerCase(TasksTable.title) like "%$queryLowerCase%") or
                         (LowerCase(TasksTable.description) like "%$queryLowerCase%"))
            }
            .orderBy(TasksTable.createdAt, SortOrder.DESC)
            .map { it.toTask() }
    }

    /**
     * Получить задачи по списку ID
     */
    override suspend fun getTasksByIds(ids: List<Long>): List<Task> = dbQuery {
        if (ids.isEmpty()) {
            return@dbQuery emptyList()
        }

        TasksTable
            .selectAll()
            .where { TasksTable.id inList ids }
            .orderBy(TasksTable.createdAt, SortOrder.DESC)
            .map { it.toTask() }
    }

    /**
     * Маппер ResultRow -> Task
     */
    private fun ResultRow.toTask() = Task(
        id = this[TasksTable.id],
        title = this[TasksTable.title],
        description = this[TasksTable.description],
        createdAt = this[TasksTable.createdAt]
    )
}
