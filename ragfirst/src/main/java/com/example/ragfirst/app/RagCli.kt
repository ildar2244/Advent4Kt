package com.example.ragfirst.app

import com.example.ragfirst.data.local.db.DatabaseFactory
import com.example.ragfirst.data.local.parser.CompositeDocumentParser
import com.example.ragfirst.data.local.parser.MarkdownParser
import com.example.ragfirst.data.local.parser.PdfParser
import com.example.ragfirst.data.remote.OllamaApiClient
import com.example.ragfirst.data.repository.OllamaRepositoryImpl
import com.example.ragfirst.data.repository.RagRepositoryImpl
import com.example.ragfirst.domain.usecase.*
import com.example.ragfirst.util.RecursiveTextChunker
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    DatabaseFactory.init()

    val ollamaClient = OllamaApiClient()
    val ollamaRepository = OllamaRepositoryImpl(ollamaClient)
    val ragRepository = RagRepositoryImpl()

    val documentParser = CompositeDocumentParser(
        listOf(MarkdownParser(), PdfParser())
    )
    val textChunker = RecursiveTextChunker(chunkSize = 500, overlap = 50)

    val indexUseCase = IndexDocumentsUseCase(ragRepository, ollamaRepository, documentParser, textChunker)
    val searchUseCase = SearchSimilarUseCase(ragRepository, ollamaRepository)
    val clearUseCase = ClearIndexUseCase(ragRepository)
    val statsUseCase = GetStatisticsUseCase(ragRepository)

    runBlocking {
        when (val command = args[0].lowercase()) {
            "index" -> {
                if (args.size < 2) {
                    println("Usage: index <directory>")
                    return@runBlocking
                }
                val directory = args[1]
                println("Indexing documents from: $directory")
                indexUseCase.execute(directory)
            }

            "index-file" -> {
                if (args.size < 2) {
                    println("Usage: index-file <file-path>")
                    return@runBlocking
                }
                val filePath = args[1]
                println("Indexing single file: $filePath")
                try {
                    indexUseCase.executeSingleFile(filePath)
                    println("✓ File indexed successfully!")
                } catch (e: IllegalArgumentException) {
                    println("✗ Error: ${e.message}")
                } catch (e: Exception) {
                    println("✗ Failed to index file: ${e.message}")
                    e.printStackTrace()
                }
            }

            "search" -> {
                if (args.size < 2) {
                    println("Usage: search <query>")
                    return@runBlocking
                }
                val query = args.drop(1).joinToString(" ")
                println("Searching for: '$query'\n")

                val results = searchUseCase.execute(query, topK = 5, threshold = 0.7f)

                if (results.isEmpty()) {
                    println("No results found.")
                } else {
                    results.forEachIndexed { index, result ->
                        println("Result ${index + 1} (similarity: ${"%.3f".format(result.similarity)})")
                        println("Document: ${result.document.path}")
                        println("Chunk #${result.chunk.chunkIndex}")
                        println("Content: ${result.chunk.content.take(200)}...")
                        println()
                    }
                }
            }

            "stats" -> {
                val stats = statsUseCase.execute()
                println("=== RAG Index Statistics ===")
                println("Documents: ${stats.documentsCount}")
                println("Chunks: ${stats.chunksCount}")
                println("Embeddings: ${stats.embeddingsCount}")
            }

            "clear" -> {
                println("Clearing index...")
                clearUseCase.execute()
                println("Index cleared successfully.")
            }

            else -> {
                println("Unknown command: $command")
                printUsage()
            }
        }
    }

    ollamaClient.close()
}

private fun printUsage() {
    println("""
        RAG CLI Usage:

        index <directory>      Index all documents in the specified directory
        index-file <path>      Index a single file (supports .md, .markdown, .pdf)
        search <query>         Search for documents similar to the query
        stats                  Show index statistics
        clear                  Clear the entire index

        Examples:
        ./gradlew :ragfirst:run --args="index /path/to/docs"
        ./gradlew :ragfirst:run --args="index-file CLAUDE.md"
        ./gradlew :ragfirst:run --args="index-file /Users/../AndroidProjects/../README.md"
        ./gradlew :ragfirst:run --args="search 'how to use RAG'"
    """.trimIndent())
}
