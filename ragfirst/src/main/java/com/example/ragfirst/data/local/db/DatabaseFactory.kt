package com.example.ragfirst.data.local.db

import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.concurrent.Executors

object DatabaseFactory {
    // Определяем путь к БД относительно корня проекта
    private val projectRoot = File(System.getProperty("user.dir")).let { cwd ->
        // Если запущено из модуля ragfirst, поднимаемся на уровень выше
        if (cwd.name == "ragfirst") cwd.parentFile else cwd
    }
    private val dbPathProjectDocs = "docs/rag_project_docs.db"
    private val dbPathModule = "ragfirst/data/rag.db"
    private val DB_PATH = File(projectRoot, dbPathModule).absolutePath
    private val JDBC_URL = "jdbc:sqlite:$DB_PATH"

    val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    fun init() {
        // Создаём директорию для БД, если её нет
        val dbFile = File(DB_PATH)
        dbFile.parentFile?.mkdirs()

        Database.connect(
            url = "$JDBC_URL?journal_mode=WAL&busy_timeout=5000",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            exec("PRAGMA journal_mode=WAL;")
            exec("PRAGMA busy_timeout=5000;")

            SchemaUtils.create(DocumentsTable, ChunksTable, EmbeddingsTable)
        }

        println("✓ RAG база данных SQLite инициализирована: $DB_PATH (WAL режим)")
    }
}
