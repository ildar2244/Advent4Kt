# TGBot - Telegram бот с интеграцией AI и MCP

## Обзор

TGBot - это Telegram бот, реализованный на Kotlin с использованием Clean Architecture. Бот
поддерживает интеграцию с несколькими AI моделями (OpenAI GPT-4o-mini, Anthropic Claude 3.5 Haiku,
YandexGPT Lite, HuggingFace), предоставляет систему сценариев общения, интеграцию с MCP серверами
для получения погоды и git информации, а также RAG (Retrieval-Augmented Generation) для
семантического поиска по документам.

## Архитектура

Бот построен по принципам Clean Architecture с разделением на три слоя:

### Domain Layer (`domain/`)
**Бизнес-логика и модели предметной области**

- `model/` - Бизнес-сущности:
  - `User`, `Message`, `Update` - основные сущности Telegram
  - `InlineKeyboard` - интерактивные кнопки
  - `Scenario`, `Expert` - система сценариев и экспертов
  - `Location` - геолокация пользователя
  - AI модели (GPT, Claude, YandexGPT, HuggingFace)

- `repository/` - Интерфейсы репозиториев:
  - `TelegramRepository` - работа с Telegram Bot API
  - `AiRepository` - работа с AI моделями
  - `McpRepository` - работа с MCP серверами
  - `RagRepository` - работа с RAG индексом

- `usecase/` - Бизнес-логика:
  - `HandleMessageUseCase` - обработка текстовых сообщений
  - `HandleCommandUseCase` - обработка команд бота
  - `HandleCallbackUseCase` - обработка нажатий на inline кнопки

- `util/` - Утилиты:
  - `WeatherFormatter` - форматирование погодных данных

### Data Layer (`data/`)
**Реализация работы с внешними API и хранилищами**

- `remote/dto/` - DTO для API:
  - Telegram API (Update, Message, InlineKeyboard)
  - OpenAI API (ChatCompletion, ChatMessage)
  - Claude API (Message, Content)
  - YandexGPT API (CompletionRequest, CompletionResponse)
  - HuggingFace API (ChatCompletion)
  - Location API

- `remote/` - API клиенты:
  - `TelegramApi` - HTTP клиент для Telegram Bot API
  - `OpenAiApiClient` - HTTP клиент для OpenAI API
  - `ClaudeApiClient` - HTTP клиент для Claude API через ProxyAPI
  - `YandexGptApiClient` - HTTP клиент для YandexGPT API
  - `HuggingFaceApiClient` - HTTP клиент для HuggingFace Router API
  - `McpWebSocketClient` - WebSocket клиент для MCP серверов

- `repository/` - Реализации репозиториев:
  - `TelegramRepositoryImpl` - реализация TelegramRepository
  - `AiRepositoryImpl` - реализация AiRepository с роутингом между AI провайдерами
  - `McpRepositoryImpl` - реализация McpRepository
  - `McpGitRepositoryImpl` - реализация McpGitRepository
  - `RagRepositoryImpl` - реализация RagRepository для работы с SQLite базой данных

### App Layer (`app/`)
**Точка входа в приложение**

- `TelegramBot.kt` - главный класс бота с long polling и конфигурацией HTTP клиента

## Технологии

- **Ktor Client** (v3.0.3) - HTTP клиент для API вызовов
  - CIO engine - асинхронный движок
  - ContentNegotiation - сериализация JSON
  - Logging - логирование запросов/ответов
  - HttpTimeout - таймауты для long polling

- **Kotlinx Serialization** (v1.7.3) - сериализация/десериализация JSON

- **Kotlinx Coroutines** (v1.7.3) - асинхронные операции

- **SLF4J Simple** (v2.0.9) - логирование

- **BuildConfig Plugin** (v5.3.5) - безопасное управление credentials

- **Exposed** (v0.50.1) - SQL библиотека для работы с базой данных

- **SQLite JDBC** (v3.45.1.0) - драйвер SQLite базы данных

## Функциональность

### Long Polling
Получение обновлений от Telegram Bot API с таймаутом 30 секунд.

### Управление сессиями пользователей
Хранение сессий пользователей в памяти с информацией о выбранной AI модели, текущем сценарии, истории диалога и настройках температуры.

### Команды бота

#### Основные команды
- `/start` - приветственное сообщение с кнопкой "Модели" для выбора AI модели
- `/stop` - очистка сессии и выход из режима консультации с AI

#### Управление AI моделями
- `/models` - показывает inline клавиатуру для выбора AI модели:
  - GPT-4o-mini (OpenAI)
  - Claude 3.5 Haiku (Anthropic)
  - YandexGPT Lite
  - HuggingFace Router API

