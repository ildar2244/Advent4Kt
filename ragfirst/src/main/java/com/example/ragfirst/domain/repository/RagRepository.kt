package com.example.ragfirst.domain.repository

import com.example.ragfirst.domain.model.Chunk
import com.example.ragfirst.domain.model.Document
import com.example.ragfirst.domain.model.Embedding
import com.example.ragfirst.domain.model.SearchResult

interface RagRepository {
    suspend fun saveDocument(document: Document): Int
    suspend fun saveChunk(chunk: Chunk): Int
    suspend fun saveEmbedding(embedding: Embedding)

    suspend fun getDocument(id: Int): Document?
    suspend fun getDocumentByPath(path: String): Document?
    suspend fun getAllDocuments(): List<Document>

    suspend fun getChunksByDocumentId(documentId: Int): List<Chunk>
    suspend fun getAllChunksWithEmbeddings(): List<Pair<Chunk, Embedding>>

    suspend fun searchSimilar(queryEmbedding: FloatArray, topK: Int = 5, threshold: Float = 0.7f): List<SearchResult>

    suspend fun clearIndex()
    suspend fun getStatistics(): IndexStatistics
}

data class IndexStatistics(
    val documentsCount: Int,
    val chunksCount: Int,
    val embeddingsCount: Int
)
