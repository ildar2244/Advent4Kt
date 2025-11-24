package com.example.ragfirst.domain.model

data class SearchResult(
    val chunk: Chunk,
    val document: Document,
    val similarity: Float
)