- `/hf_models` - показывает inline клавиатуру для выбора конкретной модели HuggingFace:
  - Qwen/Qwen2.5-72B-Instruct
  - meta-llama/Llama-3.3-70B-Instruct
  - NousResearch/Hermes-3-Llama-3.1-8B
  - mistralai/Mixtral-8x7B-Instruct-v0.1
  - mistralai/Mistral-7B-Instruct-v0.3
  - microsoft/Phi-3.5-mini-instruct

- `/temperature` - установка параметра температуры (0.0 - 2.0) для управления случайностью ответов AI

#### Сценарии общения
- `/scenario` - показывает inline клавиатуру для выбора сценария общения (5 сценариев)
- `/free-chat` - активирует сценарий "Свободный чат" (по умолчанию, без системного промпта)
- `/json-format` - активирует сценарий "JSON формат" (AI отвечает в формате JSON)
- `/consultant` - активирует сценарий "Консультант" (AI выступает в роли консультанта)
- `/step-by-step` - активирует сценарий "Шаг за шагом" (AI решает задачи пошагово)
- `/experts` - активирует сценарий "Эксперты" (параллельные запросы к нескольким экспертам с разными промптами)

#### MCP интеграция
- `/mcp` - показывает inline клавиатуру с инструментами MCP погоды
- `/weather_tools` - список доступных MCP инструментов погоды (get_forecast, get_alerts)
- `/weather_location` - запрос геолокации пользователя и возврат прогноза погоды

#### RAG интеграция
- `/rag <запрос>` - поиск по проиндексированным документам с использованием семантического поиска:
  - Возвращает релевантные фрагменты документов
  - Показывает оценки схожести (similarity scores)
  - Отображает превью содержимого

- `/rag_stats` - статистика RAG индекса:
  - Количество документов
  - Количество фрагментов (chunks)
  - Количество эмбеддингов

- `/ask <запрос>` - RAG-усиленный запрос к AI:
  - Поиск 5 релевантных фрагментов документов
  - Формирование контекста из найденных фрагментов
  - Отправка запроса в LLM с контекстом
  - Форматированный ответ с источниками и статистикой
  - Работает со всеми AI моделями
  - Каждый запрос независим (stateless, без истории диалога)
  - Системный промпт заставляет AI отвечать только на основе предоставленных источников

- `/help-project <запрос>` - помощь по проекту через RAG + Git + GPT-4o-mini:
  - Поиск в документации проекта
  - Добавление информации о текущей git ветке
  - Использует stateless режим с зафиксированной моделью GPT-4o-mini
  - Интерактивные кнопки для просмотра содержимого источников

### Inline клавиатуры
Интерактивные кнопки с обработкой callback для выбора моделей и сценариев.

### Система сценариев

#### Встроенные сценарии
Бот предоставляет 5 встроенных сценариев с настраиваемыми системными промптами:

1. **Free Chat** - свободный чат без системного промпта
2. **JSON Format** - AI форматирует ответы в JSON
3. **Consultant** - AI действует как консультант
4. **Step by Step** - AI решает задачи пошагово с объяснениями
5. **Experts** - параллельные запросы к 3 экспертам с разными специализациями

#### Динамический выбор сценария
- Через inline клавиатуру с автогенерацией кнопок из `Scenario.values()`
- Через прямые команды (например, `/json-format`)
- Автоматический сброс сценария на FREE_CHAT при смене модели

#### Расширяемость
Простое добавление новых сценариев через редактирование enum `Scenario`:
1. Добавить новое значение в enum
2. Добавить системный промпт в объект `SystemPrompts`
3. Обновить маппинг в `getSystemPromptForScenario()`
4. Кнопки и роутинг команд обновляются автоматически

### Режим экспертов

- Параллельные AI запросы с различными системными промптами
- Динамическое количество экспертов на основе размера `Experts.list`
- Каждый ответ эксперта отправляется отдельным сообщением с префиксом имени эксперта
- Полностью настраиваемые имена и системные промпты экспертов
- Использует `coroutineScope` + `async` для конкурентных запросов

### Интеграция с AI

#### Поддерживаемые провайдеры
- **OpenAI GPT-4o-mini** через OpenAI Chat Completions API
- **Anthropic Claude 3.5 Haiku** через Claude Messages API (ProxyAPI)
- **YandexGPT Lite** через Yandex Cloud Foundation Models API
- **HuggingFace модели** через Router API (совместимый с OpenAI формат)

