package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.Message
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.repository.AiRepository
import com.example.tgbot.domain.repository.TelegramRepository

/**
 * Use case для обработки обычных текстовых сообщений (не команд).
 * - Если AI-модель выбрана: отправляет запрос к AI и возвращает ответ
 * - Если модель не выбрана: работает в эхо-режиме
 */
class HandleMessageUseCase(
    private val telegramRepository: TelegramRepository,
    private val aiRepository: AiRepository
) {
    /**
     * Обрабатывает входящее сообщение.
     * Проверяет наличие активной AI-сессии и направляет обработку соответствующим образом.
     *
     * @param message Входящее сообщение от пользователя
     */
    suspend operator fun invoke(message: Message) {
        val userText = message.text ?: return
        val session = SessionManager.getSession(message.chatId)

        // Если модель не выбрана, работаем в эхо-режиме
        if (session.selectedModel == null) {
            telegramRepository.sendMessage(message.chatId, userText)
            return
        }

        // Режим AI-консультации
        handleAiMessage(message.chatId, userText, session.selectedModel!!)
    }

    /**
     * Обрабатывает сообщение в режиме AI-консультации.
     * Отправляет запрос к AI-модели и возвращает ответ пользователю.
     *
     * @param chatId ID чата
     * @param userText Текст сообщения от пользователя
     * @param model Выбранная AI-модель
     */
    private suspend fun handleAiMessage(chatId: Long, userText: String, model: com.example.tgbot.domain.model.ai.AiModel) {
        try {
            // Добавляем сообщение пользователя в историю
            SessionManager.addMessage(
                chatId,
                AiMessage(role = MessageRole.USER, content = userText)
            )

            // Получаем историю диалога
            val session = SessionManager.getSession(chatId)
            val conversationHistory = session.conversationHistory.toList()

            // Создаем запрос к AI
            val aiRequest = AiRequest(
                model = model,
                messages = conversationHistory,
                temperature = 0.7
            )

            // Отправляем запрос к AI
            val aiResponse = aiRepository.sendMessage(aiRequest)

            // Добавляем ответ AI в историю
            SessionManager.addMessage(
                chatId,
                AiMessage(role = MessageRole.ASSISTANT, content = aiResponse.content)
            )

            // Отправляем ответ пользователю
            telegramRepository.sendMessage(chatId, aiResponse.content)

        } catch (e: Exception) {
            // Обрабатываем ошибки и отправляем понятное сообщение пользователю
            telegramRepository.sendMessage(
                chatId,
                "Произошла ошибка при обращении к AI:\n${e.message}\n\nПопробуйте еще раз или используйте /stop для выхода."
            )
        }
    }
}
