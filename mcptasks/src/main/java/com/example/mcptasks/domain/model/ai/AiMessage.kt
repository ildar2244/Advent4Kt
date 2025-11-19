package com.example.mcptasks.domain.model.ai

/**
 * Роль участника в диалоге с AI.
 */
enum class MessageRole {
    SYSTEM,    // Системное сообщение (инструкции для модели)
    USER,      // Сообщение от пользователя
    ASSISTANT  // Ответ ассистента (AI)
}

/**
 * Сообщение в диалоге с AI.
 *
 * @property role Роль отправителя сообщения
 * @property content Текстовое содержимое сообщения
 */
data class AiMessage(
    val role: MessageRole,
    val content: String
)
