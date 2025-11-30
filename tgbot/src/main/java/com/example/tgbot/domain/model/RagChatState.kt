package com.example.tgbot.domain.model

/**
 * Состояние RAG чата для сценария RAG_CHAT.
 * Хранит последние результаты RAG-поиска для отображения источников.
 *
 * @property lastRagResults Список последних результатов RAG-поиска (обычно 3 чанка)
 */
data class RagChatState(
    val lastRagResults: List<RagSearchResult> = emptyList()
)
