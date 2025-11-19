package com.example.mcptasks.domain.repository

import com.example.mcptasks.domain.model.Task

/**
 * Интерфейс репозитория для работы с задачами
 */
interface TasksRepository {

    /**
     * Создать новую задачу
     *
     * @param title Название задачи
     * @param description Описание задачи
     * @return Созданная задача с присвоенным ID
     */
    suspend fun createTask(title: String, description: String): Task

    /**
     * Получить последние задачи за текущий день
     *
     * @param limit Максимальное количество задач (по умолчанию 3)
     * @return Список задач за текущий день, отсортированных по времени создания (новые первыми)
     */
    suspend fun getRecentTasksToday(limit: Int = 3): List<Task>

    /**
     * Получить количество задач за текущий день
     *
     * @return Количество задач, созданных сегодня
     */
    suspend fun getTasksCountToday(): Int
}
