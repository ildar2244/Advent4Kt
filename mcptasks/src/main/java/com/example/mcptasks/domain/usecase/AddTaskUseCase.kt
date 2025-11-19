package com.example.mcptasks.domain.usecase

import com.example.mcptasks.domain.model.Task
import com.example.mcptasks.domain.repository.TasksRepository

/**
 * Use case для добавления новой задачи
 *
 * @property repository Репозиторий задач
 */
class AddTaskUseCase(
    private val repository: TasksRepository
) {
    /**
     * Выполнить создание задачи
     *
     * @param title Название задачи
     * @param description Описание задачи
     * @return Созданная задача
     */
    suspend operator fun invoke(title: String, description: String): Task {
        require(title.isNotBlank()) { "Task title cannot be blank" }
        require(description.isNotBlank()) { "Task description cannot be blank" }

        return repository.createTask(title, description)
    }
}
