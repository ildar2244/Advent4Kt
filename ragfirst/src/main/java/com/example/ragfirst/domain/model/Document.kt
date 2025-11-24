package com.example.ragfirst.domain.model

import java.time.LocalDateTime

data class Document(
    val id: Int? = null,
    val path: String,
    val type: DocumentType,
    val content: String,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: LocalDateTime = LocalDateTime.now()
)
