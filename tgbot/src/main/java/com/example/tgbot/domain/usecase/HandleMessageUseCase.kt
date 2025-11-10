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
 * - FREE_CHAT: одиночный запрос без истории и дополнительных промптов
 * - JSON_FORMAT: одиночный запрос с system prompt для JSON-ответов
 * - CONSULTANT: использует историю диалога (до 20 последних сообщений) для контекстных ответов
 * - STEP_BY_STEP: одиночный запрос с system prompt для пошагового решения
 * - EXPERTS: параллельные независимые запросы к нескольким экспертам (без истории)
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
     * Для сценария CONSULTANT используется история сообщений, для остальных - одиночные запросы.
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
            val session = SessionManager.getSession(chatId)
            val isConsultantMode = scenario == Scenario.CONSULTANT

            // Создаем сообщение пользователя
            val userMessage = AiMessage(role = MessageRole.USER, content = userText)

            // Определяем: сохранять ли в историю
            val conversationHistory: MutableList<AiMessage> = if (isConsultantMode) {
                // CONSULTANT: добавляем в историю и используем её
                SessionManager.addMessage(chatId, userMessage)
                session.conversationHistory.toMutableList()
            } else {
                // Остальные сценарии: создаем временный список только для этого запроса
                mutableListOf(userMessage)
            }

            // Добавляем system prompt в зависимости от сценария (если нужно)
            val systemPrompt = getSystemPromptForScenario(scenario)
            if (systemPrompt != null) {
                if (isConsultantMode) {
                    // Для CONSULTANT: обновляем/добавляем в начало истории
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
                } else {
                    // Для остальных сценариев: просто добавляем в начало временного списка
                    conversationHistory.add(0, AiMessage(
                        role = MessageRole.SYSTEM,
                        content = systemPrompt
                    ))
                }
            }

            // Создаем запрос к AI с температурой из сессии
            val aiRequest = AiRequest(
                model = model,
                messages = conversationHistory,
                temperature = session.temperature
            )

            // Отправляем запрос к AI
            val aiResponse = aiRepository.sendMessage(aiRequest)

            // Добавляем ответ AI в историю только для CONSULTANT
            if (isConsultantMode) {
                SessionManager.addMessage(
                    chatId,
                    AiMessage(role = MessageRole.ASSISTANT, content = aiResponse.content)
                )
            }

            // Формируем ответ с информацией о модели и temperature
            val responseText = buildString {
                append(aiResponse.content)
                append("\n\n")
                append("```\n")
                append("model: ${model.displayName}\n")
                append("temperature: ${session.temperature}\n")
                append("```")
            }

            // Отправляем ответ пользователю
            telegramRepository.sendMessage(chatId, responseText)

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
     * Каждый запрос независим (без истории сообщений).
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
            val session = SessionManager.getSession(chatId)

            // Запускаем параллельные запросы к AI для каждого эксперта
            coroutineScope {
                val deferredResponses = Experts.list.map { expert ->
                    async {
                        try {
                            // Создаем независимый запрос для каждого эксперта
                            val expertHistory = mutableListOf<AiMessage>()

                            // Добавляем system prompt эксперта
                            expertHistory.add(
                                AiMessage(
                                    role = MessageRole.SYSTEM,
                                    content = expert.systemPrompt
                                )
                            )

                            // Добавляем только ТЕКУЩЕЕ сообщение пользователя
                            expertHistory.add(
                                AiMessage(
                                    role = MessageRole.USER,
                                    content = userText
                                )
                            )

                            // Создаем запрос к AI с температурой из сессии
                            val aiRequest = AiRequest(
                                model = model,
                                messages = expertHistory,
                                temperature = session.temperature
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

                    // Формируем ответ с информацией о модели и temperature
                    val responseText = buildString {
                        append("$expertName:\n\n")
                        append(response)
                        append("\n\n")
                        append("```\n")
                        append("model: ${model.displayName}\n")
                        append("temperature: ${session.temperature}\n")
                        append("```")
                    }

                    // Отправляем ответ каждого эксперта отдельным сообщением
                    telegramRepository.sendMessage(chatId, responseText)
                }
            }

            // Сценарий EXPERTS не использует историю - каждый запрос независим

        } catch (e: Exception) {
            // Обрабатываем ошибки и отправляем понятное сообщение пользователю
            telegramRepository.sendMessage(
                chatId,
                "Произошла ошибка при обращении к экспертам:\n${e.message}\n\nПопробуйте еще раз или используйте /stop для выхода."
            )
        }
    }
}
