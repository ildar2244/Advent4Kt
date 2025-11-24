package com.example.ragfirst.util

import kotlin.math.sqrt

object SimilarityCalculator {
    fun cosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
        require(vector1.size == vector2.size) {
            "Vectors must have the same dimension"
        }

        var dotProduct = 0.0
        var magnitude1 = 0.0
        var magnitude2 = 0.0

        for (i in vector1.indices) {
            dotProduct += vector1[i] * vector2[i]
            magnitude1 += vector1[i] * vector1[i]
            magnitude2 += vector2[i] * vector2[i]
        }

        magnitude1 = sqrt(magnitude1)
        magnitude2 = sqrt(magnitude2)

        return if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            0f
        } else {
            (dotProduct / (magnitude1 * magnitude2)).toFloat()
        }
    }
}
