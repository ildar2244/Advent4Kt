package com.example.ragfirst.data.local.db

import com.example.ragfirst.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object RagDao {
    private val json = Json { prettyPrint = false }

    fun insertDocument(document: Document): Int = transaction {
        DocumentsTable.insert {
            it[path] = document.path
            it[type] = document.type.name
            it[content] = document.content
            it[metadata] = json.encodeToString(document.metadata)
            it[createdAt] = document.createdAt
        }[DocumentsTable.id]
    }

    fun insertChunk(chunk: Chunk): Int = transaction {
        ChunksTable.insert {
            it[documentId] = chunk.documentId
            it[content] = chunk.content
            it[chunkIndex] = chunk.chunkIndex
            it[metadata] = json.encodeToString(chunk.metadata)
        }[ChunksTable.id]
    }

    fun insertEmbedding(embedding: Embedding): Unit = transaction {
        EmbeddingsTable.insert {
            it[chunkId] = embedding.chunkId
            it[vector] = ExposedBlob(serializeFloatArray(embedding.vector))
        }
        Unit
    }

    fun getDocumentById(id: Int): Document? = transaction {
        DocumentsTable.selectAll()
            .where { DocumentsTable.id eq id }
            .map { rowToDocument(it) }
            .singleOrNull()
    }

    fun getDocumentByPath(path: String): Document? = transaction {
        DocumentsTable.selectAll()
            .where { DocumentsTable.path eq path }
            .map { rowToDocument(it) }
            .singleOrNull()
    }

    fun getAllDocuments(): List<Document> = transaction {
        DocumentsTable.selectAll().map { rowToDocument(it) }
    }

    fun getChunksByDocumentId(documentId: Int): List<Chunk> = transaction {
        ChunksTable.selectAll()
            .where { ChunksTable.documentId eq documentId }
            .map { rowToChunk(it) }
    }

    fun getAllChunksWithEmbeddings(): List<Pair<Chunk, Embedding>> = transaction {
        (ChunksTable innerJoin EmbeddingsTable)
            .selectAll()
            .map {
                val chunk = rowToChunk(it)
                val embedding = Embedding(
                    chunkId = it[EmbeddingsTable.chunkId],
                    vector = deserializeFloatArray(it[EmbeddingsTable.vector].bytes)
                )
                chunk to embedding
            }
    }

    fun clearAll(): Unit = transaction {
        EmbeddingsTable.deleteAll()
        ChunksTable.deleteAll()
        DocumentsTable.deleteAll()
        Unit
    }

    fun getStatistics() = transaction {
        val documentsCount = DocumentsTable.selectAll().count().toInt()
        val chunksCount = ChunksTable.selectAll().count().toInt()
        val embeddingsCount = EmbeddingsTable.selectAll().count().toInt()

        Triple(documentsCount, chunksCount, embeddingsCount)
    }

    private fun rowToDocument(row: ResultRow) = Document(
        id = row[DocumentsTable.id],
        path = row[DocumentsTable.path],
        type = DocumentType.valueOf(row[DocumentsTable.type]),
        content = row[DocumentsTable.content],
        metadata = json.decodeFromString(row[DocumentsTable.metadata]),
        createdAt = row[DocumentsTable.createdAt]
    )

    private fun rowToChunk(row: ResultRow) = Chunk(
        id = row[ChunksTable.id],
        documentId = row[ChunksTable.documentId],
        content = row[ChunksTable.content],
        chunkIndex = row[ChunksTable.chunkIndex],
        metadata = json.decodeFromString(row[ChunksTable.metadata])
    )

    private fun serializeFloatArray(array: FloatArray): ByteArray {
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)
        dataStream.writeInt(array.size)
        array.forEach { dataStream.writeFloat(it) }
        return byteStream.toByteArray()
    }

    private fun deserializeFloatArray(bytes: ByteArray): FloatArray {
        val byteStream = ByteArrayInputStream(bytes)
        val dataStream = DataInputStream(byteStream)
        val size = dataStream.readInt()
        return FloatArray(size) { dataStream.readFloat() }
    }
}
