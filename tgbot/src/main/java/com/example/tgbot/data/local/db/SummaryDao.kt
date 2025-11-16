package com.example.tgbot.data.local.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Data class для результата запроса
 */
data class SummaryRecord(
    val id: Int,
    val text: String,
    val timestamp: Instant
)

/**
 * DAO для работы с таблицей суммаризаций
 */
object SummaryDao {

    /**
     * Сохранить результат суммаризации
     * @param text текст суммаризации
     * @return ID созданной записи
     */
    fun insert(text: String): Int = transaction {
        SummaryTable.insert {
            it[SummaryTable.text] = text
        } get SummaryTable.id
    }

    /**
     * Получить все записи
     * @return список всех записей
     */
    fun getAll(): List<SummaryRecord> = transaction {
        SummaryTable.selectAll()
            .orderBy(SummaryTable.timestamp to SortOrder.DESC)
            .map { it.toSummaryRecord() }
    }

    /**
     * Получить последние N записей
     * @param limit количество записей
     * @return список последних N записей (отсортированы по убыванию timestamp)
     */
    fun getLastN(limit: Int): List<SummaryRecord> = transaction {
        SummaryTable.selectAll()
            .orderBy(SummaryTable.timestamp to SortOrder.DESC)
            .limit(limit)
            .map { it.toSummaryRecord() }
    }

    /**
     * Получить количество записей
     * @return общее количество записей в БД
     */
    fun getCount(): Long = transaction {
        SummaryTable.selectAll().count()
    }

    /**
     * Удалить все записи из БД
     */
    fun deleteAll() = transaction {
        SummaryTable.deleteAll()
    }

    /**
     * Маппинг ResultRow в SummaryRecord
     */
    private fun ResultRow.toSummaryRecord() = SummaryRecord(
        id = this[SummaryTable.id],
        text = this[SummaryTable.text],
        timestamp = this[SummaryTable.timestamp]
    )
}
