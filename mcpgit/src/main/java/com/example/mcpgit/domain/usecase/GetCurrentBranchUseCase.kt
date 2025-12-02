package com.example.mcpgit.domain.usecase

import com.example.mcpgit.domain.repository.GitRepository

class GetCurrentBranchUseCase(
    private val gitRepository: GitRepository
) {
    suspend operator fun invoke(): String {
        return gitRepository.getCurrentBranch()
    }
}
