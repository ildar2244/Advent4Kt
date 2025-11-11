package com.example.tgbot.domain.model.ai

/**
 * Перечисление доступных моделей HuggingFace с их конфигурацией.
 *
 * @property displayName Наименование модели для отображения пользователю
 * @property modelId Идентификатор модели для API запросов (формат: owner/model-name)
 */
enum class HuggingFaceModel(
    val displayName: String,
    val modelId: String
) {
    SAO_10K(
        displayName = "Sao10K/L3-8B-Stheno",
        modelId = "Sao10K/L3-8B-Stheno-v3.2"
    ),

    MINI_MAX_AI(
        displayName = "MiniMaxAI",
        modelId = "MiniMaxAI/MiniMax-M2"
    ),

    QWEN2_5_7B(
        displayName = "Qwen2.5-7B",
        modelId = "Qwen/Qwen2.5-7B-Instruct"
    ),

    LLAMA_3_2_1B(
        displayName = "Llama 3.2 1B",
        modelId = "meta-llama/Llama-3.2-1B-Instruct"
    ),

    SWISS_APERTUS(
        displayName = "Swiss-ai/Apertus",
        modelId = "swiss-ai/Apertus-8B-Instruct-2509"
    );

    companion object {
        /**
         * Модель по умолчанию.
         */
        val DEFAULT = SAO_10K

        /**
         * Поиск модели по идентификатору.
         *
         * @param modelId Идентификатор модели (например, "microsoft/DialoGPT-medium")
         * @return Модель или null, если не найдена
         */
        fun findByModelId(modelId: String): HuggingFaceModel? {
            return values().find { it.modelId == modelId }
        }
    }
}

