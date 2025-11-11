package com.example.tgbot.app

import com.example.tgbot.BuildConfig
import com.example.tgbot.data.remote.TelegramApi
import com.example.tgbot.data.remote.ai.ClaudeApiClient
import com.example.tgbot.data.remote.ai.HuggingFaceApiClient
import com.example.tgbot.data.remote.ai.OpenAiApiClient
import com.example.tgbot.data.remote.ai.YandexGptApiClient
import com.example.tgbot.data.repository.AiRepositoryImpl
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
//                    println(maskedMessage)
                }
            }
            level = LogLevel.INFO
        }
        // Настройка таймаутов для long polling
        install(HttpTimeout) {
            requestTimeoutMillis = 40000  // 30 сек long polling + 10 сек запас
            connectTimeoutMillis = 40000  // Таймаут подключения (должен быть > long polling timeout)
            socketTimeoutMillis = 40000   // Таймаут сокета
        }
    }

    // Инициализация слоёв Clean Architecture
    private val api = TelegramApi(httpClient, token)
    private val telegramRepository = TelegramRepositoryImpl(api)

    // Инициализация AI клиентов
    private val openAiClient = OpenAiApiClient(httpClient, BuildConfig.OPENAI_API_KEY)
    private val claudeClient = ClaudeApiClient(httpClient, BuildConfig.CLAUDE_API_KEY)
    private val yandexGptClient = YandexGptApiClient(
        httpClient,
        BuildConfig.YANDEX_GPT_API_KEY,
        BuildConfig.YANDEX_CLOUD_FOLDER_ID
    )
    private val huggingFaceClient = HuggingFaceApiClient(
        client = httpClient,
        apiKey = BuildConfig.HUGGING_FACE_API_KEY
    )
    private val aiRepository = AiRepositoryImpl(
        openAiClient,
        claudeClient,
        yandexGptClient,
        huggingFaceClient
    )

    // Инициализация use cases
    private val handleMessageUseCase = HandleMessageUseCase(telegramRepository, aiRepository)
    private val handleCommandUseCase = HandleCommandUseCase(telegramRepository)
    private val handleCallbackUseCase = HandleCallbackUseCase(telegramRepository)

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
                val updates = telegramRepository.getUpdates(offset)

                updates.forEach { update ->
                    // Обработка callback'ов (клики по инлайн-кнопкам)
                    update.callbackQuery?.let { callback ->
//                        println("Получен callback от ${callback.from.firstName}: ${callback.data}")
                        handleCallbackUseCase(callback)
                    }

                    // Обработка текстовых сообщений
                    update.message?.let { message ->
                        val text = message.text ?: return@let
//                        println("Получено сообщение от ${message.from.firstName}: $text")

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
                val maskedMessage = e.message?.replace(token, "***TOKEN***") ?: "Unknown error"
                println("Ошибка при получении обновлений: $maskedMessage")
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
    // Получаем токены из BuildConfig (загружаются из local.properties)
    val telegramToken = BuildConfig.TELEGRAM_BOT_TOKEN
    val openAiKey = BuildConfig.OPENAI_API_KEY
    val claudeKey = BuildConfig.CLAUDE_API_KEY
    val yandexGptKey = BuildConfig.YANDEX_GPT_API_KEY
    val yandexCloudFolderId = BuildConfig.YANDEX_CLOUD_FOLDER_ID

    // Проверяем наличие всех необходимых токенов
    if (telegramToken.isEmpty()) {
        println("Ошибка: TELEGRAM_BOT_TOKEN не задан в local.properties")
        return@runBlocking
    }
    if (openAiKey.isEmpty()) {
        println("Предупреждение: OPENAI_API_KEY не задан в local.properties")
    }
    if (claudeKey.isEmpty()) {
        println("Предупреждение: CLAUDE_API_KEY не задан в local.properties")
    }
    if (yandexGptKey.isEmpty()) {
        println("Предупреждение: YANDEX_GPT_API_KEY не задан в local.properties")
    }
    if (yandexCloudFolderId.isEmpty()) {
        println("Предупреждение: YANDEX_CLOUD_FOLDER_ID не задан в local.properties")
    }

    val bot = TelegramBot(telegramToken)

    // Регистрируем обработчик остановки для корректного завершения
    Runtime.getRuntime().addShutdownHook(Thread {
        bot.stop()
    })

    bot.start()
}
