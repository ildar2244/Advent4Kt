package com.example.ragfirst.domain.usecase

import com.example.ragfirst.domain.repository.IndexStatistics
import com.example.ragfirst.domain.repository.RagRepository

class GetStatisticsUseCase(
    private val ragRepository: RagRepository
) {
    suspend fun execute(): IndexStatistics {
        return ragRepository.getStatistics()
    }
}
