package com.example.tgbot.domain.model

data class RagSearchResult(
    val content: String,
    val documentPath: String,
    val chunkIndex: Int,
    val similarity: Float
)
