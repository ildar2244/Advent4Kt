package com.example.mcptasks.data.local

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/**
 * Фабрика для инициализации и управления базой данных SQLite
 */
object DatabaseFactory {

    /**
     * Инициализировать подключение к базе данных SQLite
     *
     * @param dbPath Путь к файлу базы данных (например, "./data/tasks.db")
     */
    fun init(dbPath: String) {
        // Создать директорию для БД, если она не существует
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()

        // Подключиться к SQLite
        val database = Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC"
        )

        // Создать таблицы, если они не существуют
        transaction(database) {
            SchemaUtils.create(TasksTable)
        }
    }

    /**
     * Выполнить транзакцию к базе данных в suspend контексте
     *
     * @param T Тип возвращаемого значения
     * @param block Блок кода для выполнения в транзакции
     * @return Результат выполнения блока
     */
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
