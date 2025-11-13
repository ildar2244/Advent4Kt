package com.example.tgbot.domain.model

/**
 * Перечисление доступных сценариев взаимодействия с AI.
 *
 * @property displayName Наименование сценария для отображения пользователю
 * @property command Команда для активации сценария (без '/')
 * @property callbackData Данные для callback при выборе через инлайн-кнопку
 */
enum class Scenario(
    val displayName: String,
    val command: String,
    val callbackData: String
) {
    /**
     * Просто чат - отправка запроса к AI без модификаций.
     */
    FREE_CHAT(
        displayName = "Просто чат",
        command = "free-chat",
        callbackData = "scenario_free_chat"
    ),

    /**
     * Формат JSON - AI отвечает в формате JSON.
     */
    JSON_FORMAT(
        displayName = "Формат JSON",
        command = "json-format",
        callbackData = "scenario_json_format"
    ),

    /**
     * Консультант - AI работает в режиме консультанта.
     */
    CONSULTANT(
        displayName = "Консультант",
        command = "consultant",
        callbackData = "scenario_consultant"
    ),

    /**
     * Решай пошагово - AI решает задачу пошагово.
     */
    STEP_BY_STEP(
        displayName = "Решай пошагово",
        command = "step-by-step",
        callbackData = "scenario_step_by_step"
    ),

    /**
     * С компрессией - режим с автоматическим сжатием истории при достижении лимита токенов.
     * Оптимизирован для YandexGPT Lite (лимит ~7372 токена).
     */
    COMPRESSION(
        displayName = "🗜️ С компрессией",
        command = "compression",
        callbackData = "scenario_compression"
    ),

    /**
     * Эксперты - запрос обрабатывается несколькими экспертами параллельно.
     */
    EXPERTS(
        displayName = "Эксперты",
        command = "experts",
        callbackData = "scenario_experts"
    );

    companion object {
        /**
         * Находит сценарий по callback данным.
         *
         * @param callbackData Callback данные от инлайн-кнопки
         * @return Найденный сценарий или null
         */
        fun findByCallbackData(callbackData: String): Scenario? {
            return values().find { it.callbackData == callbackData }
        }

        /**
         * Находит сценарий по команде.
         *
         * @param command Команда (с '/' или без)
         * @return Найденный сценарий или null
         */
        fun findByCommand(command: String): Scenario? {
            val normalizedCommand = command.removePrefix("/")
            return values().find { it.command == normalizedCommand }
        }

        /**
         * Сценарий по умолчанию.
         */
        val DEFAULT = FREE_CHAT
    }
}
