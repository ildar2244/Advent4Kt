package com.example.mcptasks.data.remote

import com.example.mcptasks.data.remote.dto.telegram.SendMessageRequest
import com.example.mcptasks.data.remote.dto.telegram.SendMessageResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * HTTP-клиент для работы с Telegram Bot API.
 *
 * @property client Настроенный HTTP-клиент Ktor
 * @property botToken Токен Telegram бота
 */
class TelegramApiClient(
    private val client: HttpClient,
    private val botToken: String
) {
    private val baseUrl = "https://api.telegram.org/bot$botToken"

    /**
     * Отправляет сообщение в Telegram канал или чат.
     *
     * @param chatId Идентификатор чата или канала (может быть числовой ID или @username)
     * @param text Текст сообщения
     * @param parseMode Режим парсинга: "Markdown" или "HTML" (опционально)
     * @return Результат отправки
     */
    suspend fun sendMessage(
        chatId: String,
        text: String,
        parseMode: String? = "Markdown"
    ): SendMessageResponse {
        val request = SendMessageRequest(
            chatId = chatId,
            text = text,
            parseMode = parseMode
        )

        return client.post("$baseUrl/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
