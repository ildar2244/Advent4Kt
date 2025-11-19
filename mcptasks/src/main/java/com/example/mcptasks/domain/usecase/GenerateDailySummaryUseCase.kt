package com.example.mcptasks.domain.usecase

import com.example.mcptasks.data.remote.YandexGptApiClient
import com.example.mcptasks.domain.model.ai.AiMessage
import com.example.mcptasks.domain.model.ai.AiModel
import com.example.mcptasks.domain.model.ai.AiRequest
import com.example.mcptasks.domain.model.ai.MessageRole
import com.example.mcptasks.domain.repository.TasksRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Use case для генерации ежедневного summary задач через YandexGPT.
 *
 * @property tasksRepository Репозиторий задач для получения данных
 * @property yandexGptClient Клиент для взаимодействия с YandexGPT API
 */
class GenerateDailySummaryUseCase(
    private val tasksRepository: TasksRepository,
    private val yandexGptClient: YandexGptApiClient
) {
    /**
     * Генерирует summary за текущий день.
     *
     * @return Отформатированный текст summary для отправки в Telegram
     */
    suspend operator fun invoke(): String {
        val today = LocalDate.now()
        val todayFormatted = today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        // Получить количество задач за сегодня
        val tasksCount = tasksRepository.getTasksCountToday()

        // Получить последние задачи за сегодня
        val recentTasks = tasksRepository.getRecentTasksToday(limit = 100) // Получить все задачи за день

        // Сформировать промпт для YandexGPT
        val userPrompt = if (tasksCount > 0) {
            buildString {
                appendLine("Сегодня ($todayFormatted) было создано задач: $tasksCount")
                appendLine()
                appendLine("Список задач:")
                recentTasks.forEachIndexed { index, task ->
                    appendLine("${index + 1}. \"${task.title}\" - ${task.description}")
                }
                appendLine()
                appendLine("Проанализируй эти задачи и подготовь краткую аналитику:")
                appendLine("- Основные темы и категории задач")
                appendLine("- Приоритетные направления")
                appendLine("- Общий вывод и рекомендации на завтра")
                appendLine()
                appendLine("Ответ должен быть кратким, структурированным и мотивирующим (2-4 абзаца).")
            }
        } else {
            "Сегодня ($todayFormatted) задач не было создано. Подготовь короткое мотивационное сообщение (2-3 предложения) для продуктивного завтра."
        }

        val aiRequest = AiRequest(
            model = AiModel.YANDEX_GPT_LITE,
            messages = listOf(
                AiMessage(
                    role = MessageRole.SYSTEM,
                    content = "Ты - ассистент для анализа задач. Твоя задача - подготовить краткий и полезный дневной отчет на русском языке."
                ),
                AiMessage(
                    role = MessageRole.USER,
                    content = userPrompt
                )
            ),
            temperature = 0.7,
            maxTokens = 1000
        )

        val aiResponse = yandexGptClient.sendMessage(aiRequest)

        // Форматировать финальный summary
        return buildString {
            appendLine("📊 *Дневной отчет - $todayFormatted*")
            appendLine()
            appendLine("📌 *Задач создано:* $tasksCount")
            appendLine()
            appendLine("🤖 *Аналитика от YandexGPT:*")
            appendLine(aiResponse.content)
            appendLine()
            appendLine("---")
            appendLine("_Следующий отчет через 4 часа_")
        }
    }
}
