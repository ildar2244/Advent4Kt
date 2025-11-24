package com.example.ragfirst.domain.model

data class Embedding(
    val chunkId: Int,
    val vector: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Embedding

        if (chunkId != other.chunkId) return false
        if (!vector.contentEquals(other.vector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chunkId
        result = 31 * result + vector.contentHashCode()
        return result
    }
}
