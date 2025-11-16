package com.example.tgbot.data.local.db

import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.Executors

/**
 * Фабрика для инициализации и управления базой данных SQLite
 */
object DatabaseFactory {
    private const val DB_PATH = "tgbot/summaries.db"
    private const val JDBC_URL = "jdbc:sqlite:$DB_PATH"

    /**
     * Выделенный single-threaded dispatcher для БД операций
     * Предотвращает SQLITE_BUSY ошибки при параллельных записях
     */
    val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /**
     * Инициализация подключения к БД и создание таблиц
     */
    fun init() {
        Database.connect(
            url = "$JDBC_URL?journal_mode=WAL&busy_timeout=5000",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            // Включение WAL режима для лучшей конкурентности
            exec("PRAGMA journal_mode=WAL;")
            // Установка busy timeout в 5 секунд
            exec("PRAGMA busy_timeout=5000;")
            // Создание таблицы
            SchemaUtils.create(SummaryTable)
        }

        println("✓ База данных SQLite инициализирована: $DB_PATH (WAL режим)")
    }
}
