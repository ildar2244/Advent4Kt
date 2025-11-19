package com.example.mcptasks.domain.model

import java.time.LocalDateTime

/**
 * Доменная модель задачи
 *
 * @property id Уникальный идентификатор задачи
 * @property title Название задачи
 * @property description Описание задачи
 * @property createdAt Дата и время создания задачи
 */
data class Task(
    val id: Long,
    val title: String,
    val description: String,
    val createdAt: LocalDateTime
)
