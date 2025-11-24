package com.example.ragfirst.data.local.parser

import com.example.ragfirst.domain.model.Document
import com.example.ragfirst.domain.model.DocumentType
import com.example.ragfirst.domain.usecase.DocumentParser
import java.io.File

class MarkdownParser : DocumentParser {
    override fun supports(file: File): Boolean {
        return file.extension.lowercase() in listOf("md", "markdown")
    }

    override fun parse(file: File): Document {
        val content = file.readText()
        val metadata = extractMetadata(content, file)

        return Document(
            path = file.absolutePath,
            type = DocumentType.MARKDOWN,
            content = content,
            metadata = metadata
        )
    }

    private fun extractMetadata(content: String, file: File): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        metadata["filename"] = file.name
        metadata["size"] = file.length().toString()

        val headerRegex = Regex("^#{1,6}\\s+(.+)$", RegexOption.MULTILINE)
        val headers = headerRegex.findAll(content).map { it.groupValues[1] }.toList()

        if (headers.isNotEmpty()) {
            metadata["first_header"] = headers.first()
            metadata["headers_count"] = headers.size.toString()
        }

        return metadata
    }
}
