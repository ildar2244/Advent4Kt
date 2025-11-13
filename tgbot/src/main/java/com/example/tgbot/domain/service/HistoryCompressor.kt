package com.example.tgbot.domain.service

import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiModel
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.HuggingFaceModel
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.repository.AiRepository

/**
 * Сервис для сжатия истории диалога путём суммаризации.
 * Использует выбранную AI-модель для создания краткого резюме всей истории.
 */
class HistoryCompressor(
    private val aiRepository: AiRepository
) {

    /**
     * Сжимает историю диалога, создавая краткое резюме.
     *
     * @param history Список сообщений для сжатия (исключая системные промпты)
     * @param model AI-модель для выполнения суммаризации
     * @param temperature Температура генерации (если не указана, используется 0.3 для более точного резюме)
     * @param huggingFaceModel Модель HuggingFace (если используется HUGGING_FACE)
     * @return AiMessage с ролью ASSISTANT, содержащий сжатое резюме истории
     */
    suspend fun compressHistory(
        history: List<AiMessage>,
        model: AiModel,
        temperature: Double? = null,
        huggingFaceModel: HuggingFaceModel? = null
    ): AiMessage {
        // Формируем текст диалога для суммаризации
        val conversationText = history
            .filter { it.role != MessageRole.SYSTEM } // Исключаем системные промпты
            .joinToString("\n\n") { message ->
                val roleLabel = when (message.role) {
                    MessageRole.USER -> "Пользователь"
                    MessageRole.ASSISTANT -> "Ассистент"
                    MessageRole.SYSTEM -> "Система"
                }
                "$roleLabel: ${message.content}"
            }

        // Формируем промпт для суммаризации
        val summaryPrompt0 = """
            Сделай краткое и информативное резюме следующего диалога.
            Сохрани все ключевые темы, важные детали и контекст разговора.
            Резюме должно быть достаточно подробным, чтобы продолжить разговор на основе этого резюме.

            Диалог:
            $conversationText

            Напиши резюме в 3-5 абзацах, структурировав информацию логически.
        """.trimIndent()
        val summaryPrompt = """
            Ты - эксперт по суммаризации диалогов. 
            Сохрани все ключевые темы, важные детали и контекст разговора. 
            
            Диалог:
            $conversationText
            
            Напиши резюме в 3-5 предложениях, используй нейтральный тон и структурированный формат.
        """.trimIndent()

        // Создаём запрос для суммаризации
        val request = AiRequest(
            model = model,
            messages = listOf(
                AiMessage(
                    role = MessageRole.USER,
                    content = summaryPrompt
                )
            ),
            temperature = temperature ?: 0.3, // Низкая температура для точности
            huggingFaceModel = huggingFaceModel
        )

        // Отправляем запрос и получаем резюме
        val response = aiRepository.sendMessage(request)

        // Возвращаем сжатое резюме как сообщение ассистента с префиксом
        return AiMessage(
            role = MessageRole.ASSISTANT,
            content = "📦 [SUMMARY] ${response.content}"
        )
    }
}
