package com.example.tgbot.data.repository

import com.example.ragfirst.data.local.db.DatabaseFactory as RagDatabaseFactory
import com.example.ragfirst.data.remote.OllamaApiClient
import com.example.ragfirst.data.repository.OllamaRepositoryImpl
import com.example.ragfirst.data.repository.RagRepositoryImpl as RagfirstRepositoryImpl
import com.example.ragfirst.domain.usecase.ClearIndexUseCase
import com.example.ragfirst.domain.usecase.GetStatisticsUseCase
import com.example.ragfirst.domain.usecase.SearchSimilarUseCase
import com.example.tgbot.domain.model.RagSearchResult
import com.example.tgbot.domain.model.RagStatistics
import com.example.tgbot.domain.repository.RagRepository

class RagRepositoryImpl : RagRepository {
    private val ragfirstRepository = RagfirstRepositoryImpl()
    private val ollamaClient = OllamaApiClient(model = "bge-m3")
    private val ollamaRepository = OllamaRepositoryImpl(ollamaClient)

    private val searchUseCase = SearchSimilarUseCase(ragfirstRepository, ollamaRepository)
    private val statsUseCase = GetStatisticsUseCase(ragfirstRepository)
    private val clearUseCase = ClearIndexUseCase(ragfirstRepository)

    init {
        RagDatabaseFactory.init()
    }

    override suspend fun searchSimilar(query: String, topK: Int): List<RagSearchResult> {
        val results = searchUseCase.execute(query, topK, threshold = 0.7f)
        return results.mapNotNull { result ->
            // Проверяем, что document.id и chunk.id не null
            val docId = result.document.id
            val chId = result.chunk.id

            if (docId == null || chId == null) {
                return@mapNotNull null
            }

            RagSearchResult(
                content = result.chunk.content,
                documentPath = result.document.path,
                documentId = docId,
                chunkIndex = result.chunk.chunkIndex,
                chunkId = chId,
                similarity = result.similarity
            )
        }
    }

    override suspend fun getStatistics(): RagStatistics {
        val stats = statsUseCase.execute()
        return RagStatistics(
            documentsCount = stats.documentsCount,
            chunksCount = stats.chunksCount,
            embeddingsCount = stats.embeddingsCount
        )
    }

    override suspend fun clearIndex() {
        clearUseCase.execute()
    }

    override suspend fun getChunkByDocumentAndIndex(documentId: Int, chunkIndex: Int): RagSearchResult? {
        try {
            // Получаем document из БД
            val document = ragfirstRepository.getDocument(documentId) ?: return null

            // Получаем все chunks документа
            val chunks = ragfirstRepository.getChunksByDocumentId(documentId)

            // Находим chunk с нужным chunkIndex
            val chunk = chunks.firstOrNull { it.chunkIndex == chunkIndex } ?: return null

            // Возвращаем как RagSearchResult
            return RagSearchResult(
                content = chunk.content,
                documentPath = document.path,
                documentId = document.id ?: 0,
                chunkIndex = chunk.chunkIndex,
                chunkId = chunk.id ?: 0,
                similarity = 1.0f  // Не релевантность, а точное совпадение
            )
        } catch (e: Exception) {
            println("Error in getChunkByDocumentAndIndex: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
