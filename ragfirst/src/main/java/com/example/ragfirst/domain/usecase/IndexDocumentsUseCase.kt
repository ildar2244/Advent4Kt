package com.example.ragfirst.domain.usecase

import com.example.ragfirst.domain.model.Chunk
import com.example.ragfirst.domain.model.Document
import com.example.ragfirst.domain.model.Embedding
import com.example.ragfirst.domain.repository.OllamaRepository
import com.example.ragfirst.domain.repository.RagRepository
import org.slf4j.LoggerFactory
import java.io.File

class IndexDocumentsUseCase(
    private val ragRepository: RagRepository,
    private val ollamaRepository: OllamaRepository,
    private val documentParser: DocumentParser,
    private val textChunker: TextChunker
) {
    private val logger = LoggerFactory.getLogger(IndexDocumentsUseCase::class.java)

    suspend fun execute(directoryPath: String) {
        logger.info("Starting indexing from directory: $directoryPath")
        val directory = File(directoryPath)

        if (!directory.exists() || !directory.isDirectory) {
            throw IllegalArgumentException("Invalid directory path: $directoryPath")
        }

        val files = directory.walkTopDown()
            .filter { it.isFile && documentParser.supports(it) }
            .toList()

        logger.info("Found ${files.size} files to index")

        files.forEachIndexed { index, file ->
            logger.info("Indexing file ${index + 1}/${files.size}: ${file.name}")
            indexFile(file)
        }

        logger.info("Indexing completed successfully")
    }

    suspend fun executeSingleFile(filePath: String) {
        logger.info("Starting indexing single file: $filePath")
        val file = File(filePath)

        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }

        if (!file.isFile) {
            throw IllegalArgumentException("Path is not a file: $filePath")
        }

        if (!documentParser.supports(file)) {
            throw IllegalArgumentException("File type not supported: ${file.extension}. Supported: .md, .markdown, .pdf")
        }

        indexFile(file)
        logger.info("Indexing completed successfully")
    }

    private suspend fun indexFile(file: File) {
        try {
            val document = ragRepository.getDocumentByPath(file.absolutePath)
            if (document != null) {
                logger.info("Document already indexed, skipping: ${file.name}")
                return
            }

            val parsedDocument = documentParser.parse(file)
            val documentId = ragRepository.saveDocument(parsedDocument)

            val chunks = textChunker.chunk(parsedDocument.content)
            logger.info("Created ${chunks.size} chunks for document: ${file.name}")

            chunks.forEachIndexed { index, chunkText ->
                val chunk = Chunk(
                    documentId = documentId,
                    content = chunkText,
                    chunkIndex = index
                )
                val chunkId = ragRepository.saveChunk(chunk)

                val embedding = ollamaRepository.generateEmbedding(chunkText)
                ragRepository.saveEmbedding(Embedding(chunkId, embedding))
            }

            logger.info("Successfully indexed file: ${file.name}")
        } catch (e: Exception) {
            logger.error("Failed to index file: ${file.name}", e)
        }
    }
}

interface DocumentParser {
    fun supports(file: File): Boolean
    fun parse(file: File): Document
}

interface TextChunker {
    fun chunk(text: String): List<String>
}
