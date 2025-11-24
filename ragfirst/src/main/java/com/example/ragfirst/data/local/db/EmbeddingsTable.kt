package com.example.ragfirst.data.local.db

import org.jetbrains.exposed.sql.Table

object EmbeddingsTable : Table("embeddings") {
    val chunkId = integer("chunk_id").references(ChunksTable.id)
    val vector = blob("vector")

    override val primaryKey = PrimaryKey(chunkId)
}
