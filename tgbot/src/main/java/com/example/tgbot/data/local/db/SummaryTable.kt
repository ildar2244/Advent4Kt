package com.example.tgbot.data.local.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * Таблица для хранения результатов суммаризации диалога
 */
object SummaryTable : Table("summaries") {
    val id = integer("id").autoIncrement()
    val text = text("text")
    val timestamp = timestamp("timestamp").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}
