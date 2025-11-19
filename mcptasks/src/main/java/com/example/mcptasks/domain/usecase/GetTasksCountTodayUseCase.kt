package com.example.mcptasks.domain.usecase

import com.example.mcptasks.domain.repository.TasksRepository

/**
 * Use case для получения количества задач за текущий день
 *
 * @property repository Репозиторий задач
 */
class GetTasksCountTodayUseCase(
    private val repository: TasksRepository
) {
    /**
     * Выполнить получение количества задач за текущий день
     *
     * @return Количество задач, созданных сегодня
     */
    suspend operator fun invoke(): Int {
        return repository.getTasksCountToday()
    }
}
