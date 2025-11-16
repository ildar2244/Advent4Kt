package com.example.tgbot.domain.repository

import com.example.tgbot.domain.model.SummarizedHistory

/**
 * Интерфейс репозитория для работы с суммаризациями диалога
 */
interface SummaryRepository {

    /**
     * Сохранить результат суммаризации
     * @param text текст суммаризации
     * @return ID созданной записи
     */
    suspend fun saveSummary(text: String): Int

    /**
     * Получить все записи суммаризаций
     * @return список всех записей (отсортированы по убыванию timestamp)
     */
    suspend fun getAllSummaries(): List<SummarizedHistory>

    /**
     * Получить последние N записей
     * @param limit количество записей
     * @return список последних N записей
     */
    suspend fun getLastSummaries(limit: Int): List<SummarizedHistory>

    /**
     * Получить количество записей в БД
     * @return общее количество записей
     */
    suspend fun getCount(): Long

    /**
     * Удалить все записи из БД
     */
    suspend fun clearAll()
}
