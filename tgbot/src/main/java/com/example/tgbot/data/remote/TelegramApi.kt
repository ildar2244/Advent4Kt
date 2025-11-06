package com.example.tgbot.data.remote

import com.example.tgbot.data.remote.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * HTTP-клиент для работы с Telegram Bot API.
 * Выполняет прямые HTTP-запросы к API Telegram.
 */
class TelegramApi(
    private val client: HttpClient,
    private val token: String
) {
    private val baseUrl = "https://api.telegram.org/bot$token"

    /**
     * Получает обновления от Telegram через long polling.
     *
     * @param offset Идентификатор первого обновления для получения
     * @param timeout Таймаут long polling в секундах (по умолчанию 30)
     * @return Ответ с массивом обновлений
     */
    suspend fun getUpdates(offset: Long?, timeout: Int = 30): GetUpdatesResponse {
        return client.get("$baseUrl/getUpdates") {
            parameter("offset", offset)
            parameter("timeout", timeout)
        }.body()
    }

    /**
     * Отправляет сообщение (с опциональной инлайн-клавиатурой).
     *
     * @param request Запрос на отправку сообщения
     * @return Ответ с отправленным сообщением
     */
    suspend fun sendMessage(request: SendMessageRequest): SendMessageResponse {
        return client.post("$baseUrl/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Редактирует текст существующего сообщения.
     *
     * @param request Запрос на редактирование сообщения
     * @return Ответ с отредактированным сообщением
     */
    suspend fun editMessageText(request: EditMessageTextRequest): EditMessageResponse {
        return client.post("$baseUrl/editMessageText") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Отвечает на callback-запрос от инлайн-кнопки.
     * Убирает индикатор загрузки на кнопке в клиенте пользователя.
     *
     * @param request Запрос с ID callback'а
     * @return Ответ об успешности операции
     */
    suspend fun answerCallbackQuery(request: AnswerCallbackQueryRequest): AnswerCallbackQueryResponse {
        return client.post("$baseUrl/answerCallbackQuery") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
