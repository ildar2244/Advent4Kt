package com.example.ragfirst.domain.usecase

import com.example.ragfirst.domain.repository.RagRepository
import org.slf4j.LoggerFactory

class ClearIndexUseCase(
    private val ragRepository: RagRepository
) {
    private val logger = LoggerFactory.getLogger(ClearIndexUseCase::class.java)

    suspend fun execute() {
        logger.info("Clearing index...")
        ragRepository.clearIndex()
        logger.info("Index cleared successfully")
    }
}
