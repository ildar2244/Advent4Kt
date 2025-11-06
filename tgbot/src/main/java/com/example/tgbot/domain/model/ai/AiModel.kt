package com.example.tgbot.domain.model.ai

/**
 * Перечисление доступных AI-моделей с их конфигурацией.
 *
 * @property displayName Наименование модели для отображения пользователю
 * @property modelId Идентификатор модели для API запросов
 * @property endpoint URL эндпоинта для запросов к модели через ProxyAPI
 */
enum class AiModel(
    val displayName: String,
    val modelId: String,
    val endpoint: String
) {
    /**
     * GPT-4o Mini от OpenAI (базовая модель по умолчанию).
     */
    GPT_4O_MINI(
        displayName = "GPT-4o Mini",
        modelId = "gpt-4o-mini",
        endpoint = "https://api.proxyapi.ru/openai/v1/chat/completions"
    ),

    /**
     * Claude 3.5 Haiku от Anthropic.
     */
    CLAUDE_HAIKU(
        displayName = "Claude 3.5 Haiku",
        modelId = "claude-3-5-haiku-20241022",
        endpoint = "https://api.proxyapi.ru/anthropic/v1/messages"
    );

    companion object {
        /**
         * Модель по умолчанию.
         */
        val DEFAULT = GPT_4O_MINI
    }
}