#### Единый интерфейс
- Унифицированный интерфейс через `AiRepository`
- Автоматический роутинг к соответствующему API клиенту на основе выбранной модели
- Отслеживание истории диалога с сообщениями system/user/assistant
- Инъекция системного промпта на основе выбранного сценария

#### Статистика ответов
Каждый ответ включает:
- Время выполнения запроса (мс)
- Использование токенов (prompt/completion/total)
- Информацию о модели

### MCP Weather интеграция

- Интеграция с модулем mcpweather для получения прогнозов погоды
- Обработка сообщений с геолокацией (Telegram location sharing)
- Отображение прогноза погоды с emoji форматированием
- Инструменты MCP:
  - `get_forecast` - прогноз по координатам (lat/lon)
  - `get_alerts` - предупреждения по штату США

### RAG интеграция

#### Полнотекстовый семантический поиск
- Поиск через проиндексированные документы с использованием Ollama embeddings
- Модель эмбеддингов: `nomic-embed-text`
- База данных: SQLite в `ragfirst/data/rag.db`

#### Команды RAG
- `/rag <запрос>` - прямой поиск с оценками схожести и превью содержимого
- `/ask <запрос>` - RAG-усиленные ответы LLM:
  - Поток: запрос → поиск 5 релевантных фрагментов → сборка контекста → отправка в LLM → форматированный ответ
  - Stateless: каждый запрос независим, без истории диалога
  - Работает со всеми AI моделями (GPT-4o Mini, Claude 3.5 Haiku, YandexGPT Lite, HuggingFace)
  - Системный промпт обеспечивает ответы только на основе источников (предотвращает галлюцинации)
  - Ответ включает: ответ LLM, список источников с процентом релевантности, статистику (время, токены, модель)

#### Индексация документов
- Выполняется через CLI модуль ragfirst: `./gradlew :ragfirst:run --args="index /path/to/docs"`
- Поддерживаемые форматы: Markdown (.md) и PDF
- Рекурсивное разбиение текста на фрагменты (500 символов, перекрытие 50 символов)
- Структура базы данных: таблицы documents, chunks, embeddings

## Конфигурация

### Файл конфигурации

Создайте файл `local.properties` в корне проекта со следующими учетными данными:

```properties
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
OPENAI_API_KEY=your_proxyapi_openai_key
CLAUDE_API_KEY=your_proxyapi_claude_key
YANDEX_GPT_API_KEY=your_yandex_cloud_api_key
YANDEX_CLOUD_FOLDER_ID=your_yandex_cloud_folder_id
HUGGING_FACE_API_KEY=your_huggingface_token
```

### API Endpoints

- **Telegram Bot API**: `https://api.telegram.org/bot{token}/`
- **OpenAI** (через ProxyAPI): `https://api.proxyapi.ru/openai/v1/chat/completions`
- **Claude** (через ProxyAPI): `https://api.proxyapi.ru/anthropic/v1/messages`
- **YandexGPT**: `https://llm.api.cloud.yandex.net/foundationModels/v1/completion`
- **HuggingFace Router API**: `https://router.huggingface.co/v1/chat/completions`

### Аутентификация

- **OpenAI**: `Authorization: Bearer {OPENAI_API_KEY}`
- **Claude**: `x-api-key: {CLAUDE_API_KEY}` + `anthropic-version: 2023-06-01`
- **YandexGPT**: `Authorization: Api-Key {YANDEX_GPT_API_KEY}` + folder ID в URI модели
- **HuggingFace**: `Authorization: Bearer {HUGGING_FACE_API_KEY}`

### Функции безопасности

- Маскировка токенов в логах HTTP запросов (замена токена на "***TOKEN***")
- Учетные данные хранятся в local.properties (добавлен в .gitignore)
- Поля BuildConfig для инъекции во время компиляции

## Ключевые детали реализации

### Long Polling
- Таймаут: 35 секунд (30с polling + 5с буфер)
- Настраивается через HttpTimeout plugin в Ktor Client

### Системные сообщения

#### Claude API
- Обрабатываются отдельно через параметр `system`
- Не включаются в массив `messages`

#### OpenAI API
- Включаются в массив `messages` с ролью "system"

#### YandexGPT API
- Включаются в массив `messages` с ролью "system"
- Поддерживаются все роли (system, user, assistant)

### YandexGPT специфика

- **URI модели**: Формат `gpt://{folder_id}/{model_id}` (например, `gpt://b1abc123/yandexgpt-lite`)
- **Поле текста**: Использует поле `text` вместо `content`
- **maxTokens**: Передается как String, а не Number
- **Ответ**: Находится в `result.alternatives[0].message.text`

### HuggingFace Router API

