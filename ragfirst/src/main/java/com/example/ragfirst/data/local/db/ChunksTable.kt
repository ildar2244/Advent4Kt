package com.example.ragfirst.data.local.db

import org.jetbrains.exposed.sql.Table

object ChunksTable : Table("chunks") {
    val id = integer("id").autoIncrement()
    val documentId = integer("document_id").references(DocumentsTable.id)
    val content = text("content")
    val chunkIndex = integer("chunk_index")
    val metadata = text("metadata")

    override val primaryKey = PrimaryKey(id)
}
