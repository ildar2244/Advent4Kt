package com.example.ragfirst.data.local.parser

import com.example.ragfirst.domain.model.Document
import com.example.ragfirst.domain.usecase.DocumentParser
import java.io.File

class CompositeDocumentParser(
    private val parsers: List<DocumentParser>
) : DocumentParser {
    override fun supports(file: File): Boolean {
        return parsers.any { it.supports(file) }
    }

    override fun parse(file: File): Document {
        val parser = parsers.firstOrNull { it.supports(file) }
            ?: throw UnsupportedOperationException("No parser found for file: ${file.name}")
        return parser.parse(file)
    }
}
