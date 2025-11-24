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
    private val ollamaClient = OllamaApiClient()
    private val ollamaRepository = OllamaRepositoryImpl(ollamaClient)

    private val searchUseCase = SearchSimilarUseCase(ragfirstRepository, ollamaRepository)
    private val statsUseCase = GetStatisticsUseCase(ragfirstRepository)
    private val clearUseCase = ClearIndexUseCase(ragfirstRepository)

    init {
        RagDatabaseFactory.init()
    }

    override suspend fun searchSimilar(query: String, topK: Int): List<RagSearchResult> {
        val results = searchUseCase.execute(query, topK, threshold = 0.7f)
        return results.map { result ->
            RagSearchResult(
                content = result.chunk.content,
                documentPath = result.document.path,
                chunkIndex = result.chunk.chunkIndex,
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
}
