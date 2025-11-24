package com.example.ragfirst.data.local.parser

import com.example.ragfirst.domain.model.Document
import com.example.ragfirst.domain.model.DocumentType
import com.example.ragfirst.domain.usecase.DocumentParser
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

class PdfParser : DocumentParser {
    override fun supports(file: File): Boolean {
        return file.extension.lowercase() == "pdf"
    }

    override fun parse(file: File): Document {
        val pdfDocument = Loader.loadPDF(file)

        return try {
            val stripper = PDFTextStripper()
            val content = stripper.getText(pdfDocument)

            val metadata = mutableMapOf<String, String>()
            metadata["filename"] = file.name
            metadata["size"] = file.length().toString()
            metadata["pages"] = pdfDocument.numberOfPages.toString()

            val info = pdfDocument.documentInformation
            info.title?.let { metadata["title"] = it }
            info.author?.let { metadata["author"] = it }
            info.subject?.let { metadata["subject"] = it }

            Document(
                path = file.absolutePath,
                type = DocumentType.PDF,
                content = content.trim(),
                metadata = metadata
            )
        } finally {
            pdfDocument.close()
        }
    }
}
