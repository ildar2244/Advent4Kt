package com.example.ragfirst.data.local.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object DocumentsTable : Table("documents") {
    val id = integer("id").autoIncrement()
    val path = text("path")
    val type = varchar("type", 50)
    val content = text("content")
    val metadata = text("metadata")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
