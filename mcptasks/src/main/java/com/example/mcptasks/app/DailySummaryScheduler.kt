package com.example.mcptasks.app

import com.example.mcptasks.data.remote.TelegramApiClient
import com.example.mcptasks.domain.usecase.GenerateDailySummaryUseCase
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Планировщик для автоматической генерации и отправки summary каждые 4 часа.
 *
 * Интервалы отправки: 00:00, 04:00, 08:00, 12:00, 16:00, 20:00 (от начала суток).
 *
 * @property generateSummaryUseCase Use case для генерации summary
 * @property telegramClient Клиент для отправки сообщений в Telegram
 * @property channelChatId Идентификатор Telegram канала для отправки
 */
class DailySummaryScheduler(
    private val generateSummaryUseCase: GenerateDailySummaryUseCase,
    private val telegramClient: TelegramApiClient,
    private val channelChatId: String
) {
    @Volatile
    private var isRunning = false

    /**
     * Интервалы запуска в часах от начала суток.
     */
    private val scheduleHours = listOf(0, 4, 8, 12, 16, 20)

    companion object {
        /**
         * Режим отладки: если true, первая отправка summary произойдет через DEBUG_FIRST_DELAY_MINUTES минут.
         * После первой отправки планировщик продолжит работать по обычному расписанию.
         */
        private const val DEBUG_MODE = false

        /**
         * Задержка в минутах для первой отладочной отправки (используется только при DEBUG_MODE = true).
         */
        private const val DEBUG_FIRST_DELAY_MINUTES = 5L
    }

    /**
     * Запускает планировщик.
     */
    suspend fun start() {
        isRunning = true
        println("📅 Daily Summary Scheduler started")
        println("   Schedule: 00:00, 04:00, 08:00, 12:00, 16:00, 20:00")
        println("   Channel ID: $channelChatId")

        // Режим отладки: отправить первый summary через 5 минут
        if (DEBUG_MODE) {
            println("🐛 DEBUG MODE: First summary will be sent in $DEBUG_FIRST_DELAY_MINUTES minutes")
            val debugDelayMs = DEBUG_FIRST_DELAY_MINUTES * 60 * 1000
            delay(debugDelayMs)

            if (!isRunning) return

            println("🐛 DEBUG MODE: Sending first debug summary...")
            executeSummaryGeneration()
        }

        while (isRunning) {
            try {
                // Вычислить задержку до следующего запуска
                val delayMs = calculateDelayToNextExecution()
                val nextExecutionTime = LocalDateTime.now().plusNanos(delayMs * 1_000_000)

                println("⏰ Next summary will be sent at: ${nextExecutionTime.toLocalTime()}")

                // Ожидание до следующего выполнения
                delay(delayMs)

                if (!isRunning) break

                // Выполнить генерацию и отправку summary
                executeSummaryGeneration()

            } catch (e: Exception) {
                println("❌ Error in DailySummaryScheduler: ${e.message}")
                e.printStackTrace()
                println("   Retrying in 1 minute...")
                delay(60_000) // Retry через 1 минуту при ошибке
            }
        }

        println("📅 Daily Summary Scheduler stopped")
    }

    /**
     * Останавливает планировщик.
     */
    fun stop() {
        isRunning = false
    }

    /**
     * Выполняет генерацию summary и отправку в Telegram канал.
     */
    private suspend fun executeSummaryGeneration() {
        try {
            println("🔄 Generating daily summary...")

            // Генерация summary через YandexGPT
            val summary = generateSummaryUseCase()

            // Отправка в Telegram канал
            val response = telegramClient.sendMessage(
                chatId = channelChatId,
                text = summary,
                parseMode = "Markdown"
            )

            if (response.ok) {
                println("✅ Summary sent to Telegram channel successfully!")
            } else {
                println("⚠️ Failed to send summary: ${response.description}")
            }

        } catch (e: Exception) {
            println("❌ Error generating or sending summary: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Вычисляет задержку в миллисекундах до следующего запланированного выполнения.
     *
     * @return Задержка в миллисекундах
     */
    private fun calculateDelayToNextExecution(): Long {
        val now = LocalDateTime.now()
        val currentHour = now.hour

        // Найти следующий час из расписания
        val nextHour = scheduleHours.firstOrNull { it > currentHour }

        // Вычислить следующее время выполнения
        val nextExecutionTime = if (nextHour != null) {
            // Найден час сегодня
            now.withHour(nextHour).withMinute(0).withSecond(0).withNano(0)
        } else {
            // Все часы прошли, берем первый час завтра
            now.plusDays(1).withHour(scheduleHours.first()).withMinute(0).withSecond(0).withNano(0)
        }

        val duration = Duration.between(now, nextExecutionTime)
        return duration.toMillis().coerceAtLeast(0)
    }
}
