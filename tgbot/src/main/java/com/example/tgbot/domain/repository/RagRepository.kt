package com.example.tgbot.domain.repository

import com.example.tgbot.domain.model.RagSearchResult
import com.example.tgbot.domain.model.RagStatistics

interface RagRepository {
    suspend fun searchSimilar(query: String, topK: Int = 5): List<RagSearchResult>
    suspend fun getStatistics(): RagStatistics
    suspend fun clearIndex()

    /**
     * Получить chunk по ID документа и индексу фрагмента.
     * Используется для отображения preview содержимого фрагмента в popup.
     *
     * @param documentId ID документа
     * @param chunkIndex Индекс фрагмента в документе (0-based)
     * @return RagSearchResult с данными фрагмента или null, если не найден
     */
    suspend fun getChunkByDocumentAndIndex(documentId: Int, chunkIndex: Int): RagSearchResult?
}
