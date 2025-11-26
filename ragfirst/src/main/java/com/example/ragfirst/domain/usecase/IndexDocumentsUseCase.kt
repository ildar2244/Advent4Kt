package com.example.ragfirst.domain.usecase

import com.example.ragfirst.domain.model.Chunk
import com.example.ragfirst.domain.model.Document
import com.example.ragfirst.domain.model.Embedding
import com.example.ragfirst.domain.repository.OllamaRepository
import com.example.ragfirst.domain.repository.RagRepository
import kotlinx.coroutines.delay
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

            var successfulChunks = 0
            var failedChunks = 0

            chunks.forEachIndexed { index, chunkText ->
                try {
                    val chunk = Chunk(
                        documentId = documentId,
                        content = chunkText,
                        chunkIndex = index
                    )
                    val chunkId = ragRepository.saveChunk(chunk)

                    val embedding = ollamaRepository.generateEmbedding(chunkText)
                    ragRepository.saveEmbedding(Embedding(chunkId, embedding))
                    successfulChunks++

                    // Rate limiting: 100ms задержка между запросами
                    if (index < chunks.size - 1) {
                        delay(100)
                    }

                    if ((index + 1) % 10 == 0) {
                        logger.info("Progress: ${index + 1}/${chunks.size} chunks processed")
                    }
                } catch (e: Exception) {
                    failedChunks++
                    logger.error("Failed to process chunk $index for file ${file.name}: ${e.message}")
                    // Продолжаем обработку остальных чанков
                }
            }

            if (failedChunks > 0) {
                val errorMsg = "Partially indexed ${file.name}: $successfulChunks/${chunks.size} chunks succeeded, $failedChunks failed"
                logger.warn(errorMsg)
                throw PartialIndexingException(errorMsg, successfulChunks, failedChunks)
            }

            logger.info("Successfully indexed file: ${file.name} ($successfulChunks chunks)")
        } catch (e: PartialIndexingException) {
            throw e // Пробрасываем дальше для CLI
        } catch (e: Exception) {
            logger.error("Failed to index file: ${file.name}", e)
            throw e // Пробрасываем для точной отчётности
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

class PartialIndexingException(
    message: String,
    val successfulChunks: Int,
    val failedChunks: Int
) : Exception(message)

