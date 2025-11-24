package com.example.ragfirst.util

import com.example.ragfirst.domain.usecase.TextChunker

class RecursiveTextChunker(
    private val chunkSize: Int = 500,
    private val overlap: Int = 50
) : TextChunker {
    private val separators = listOf("\n\n", "\n", ". ", " ", "")

    override fun chunk(text: String): List<String> {
        if (text.length <= chunkSize) {
            return listOf(text)
        }

        return splitRecursively(text, separators)
    }

    private fun splitRecursively(text: String, separators: List<String>): List<String> {
        if (text.length <= chunkSize) {
            return listOf(text)
        }

        if (separators.isEmpty()) {
            return text.chunked(chunkSize)
        }

        val separator = separators.first()
        val parts = if (separator.isEmpty()) {
            text.chunked(chunkSize)
        } else {
            text.split(separator)
        }

        val chunks = mutableListOf<String>()
        var currentChunk = ""

        for (part in parts) {
            val testChunk = if (currentChunk.isEmpty()) {
                part
            } else {
                "$currentChunk$separator$part"
            }

            if (testChunk.length <= chunkSize) {
                currentChunk = testChunk
            } else {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk)
                    currentChunk = ""
                }

                if (part.length > chunkSize) {
                    chunks.addAll(splitRecursively(part, separators.drop(1)))
                } else {
                    currentChunk = part
                }
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }

        return addOverlap(chunks)
    }

    private fun addOverlap(chunks: List<String>): List<String> {
        if (chunks.size <= 1 || overlap == 0) {
            return chunks
        }

        val chunksWithOverlap = mutableListOf<String>()

        for (i in chunks.indices) {
            val chunk = chunks[i]
            val overlapText = if (i > 0) {
                val prevChunk = chunks[i - 1]
                val words = prevChunk.split(" ")
                words.takeLast(minOf(overlap, words.size)).joinToString(" ")
            } else {
                ""
            }

            val finalChunk = if (overlapText.isNotEmpty()) {
                "$overlapText $chunk"
            } else {
                chunk
            }

            chunksWithOverlap.add(finalChunk)
        }

        return chunksWithOverlap
    }
}
