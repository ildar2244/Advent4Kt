package com.example.mcptasks.data.local

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Exposed таблица для хранения задач в SQLite
 */
object TasksTable : Table("tasks") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 500)
    val description = text("description")
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
