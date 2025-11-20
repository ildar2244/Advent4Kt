package com.example.mcptasks.domain.usecase

import com.example.mcptasks.domain.model.Task
import com.example.mcptasks.domain.repository.TasksRepository

/**
 * Use case для поиска задач по ключевым словам
 *
 * @property repository Репозиторий задач
 */
class SearchTasksUseCase(
    private val repository: TasksRepository
) {
    /**
     * Выполнить поиск задач
     *
     * @param query Поисковый запрос
     * @return Список найденных задач за последние 7 дней
     */
    suspend operator fun invoke(query: String): List<Task> {
        require(query.isNotBlank()) { "Search query cannot be blank" }
        require(query.length >= 2) { "Search query must be at least 2 characters" }

        return repository.searchTasks(query)
    }
}
