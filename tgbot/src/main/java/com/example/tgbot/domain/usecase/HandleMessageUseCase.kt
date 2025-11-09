package com.example.tgbot.domain.usecase

import com.example.tgbot.domain.model.Experts
import com.example.tgbot.domain.model.Message
import com.example.tgbot.domain.model.Scenario
import com.example.tgbot.domain.model.SessionManager
import com.example.tgbot.domain.model.SystemPrompts
import com.example.tgbot.domain.model.ai.AiMessage
import com.example.tgbot.domain.model.ai.AiRequest
import com.example.tgbot.domain.model.ai.MessageRole
import com.example.tgbot.domain.repository.AiRepository
import com.example.tgbot.domain.repository.TelegramRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Use case для обработки обычных текстовых сообщений (не команд).
 *
 * Режимы работы:
 * - Если AI-модель не выбрана: работает в эхо-режиме (возвращает текст пользователя)
 * - Если модель выбрана: отправляет запрос к AI с учетом текущего сценария
 *
 * Поддержка сценариев:
 * - FREE_CHAT: обычный диалог без дополнительных промптов
 * - JSON_FORMAT, CONSULTANT, STEP_BY_STEP: добавляется специальный system prompt
 * - EXPERTS: параллельные запросы к нескольким экспертам с разными промптами
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

        // Проверяем текущий сценарий
        when (session.currentScenario) {
            Scenario.EXPERTS -> handleExpertsScenario(message.chatId, userText, session.selectedModel!!)
            else -> handleAiMessage(message.chatId, userText, session.selectedModel!!, session.currentScenario)
        }
    }

    /**
     * Обрабатывает сообщение в режиме AI-консультации.
     * Отправляет запрос к AI-модели и возвращает ответ пользователю.
     * Применяет system prompt в зависимости от выбранного сценария.
     *
     * @param chatId ID чата
     * @param userText Текст сообщения от пользователя
     * @param model Выбранная AI-модель
     * @param scenario Текущий сценарий взаимодействия
     */
    private suspend fun handleAiMessage(
        chatId: Long,
        userText: String,
        model: com.example.tgbot.domain.model.ai.AiModel,
        scenario: Scenario
    ) {
        try {
            // Добавляем сообщение пользователя в историю
            SessionManager.addMessage(
                chatId,
                AiMessage(role = MessageRole.USER, content = userText)
            )

            // Получаем историю диалога
            val session = SessionManager.getSession(chatId)
            val conversationHistory = session.conversationHistory.toMutableList()

            // Добавляем system prompt в зависимости от сценария (если нужно)
            val systemPrompt = getSystemPromptForScenario(scenario)
            if (systemPrompt != null) {
                // Добавляем system prompt в начало списка сообщений (перед первым USER сообщением)
                // Находим индекс первого SYSTEM сообщения
                val firstSystemIndex = conversationHistory.indexOfFirst { it.role == MessageRole.SYSTEM }
                if (firstSystemIndex != -1) {
                    // Заменяем существующий system prompt
                    conversationHistory[firstSystemIndex] = AiMessage(
                        role = MessageRole.SYSTEM,
                        content = systemPrompt
                    )
                } else {
                    // Добавляем новый system prompt в начало
                    conversationHistory.add(0, AiMessage(
                        role = MessageRole.SYSTEM,
                        content = systemPrompt
                    ))
                }
            }

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

    /**
     * Возвращает system prompt для указанного сценария.
     *
     * @param scenario Сценарий взаимодействия
     * @return System prompt или null для FREE_CHAT
     */
    private fun getSystemPromptForScenario(scenario: Scenario): String? {
        return when (scenario) {
            Scenario.FREE_CHAT -> null
            Scenario.JSON_FORMAT -> SystemPrompts.JSON_FORMAT
            Scenario.CONSULTANT -> SystemPrompts.CONSULTANT
            Scenario.STEP_BY_STEP -> SystemPrompts.STEP_BY_STEP
            Scenario.EXPERTS -> null // Этот сценарий обрабатывается отдельно
        }
    }

    /**
     * Обрабатывает сообщение в сценарии "Эксперты".
     * Отправляет несколько параллельных запросов к AI с разными system prompts.
     * Количество запросов определяется динамически размером списка Experts.list.
     *
     * @param chatId ID чата
     * @param userText Текст сообщения от пользователя
     * @param model Выбранная AI-модель
     */
    private suspend fun handleExpertsScenario(
        chatId: Long,
        userText: String,
        model: com.example.tgbot.domain.model.ai.AiModel
    ) {
        try {
            // Добавляем сообщение пользователя в историю
            SessionManager.addMessage(
                chatId,
                AiMessage(role = MessageRole.USER, content = userText)
            )

            // Получаем историю диалога (без system промптов для экспертов)
            val session = SessionManager.getSession(chatId)
            val baseHistory = session.conversationHistory.toList()

            // Запускаем параллельные запросы к AI для каждого эксперта
            coroutineScope {
                val deferredResponses = Experts.list.map { expert ->
                    async {
                        try {
                            // Создаем историю с system prompt этого эксперта
                            val expertHistory = mutableListOf<AiMessage>()

                            // Добавляем system prompt эксперта
                            expertHistory.add(
                                AiMessage(
                                    role = MessageRole.SYSTEM,
                                    content = expert.systemPrompt
                                )
                            )

                            // Добавляем остальную историю диалога
                            expertHistory.addAll(baseHistory)

                            // Создаем запрос к AI
                            val aiRequest = AiRequest(
                                model = model,
                                messages = expertHistory,
                                temperature = 0.7
                            )

                            // Отправляем запрос к AI
                            val aiResponse = aiRepository.sendMessage(aiRequest)

                            // Возвращаем пару: имя эксперта и ответ
                            expert.name to aiResponse.content
                        } catch (e: Exception) {
                            // В случае ошибки возвращаем сообщение об ошибке
                            expert.name to "Ошибка: ${e.message}"
                        }
                    }
                }

                // Получаем ответы по мере их поступления
                deferredResponses.forEach { deferred ->
                    val (expertName, response) = deferred.await()

                    // Отправляем ответ каждого эксперта отдельным сообщением
                    telegramRepository.sendMessage(
                        chatId,
                        "$expertName:\n\n$response"
                    )
                }
            }

            // Добавляем объединенный ответ в историю (для контекста)
            // Можно сохранить только факт обращения к экспертам
            SessionManager.addMessage(
                chatId,
                AiMessage(
                    role = MessageRole.ASSISTANT,
                    content = "[Получены ответы от ${Experts.list.size} экспертов]"
                )
            )

        } catch (e: Exception) {
            // Обрабатываем ошибки и отправляем понятное сообщение пользователю
            telegramRepository.sendMessage(
                chatId,
                "Произошла ошибка при обращении к экспертам:\n${e.message}\n\nПопробуйте еще раз или используйте /stop для выхода."
            )
        }
    }
}
