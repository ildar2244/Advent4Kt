package com.example.ragfirst.domain.model

data class Chunk(
    val id: Int? = null,
    val documentId: Int,
    val content: String,
    val chunkIndex: Int,
    val metadata: Map<String, String> = emptyMap()
)
