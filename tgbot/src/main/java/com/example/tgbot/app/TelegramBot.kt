package com.example.tgbot.app

import com.example.tgbot.BuildConfig
import com.example.tgbot.data.local.db.DatabaseFactory
import com.example.tgbot.data.remote.TelegramApi
import com.example.tgbot.data.remote.McpWebSocketClient
import com.example.tgbot.data.remote.TasksWebSocketClient
import com.example.tgbot.data.remote.ai.ClaudeApiClient
import com.example.tgbot.data.remote.ai.HuggingFaceApiClient
import com.example.tgbot.data.remote.ai.OpenAiApiClient
import com.example.tgbot.data.remote.ai.YandexGptApiClient
import com.example.tgbot.data.repository.AiRepositoryImpl
import com.example.tgbot.data.repository.GeocodingRepositoryImpl
import com.example.tgbot.data.repository.McpRepositoryImpl
import com.example.tgbot.data.repository.RagRepositoryImpl
import com.example.tgbot.data.repository.TasksRepositoryImpl
import com.example.tgbot.data.repository.SummaryRepositoryImpl
import com.example.tgbot.data.repository.TelegramRepositoryImpl
import com.example.tgbot.domain.service.HistoryCompressor
import com.example.tgbot.domain.usecase.HandleCallbackUseCase
import com.example.tgbot.domain.usecase.HandleCommandUseCase
import com.example.tgbot.domain.usecase.HandleMessageUseCase
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
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
        // Настройка CIO engine для лучшей надежности
        engine {
            maxConnectionsCount = 1000
            endpoint {
                maxConnectionsPerRoute = 100
                pipelineMaxSize = 20
                keepAliveTime = 5000
                connectTimeout = 40000
                connectAttempts = 3
            }
        }

        // Content Negotiation для автоматической сериализации/десериализации JSON
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // Игнорируем неизвестные поля в JSON
                isLenient = true // Более мягкий парсинг JSON
                encodeDefaults = true // ВАЖНО: сериализуем default values (для OpenAI tools type="function")
                explicitNulls = false // Не включаем null поля в JSON (убирает tool_calls:null и т.д.)
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

        // WebSockets для MCP Weather
        install(WebSockets)

        // Retry механизм для сетевых ошибок
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
            retryIf { request, response ->
                response.status.value.let { it == 429 || it >= 500 }
            }
            retryOnException(maxRetries = 3, retryOnTimeout = true)
            delayMillis { retry ->
                (retry * 1000L).coerceAtMost(5000L)
            }
        }
    }

    // Инициализация слоёв Clean Architecture
    private val api = TelegramApi(httpClient, token)
    private val telegramRepository = TelegramRepositoryImpl(api)

    // Инициализация MCP Weather WebSocket Client (перенесено выше для использования в OpenAI)
    private val mcpWebSocketClient = McpWebSocketClient(
        httpClient = httpClient,
        wsUrl = BuildConfig.MCP_WEATHER_WS_URL
    )
    private val mcpRepository = McpRepositoryImpl(mcpWebSocketClient)

    // Инициализация MCP Tasks WebSocket Client
    private val tasksWebSocketClient = TasksWebSocketClient(
        httpClient = httpClient,
        wsUrl = BuildConfig.MCP_TASKS_WS_URL
    )
    private val tasksRepository = TasksRepositoryImpl(tasksWebSocketClient)

    // Инициализация Geocoding Repository для конвертации городов в координаты
    private val geocodingRepository = GeocodingRepositoryImpl(httpClient)

    // Инициализация AI клиентов
    private val openAiClient = OpenAiApiClient(
        httpClient,
        BuildConfig.OPENAI_API_KEY,
        mcpRepository,
        tasksRepository,
        geocodingRepository
    )
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

    // Инициализация сервисов для работы с историей диалога
    private val historyCompressor = HistoryCompressor(aiRepository)

    // Инициализация репозитория для работы с БД
    private val summaryRepository = SummaryRepositoryImpl()

    // Инициализация RAG репозитория
    private val ragRepository = RagRepositoryImpl()

    // Инициализация use cases
    private val handleMessageUseCase = HandleMessageUseCase(telegramRepository, aiRepository, historyCompressor, summaryRepository, mcpRepository, ragRepository)
    private val handleCommandUseCase = HandleCommandUseCase(telegramRepository, summaryRepository, mcpRepository, ragRepository, aiRepository)
    private val handleCallbackUseCase = HandleCallbackUseCase(telegramRepository, mcpRepository, ragRepository, aiRepository)

    // Offset для отслеживания обработанных обновлений
    private var offset: Long? = null
    private var isRunning = false

    /**
     * Запускает бота и начинает обработку обновлений через long polling.
     */
    suspend fun start() {
        isRunning = true
        println("Бот запущен. Ожидание сообщений...")

        // Подключение к MCP Weather WebSocket серверу
        try {
            println("🔌 Подключение к MCP Weather WebSocket: ${BuildConfig.MCP_WEATHER_WS_URL}")
            println("   Ожидание установки соединения (таймаут 5 секунд)...")
            mcpWebSocketClient.connect()
            println("✅ Подключено к MCP Weather WebSocket успешно!")
        } catch (e: Exception) {
            println("⚠️ Не удалось подключиться к MCP Weather WebSocket: ${e.message}")
            println("   ${e.javaClass.simpleName}: ${e.stackTraceToString().take(500)}")
            println("   Функции погоды будут недоступны. Бот продолжит работу...")
        }

        // Подключение к MCP Tasks WebSocket серверу
        try {
            println("🔌 Подключение к MCP Tasks WebSocket: ${BuildConfig.MCP_TASKS_WS_URL}")
            println("   Ожидание установки соединения (таймаут 5 секунд)...")
            tasksWebSocketClient.connect()
            println("✅ Подключено к MCP Tasks WebSocket успешно!")
        } catch (e: Exception) {
            println("⚠️ Не удалось подключиться к MCP Tasks WebSocket: ${e.message}")
            println("   ${e.javaClass.simpleName}: ${e.stackTraceToString().take(500)}")
            println("   Функции задач будут недоступны. Бот продолжит работу...")
        }

        println("✅ Telegram бот готов к работе")

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

                    // Обработка сообщений (текстовых и location)
                    update.message?.let { message ->
                        // Если это location message
                        if (message.location != null) {
                            println("📍 Получена геолокация от ${message.from.firstName}")
                            handleMessageUseCase(message)
                            return@let
                        }

                        // Обработка текстовых сообщений
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
    suspend fun stop() {
        isRunning = false

        // Отключение от MCP Weather WebSocket
        try {
            mcpWebSocketClient.disconnect()
            println("🔌 Отключено от MCP Weather WebSocket")
        } catch (e: Exception) {
            println("⚠️ Ошибка при отключении от MCP Weather WebSocket: ${e.message}")
        }

        // Отключение от MCP Tasks WebSocket
        try {
            tasksWebSocketClient.disconnect()
            println("🔌 Отключено от MCP Tasks WebSocket")
        } catch (e: Exception) {
            println("⚠️ Ошибка при отключении от MCP Tasks WebSocket: ${e.message}")
        }

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

    // Инициализация базы данных
    DatabaseFactory.init()

    val bot = TelegramBot(telegramToken)

    // Регистрируем обработчик остановки для корректного завершения
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            bot.stop()
        }
    })

    bot.start()
}
