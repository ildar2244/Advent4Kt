package com.example.tgbot.domain.repository

import com.example.tgbot.domain.model.RagSearchResult
import com.example.tgbot.domain.model.RagStatistics

interface RagRepository {
    suspend fun searchSimilar(query: String, topK: Int = 5): List<RagSearchResult>
    suspend fun getStatistics(): RagStatistics
    suspend fun clearIndex()
}
