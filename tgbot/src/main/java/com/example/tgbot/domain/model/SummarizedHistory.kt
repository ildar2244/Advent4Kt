package com.example.tgbot.domain.model

import java.time.Instant

/**
 * Доменная модель для результата суммаризации диалога
 *
 * @property id уникальный идентификатор записи
 * @property text текст суммаризации
 * @property timestamp дата и время создания записи
 */
data class SummarizedHistory(
    val id: Int,
    val text: String,
    val timestamp: Instant
)