- **Формат**: Совместимый с OpenAI формат
- **ID модели**: Передается в теле запроса, а не в URL
- **Поддерживаемые модели**: Множество open-source моделей
- **Выбор модели**: Передается через поле `AiRequest.huggingFaceModel` для правильного роутинга
- **Механизм retry**: Автоматическая повторная попытка (3 попытки, задержка 10с) при 503 Service Unavailable (загрузка модели)

### Статистика ответов

Все провайдеры возвращают:
- Время выполнения (мс)
- Использование токенов (prompt/completion/total tokens)

### Маппинг DTO

- Функции расширения `toDto()` и `toDomain()` для чистого разделения слоев
- Преобразование между domain моделями и DTO

### Обработка ошибок

- `IllegalStateException` для отсутствующих ответов от API
- Валидация ответов от всех провайдеров

### Управление сессиями

Класс `UserSession` хранит для каждого `chatId`:
- `selectedModel` - выбранная AI модель
- `selectedHuggingFaceModel` - выбранная модель HuggingFace (если применимо)
- `conversationHistory` - история диалога
- `temperature` - параметр температуры для AI
- `currentScenario` - текущий активный сценарий

### Архитектура сценариев

- **Enum Scenario**: Содержит метаданные (displayName, command, callbackData)
- **Вспомогательные методы**:
  - `findByCallbackData()` - поиск сценария по callback данным
  - `findByCommand()` - поиск сценария по команде
- **SystemPrompts**: Объект с константами для системного промпта каждого сценария
- **Experts**: Объект со списком `Expert(name, systemPrompt)` для параллельных запросов

### Динамическая генерация UI

- Кнопки сценариев автоматически генерируются из `Scenario.values()`
- Запросы экспертов автоматически масштабируются на основе `Experts.list.size`
- Кнопки разбиты на строки (2 кнопки в ряд) для лучшего UX

### Параллельная обработка

- Сценарий Experts использует `coroutineScope` + `async` для конкурентных AI запросов
- Все запросы выполняются параллельно, результаты собираются и отправляются по мере готовности

### Поток диалога

1. Пользователь выбирает модель → сценарий сбрасывается на FREE_CHAT
2. Пользователь выбирает сценарий → системный промпт внедряется в историю диалога
3. Для сценария EXPERTS → множественные параллельные запросы с различными промптами
4. Ответы отслеживаются в истории диалога для сохранения контекста

### Обработка сообщений с геолокацией

- Сообщения с геолокацией проверяются ПЕРЕД текстовыми сообщениями в TelegramBot.kt (критично для правильной обработки)
- Формат: Сначала проверяется `message.location != null`, затем `message.text`
- Данные геолокации передаются в HandleMessageUseCase для получения прогноза погоды

## Запуск бота

### Через Gradle

```bash
./gradlew :tgbot:run
```

### Через IDE

Запустите функцию `main()` в классе `TelegramBot.kt`.

### Ожидаемый вывод

```
Starting Telegram Bot with long polling...
Bot is running. Press Ctrl+C to stop.
```

## Структура пакетов

```
com.example.tgbot/
├── app/
│   └── TelegramBot.kt
├── data/
│   ├── remote/
│   │   ├── dto/
│   │   │   ├── telegram/
│   │   │   ├── openai/
│   │   │   ├── claude/
│   │   │   ├── yandex/
│   │   │   └── huggingface/
│   │   ├── TelegramApi.kt
│   │   ├── OpenAiApiClient.kt
│   │   ├── ClaudeApiClient.kt
│   │   ├── YandexGptApiClient.kt
│   │   ├── HuggingFaceApiClient.kt
│   │   └── McpWebSocketClient.kt
│   └── repository/
│       ├── TelegramRepositoryImpl.kt
│       ├── AiRepositoryImpl.kt
│       ├── McpRepositoryImpl.kt
│       ├── McpGitRepositoryImpl.kt
│       └── RagRepositoryImpl.kt
└── domain/
    ├── model/
    │   ├── User.kt
    │   ├── Message.kt
    │   ├── Update.kt
    │   ├── InlineKeyboard.kt
    │   ├── Scenario.kt
    │   ├── SystemPrompts.kt
    │   ├── Expert.kt
    │   ├── Location.kt
    │   └── [AI models]
    ├── repository/
    │   ├── TelegramRepository.kt
    │   ├── AiRepository.kt
    │   ├── McpRepository.kt
    │   ├── McpGitRepository.kt
    │   └── RagRepository.kt
    ├── usecase/
    │   ├── HandleMessageUseCase.kt
    │   ├── HandleCommandUseCase.kt
    │   └── HandleCallbackUseCase.kt
    └── util/
        └── WeatherFormatter.kt
```
