package com.example.tgbot.app

import com.example.tgbot.BuildConfig
import com.example.tgbot.data.remote.TelegramApi
import com.example.tgbot.data.repository.TelegramRepositoryImpl
import com.example.tgbot.domain.usecase.HandleCallbackUseCase
import com.example.tgbot.domain.usecase.HandleCommandUseCase
import com.example.tgbot.domain.usecase.HandleMessageUseCase
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Главный класс Telegram-бота.
 * Управляет жизненным циклом бота, обрабатывает обновления через long polling.
 */
class TelegramBot(private val token: String) {
    // Настройка HTTP-клиента Ktor
    private val httpClient = HttpClient(CIO) {
        // Content Negotiation для автоматической сериализации/десериализации JSON
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // Игнорируем неизвестные поля в JSON
                isLenient = true // Более мягкий парсинг JSON
            })
        }
        // Логирование HTTP-запросов
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    // Маскируем токен в логах для безопасности
                    val maskedMessage = message.replace(token, "***TOKEN***")
                    println(maskedMessage)
                }
            }
            level = LogLevel.INFO
        }
        // Настройка таймаутов для long polling
        install(HttpTimeout) {
            requestTimeoutMillis = 35000  // 30 сек long polling + 5 сек запас
            connectTimeoutMillis = 10000  // Таймаут подключения
            socketTimeoutMillis = 35000   // Таймаут сокета
        }
    }

    // Инициализация слоёв Clean Architecture
    private val api = TelegramApi(httpClient, token)
    private val repository = TelegramRepositoryImpl(api)
    private val handleMessageUseCase = HandleMessageUseCase(repository)
    private val handleCommandUseCase = HandleCommandUseCase(repository)
    private val handleCallbackUseCase = HandleCallbackUseCase(repository)

    // Offset для отслеживания обработанных обновлений
    private var offset: Long? = null
    private var isRunning = false

    /**
     * Запускает бота и начинает обработку обновлений через long polling.
     */
    suspend fun start() {
        isRunning = true
        println("Бот запущен. Ожидание сообщений...")

        while (isRunning) {
            try {
                // Получаем обновления от Telegram
                val updates = repository.getUpdates(offset)

                updates.forEach { update ->
                    // Обработка callback'ов (клики по инлайн-кнопкам)
                    update.callbackQuery?.let { callback ->
                        println("Получен callback от ${callback.from.firstName}: ${callback.data}")
                        handleCallbackUseCase(callback)
                    }

                    // Обработка текстовых сообщений
                    update.message?.let { message ->
                        val text = message.text ?: return@let
                        println("Получено сообщение от ${message.from.firstName}: $text")

                        // Роутинг: команды vs обычные сообщения
                        if (text.startsWith("/")) {
                            handleCommandUseCase(message)
                        } else {
                            // Эхо-ответ для обычных сообщений
                            handleMessageUseCase(message)
                        }
                    }

                    // Обновляем offset для пропуска уже обработанных обновлений
                    offset = update.updateId + 1
                }
            } catch (e: Exception) {
                println("Ошибка при получении обновлений: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Останавливает работу бота и закрывает HTTP-клиент.
     */
    fun stop() {
        isRunning = false
        httpClient.close()
        println("Бот остановлен")
    }
}

/**
 * Точка входа в приложение.
 * Инициализирует и запускает бота.
 */
fun main() = runBlocking {
    // Получаем токен из BuildConfig (загружается из local.properties)
    val token = BuildConfig.TELEGRAM_BOT_TOKEN
    if (token.isEmpty()) {
        println("Ошибка: TELEGRAM_BOT_TOKEN не задан")
        return@runBlocking
    }

    val bot = TelegramBot(token)

    // Регистрируем обработчик остановки для корректного завершения
    Runtime.getRuntime().addShutdownHook(Thread {
        bot.stop()
    })

    bot.start()
}
