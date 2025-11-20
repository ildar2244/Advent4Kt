package com.example.mcptasks.domain.usecase

import com.example.mcptasks.data.remote.TelegramApiClient
import com.example.mcptasks.domain.repository.TasksRepository
import java.time.format.DateTimeFormatter

/**
 * Use case для отправки задач в Telegram канал
 *
 * @property repository Репозиторий задач
 * @property telegramClient Клиент для отправки сообщений в Telegram
 * @property channelChatId Идентификатор Telegram канала
 */
class SendTasksToTelegramUseCase(
    private val repository: TasksRepository,
    private val telegramClient: TelegramApiClient,
    private val channelChatId: String
) {
    /**
     * Отправить задачи в Telegram канал
     *
     * @param taskIds Список ID задач для отправки
     * @return Результат отправки
     */
    suspend operator fun invoke(taskIds: List<Long>): String {
        require(taskIds.isNotEmpty()) { "Task IDs list cannot be empty" }

        // Получить задачи по ID
        val tasks = repository.getTasksByIds(taskIds)

        if (tasks.isEmpty()) {
            return "⚠️ Задачи с указанными ID не найдены"
        }

        // Форматировать сообщение
        val message = buildString {
            appendLine("📋 *Найденные задачи* (${tasks.size})")
            appendLine()

            tasks.forEachIndexed { index, task ->
                appendLine("*${index + 1}. ${task.title}*")
                appendLine("   ID: `${task.id}`")
                appendLine("   ${task.description}")
                appendLine("   📅 ${task.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}")
                if (index < tasks.size - 1) {
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
            }
        }

        // Отправить в Telegram
        return try {
            val response = telegramClient.sendMessage(
                chatId = channelChatId,
                text = message,
                parseMode = "Markdown"
            )

            if (response.ok) {
                "✅ Задачи успешно отправлены в Telegram канал (${tasks.size} шт.)"
            } else {
                "❌ Ошибка отправки в Telegram: ${response.description}"
            }
        } catch (e: Exception) {
            "❌ Ошибка отправки в Telegram: ${e.message}"
        }
    }
}
