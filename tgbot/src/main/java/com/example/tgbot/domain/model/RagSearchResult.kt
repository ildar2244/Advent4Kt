package com.example.tgbot.domain.model

data class RagSearchResult(
    val content: String,
    val documentPath: String,
    val documentId: Int,
    val chunkIndex: Int,
    val chunkId: Int,
    val similarity: Float
)
