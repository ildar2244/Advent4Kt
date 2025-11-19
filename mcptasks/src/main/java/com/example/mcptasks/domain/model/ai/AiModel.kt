package com.example.mcptasks.domain.model.ai

/**
 * Перечисление доступных AI-моделей с их конфигурацией.
 *
 * @property displayName Наименование модели для отображения
 * @property modelId Идентификатор модели для API запросов
 * @property endpoint URL эндпоинта для запросов к модели
 */
enum class AiModel(
    val displayName: String,
    val modelId: String,
    val endpoint: String
) {
    /**
     * YandexGPT Lite от Yandex Cloud.
     */
    YANDEX_GPT_LITE(
        displayName = "YandexGPT Lite",
        modelId = "yandexgpt-lite",
        endpoint = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"
    );

    companion object {
        /**
         * Модель по умолчанию.
         */
        val DEFAULT = YANDEX_GPT_LITE
    }
}
