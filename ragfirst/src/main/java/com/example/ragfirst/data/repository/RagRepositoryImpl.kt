package com.example.ragfirst.data.repository

import com.example.ragfirst.data.local.db.DatabaseFactory
import com.example.ragfirst.data.local.db.RagDao
import com.example.ragfirst.domain.model.Chunk
import com.example.ragfirst.domain.model.Document
import com.example.ragfirst.domain.model.Embedding
import com.example.ragfirst.domain.model.SearchResult
import com.example.ragfirst.domain.repository.IndexStatistics
import com.example.ragfirst.domain.repository.RagRepository
import com.example.ragfirst.util.SimilarityCalculator
import kotlinx.coroutines.withContext

class RagRepositoryImpl : RagRepository {
    override suspend fun saveDocument(document: Document): Int = withContext(DatabaseFactory.databaseDispatcher) {
        RagDao.insertDocument(document)
    }

    override suspend fun saveChunk(chunk: Chunk): Int = withContext(DatabaseFactory.databaseDispatcher) {
        RagDao.insertChunk(chunk)
    }

    override suspend fun saveEmbedding(embedding: Embedding) = withContext(DatabaseFactory.databaseDispatcher) {
        RagDao.insertEmbedding(embedding)
    }

    override suspend fun getDocument(id: Int): Document? = withContext(DatabaseFactory.databaseDispatcher) {
        RagDao.getDocumentById(id)
    }

    override suspend fun getDocumentByPath(path: String): Document? = withContext(DatabaseFactory.databaseDispatcher) {
        RagDao.getDocumentByPath(path)
    }

    override suspend fun getAllDocuments(): List<Document> = withContext(DatabaseFactory.databaseDispatcher) {
        RagDao.getAllDocuments()
    }

    override suspend fun getChunksByDocumentId(documentId: Int): List<Chunk> = withContext(DatabaseFactory.databaseDispatcher) {
        RagDao.getChunksByDocumentId(documentId)
    }

    override suspend fun getAllChunksWithEmbeddings(): List<Pair<Chunk, Embedding>> = withContext(DatabaseFactory.databaseDispatcher) {
        RagDao.getAllChunksWithEmbeddings()
    }

    override suspend fun searchSimilar(
        queryEmbedding: FloatArray,
        topK: Int,
        threshold: Float
    ): List<SearchResult> = withContext(DatabaseFactory.databaseDispatcher) {
        val chunksWithEmbeddings = RagDao.getAllChunksWithEmbeddings()

        val similarities = chunksWithEmbeddings.map { (chunk, embedding) ->
            val similarity = SimilarityCalculator.cosineSimilarity(queryEmbedding, embedding.vector)
            Triple(chunk, similarity, embedding)
        }

        val filtered = similarities.filter { it.second >= threshold }
        val sorted = filtered.sortedByDescending { it.second }
        val topResults = sorted.take(topK)

        topResults.map { (chunk, similarity, _) ->
            val document = RagDao.getDocumentById(chunk.documentId)
                ?: throw IllegalStateException("Document not found for chunk ${chunk.id}")

            SearchResult(
                chunk = chunk,
                document = document,
                similarity = similarity
            )
        }
    }

    override suspend fun clearIndex() = withContext(DatabaseFactory.databaseDispatcher) {
        RagDao.clearAll()
    }

    override suspend fun getStatistics(): IndexStatistics = withContext(DatabaseFactory.databaseDispatcher) {
        val (documentsCount, chunksCount, embeddingsCount) = RagDao.getStatistics()
        IndexStatistics(documentsCount, chunksCount, embeddingsCount)
    }
}
