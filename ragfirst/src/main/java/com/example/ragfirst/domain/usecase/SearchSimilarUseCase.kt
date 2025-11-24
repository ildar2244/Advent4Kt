package com.example.ragfirst.domain.usecase

import com.example.ragfirst.domain.model.SearchResult
import com.example.ragfirst.domain.repository.OllamaRepository
import com.example.ragfirst.domain.repository.RagRepository
import org.slf4j.LoggerFactory

class SearchSimilarUseCase(
    private val ragRepository: RagRepository,
    private val ollamaRepository: OllamaRepository
) {
    private val logger = LoggerFactory.getLogger(SearchSimilarUseCase::class.java)

    suspend fun execute(query: String, topK: Int = 5, threshold: Float = 0.7f): List<SearchResult> {
        logger.info("Searching for: '$query' (topK=$topK, threshold=$threshold)")

        val queryEmbedding = ollamaRepository.generateEmbedding(query)
        val results = ragRepository.searchSimilar(queryEmbedding, topK, threshold)

        logger.info("Found ${results.size} relevant chunks")
        return results
    }
}
