package com.example.tgbot.data.repository

import com.example.tgbot.data.local.db.DatabaseFactory
import com.example.tgbot.data.local.db.SummaryDao
import com.example.tgbot.data.local.db.SummaryRecord
import com.example.tgbot.domain.model.SummarizedHistory
import com.example.tgbot.domain.repository.SummaryRepository
import kotlinx.coroutines.withContext

/**
 * Реализация репозитория для работы с суммаризациями
 */
class SummaryRepositoryImpl : SummaryRepository {

    override suspend fun saveSummary(text: String): Int = withContext(DatabaseFactory.databaseDispatcher) {
        SummaryDao.insert(text)
    }

    override suspend fun getAllSummaries(): List<SummarizedHistory> = withContext(DatabaseFactory.databaseDispatcher) {
        SummaryDao.getAll().map { it.toDomain() }
    }

    override suspend fun getLastSummaries(limit: Int): List<SummarizedHistory> = withContext(DatabaseFactory.databaseDispatcher) {
        SummaryDao.getLastN(limit).map { it.toDomain() }
    }

    override suspend fun getCount(): Long = withContext(DatabaseFactory.databaseDispatcher) {
        SummaryDao.getCount()
    }

    override suspend fun clearAll(): Unit = withContext(DatabaseFactory.databaseDispatcher) {
        SummaryDao.deleteAll()
    }

    /**
     * Маппинг из data-слоя в domain-слой
     */
    private fun SummaryRecord.toDomain() = SummarizedHistory(
        id = id,
        text = text,
        timestamp = timestamp
    )
}
