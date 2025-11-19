package com.example.mcptasks.domain.usecase

import com.example.mcptasks.domain.model.Task
import com.example.mcptasks.domain.repository.TasksRepository

/**
 * Use case для получения последних задач за текущий день
 *
 * @property repository Репозиторий задач
 */
class GetRecentTasksTodayUseCase(
    private val repository: TasksRepository
) {
    /**
     * Выполнить получение последних задач за текущий день
     *
     * @param limit Максимальное количество задач (по умолчанию 3)
     * @return Список последних задач за текущий день
     */
    suspend operator fun invoke(limit: Int = 3): List<Task> {
        require(limit > 0) { "Limit must be greater than 0" }

        return repository.getRecentTasksToday(limit)
    }
}
