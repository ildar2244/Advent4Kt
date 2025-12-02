# Advent4Kt

Мультимодульный Kotlin проект, объединяющий Android приложение, Telegram бота с AI интеграцией и
MCP (Model Context Protocol) серверы.

## Обзор проекта

Advent4Kt - это образовательный и экспериментальный проект, демонстрирующий современные подходы к
разработке на Kotlin. Проект включает в себя четыре модуля:

- **app** - Android приложение (API 29+, target SDK 36)
- **tgbot** - Telegram бот с интеграцией AI и RAG поиском
- **mcpweather** - MCP сервер для получения прогнозов погоды через weather.gov API
- **mcpgit** - MCP сервер для интеграции с git репозиторием

## Структура проекта

```
Advent4Kt/
├── app/                    # Android приложение
│   ├── src/main/java/com/example/advent4kt/
│   └── build.gradle.kts
├── tgbot/                  # Telegram бот
│   ├── src/main/java/com/example/tgbot/
│   ├── README_TG_BOT.md
│   └── build.gradle.kts
├── mcpweather/             # MCP сервер погоды
│   ├── src/main/java/com/example/mcpweather/
│   ├── README_MCP_WEATHER.md
│   └── build.gradle.kts
├── mcpgit/                 # MCP сервер git
│   ├── src/main/java/com/example/mcpgit/
│   ├── README_MCP_GIT.md
│   └── build.gradle.kts
├── ragfirst/               # RAG индексация
│   ├── README_RAG_FIRST.md
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml  # Каталог версий зависимостей
├── settings.gradle.kts
├── build.gradle.kts
└── CLAUDE.md              # Инструкции для Claude Code
```

## Технологический стек

### Общее
- **Язык**: Kotlin 2.2.21
- **Система сборки**: Gradle с Kotlin DSL
- **Java**: Целевая версия 11
- **Управление зависимостями**: Version Catalog (gradle/libs.versions.toml)

### Android модуль (app)
- **Min SDK**: 29 (Android 10)
- **Target/Compile SDK**: 36
- **UI**: Material Design Components, Edge-to-edge display
- **Activity**: Single MainActivity

### Telegram бот (tgbot)
- **Архитектура**: Clean Architecture (Domain/Data/App layers)
- **HTTP клиент**: Ktor Client 3.0.3 (CIO engine)
- **Сериализация**: Kotlinx Serialization 1.7.3
- **Асинхронность**: Kotlinx Coroutines 1.7.3
- **База данных**: SQLite (Exposed 0.50.1 + SQLite JDBC 3.45.1.0)
- **AI провайдеры**:
  - OpenAI GPT-4o-mini
  - Anthropic Claude 3.5 Haiku (через ProxyAPI)
  - YandexGPT Lite
  - HuggingFace Router API (множество open-source моделей)
- **RAG**: Ollama embeddings (nomic-embed-text)

### MCP серверы (mcpweather, mcpgit)
- **Сервер**: Ktor Server 3.0.3 (CIO engine)
- **WebSocket**: JSON-RPC 2.0 протокол
- **MCP SDK**: Kotlin SDK 0.7.7

## Основные возможности

### Android приложение (app)
- Базовое Android приложение с Material Design
- Поддержка edge-to-edge отображения
- Минимальная версия Android 10

### Telegram бот (tgbot)

#### AI интеграция
- Поддержка 4 AI провайдеров (OpenAI, Claude, YandexGPT, HuggingFace)
- Выбор модели через inline клавиатуру
- Управление температурой ответов (0.0 - 2.0)
- Отслеживание истории диалога
- Статистика использования токенов

#### Система сценариев
- 5 встроенных сценариев общения:
  - Free Chat - свободный чат
  - JSON Format - ответы в формате JSON
  - Consultant - режим консультанта
  - Step by Step - пошаговое решение задач
  - Experts - параллельные запросы к нескольким экспертам
- Динамическая генерация UI
- Простое добавление новых сценариев

#### RAG (Retrieval-Augmented Generation)
- Семантический поиск по проиндексированным документам
- Поддержка Markdown и PDF файлов
- Ollama embeddings (nomic-embed-text)
- Команды:
  - `/rag <запрос>` - прямой поиск с оценками схожести
  - `/ask <запрос>` - AI ответы на основе найденных документов
  - `/help-project <запрос>` - помощь по проекту с git контекстом
  - `/rag_stats` - статистика индекса

#### MCP интеграция
- Получение прогнозов погоды через mcpweather
- Информация о git ветке через mcpgit
- Обработка геолокации пользователя

#### Команды бота
```
/start          - Начало работы
/models         - Выбор AI модели
/hf_models      - Выбор HuggingFace модели
/temperature    - Установка температуры
/scenario       - Выбор сценария общения
/free-chat      - Свободный чат
/json-format    - JSON формат ответов
/consultant     - Режим консультанта
/step-by-step   - Пошаговое решение
/experts        - Режим экспертов
/mcp            - MCP инструменты
/weather_tools  - Инструменты погоды
/weather_location - Прогноз по геолокации
/rag           - Поиск по документам
/ask           - AI ответ с RAG контекстом
/help-project  - Помощь по проекту
/rag_stats     - Статистика RAG индекса
/stop          - Завершение сессии
```

### MCP Weather сервер (mcpweather)
- WebSocket сервер на порту 8765
- JSON-RPC 2.0 протокол
- Инструменты:
  - `get_forecast` - прогноз погоды по координатам (lat/lon)
  - `get_alerts` - предупреждения по штату США
- Интеграция с weather.gov API
- Автоматическое разрешение координат в точки сетки

### MCP Git сервер (mcpgit)
- WebSocket сервер на порту 8767
- JSON-RPC 2.0 протокол
- Инструменты:
  - `get_current_branch` - получение текущей git ветки
- Выполнение git команд через ProcessBuilder
- Обработка detached HEAD состояния

## Установка и настройка

### Требования
- JDK 11 или выше
- Android SDK (для app модуля)
- Gradle 8.x
- Git

### Клонирование репозитория
```bash
git clone <repository-url>
cd Advent4Kt
```

### Конфигурация

Создайте файл `local.properties` в корне проекта:

```properties
# Telegram бот
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
OPENAI_API_KEY=your_proxyapi_openai_key
CLAUDE_API_KEY=your_proxyapi_claude_key
YANDEX_GPT_API_KEY=your_yandex_cloud_api_key
YANDEX_CLOUD_FOLDER_ID=your_yandex_cloud_folder_id
HUGGING_FACE_API_KEY=your_huggingface_token

# MCP Weather сервер
MCP_WEATHER_WS_HOST=localhost
MCP_WEATHER_WS_PORT=8765
MCP_WEATHER_WS_URL=ws://localhost:8765/mcp

# MCP Git сервер
MCP_GIT_WS_HOST=localhost
MCP_GIT_WS_PORT=8767
MCP_GIT_WS_URL=ws://localhost:8767/mcp
GIT_REPO_PATH=/path/to/your/repo
```

## Сборка и запуск

### Сборка всего проекта
```bash
./gradlew build
```

### Сборка конкретного модуля
```bash
./gradlew :app:build
./gradlew :tgbot:build
./gradlew :mcpweather:build
./gradlew :mcpgit:build
```

### Запуск Android приложения
```bash
./gradlew :app:installDebug
# или с автозапуском
./gradlew :app:installDebug && adb shell am start -n com.example.advent4kt/.MainActivity
```

### Запуск Telegram бота
```bash
./gradlew :tgbot:run
```

### Запуск MCP серверов

#### Weather сервер
```bash
./gradlew :mcpweather:run
```

#### Git сервер
```bash
./gradlew :mcpgit:run
```

### Индексация документов для RAG
```bash
./gradlew :ragfirst:run --args="index /path/to/docs"
```

## Тестирование

### Запуск всех тестов
```bash
./gradlew test
```

### Тесты конкретного модуля
```bash
./gradlew :app:test
./gradlew :tgbot:test
./gradlew :mcpweather:test
./gradlew :mcpgit:test
```

### Android инструментальные тесты
```bash
./gradlew :app:connectedAndroidTest
```

### Запуск конкретного теста
```bash
./gradlew :app:test --tests com.example.advent4kt.ExampleUnitTest
```

## Архитектура

### Модульная структура
Проект использует мультимодульную архитектуру Gradle с разделением ответственности:
- **app** - Android UI слой
- **tgbot** - Бизнес-логика бота (Clean Architecture)
- **mcpweather** - Внешний сервис погоды
- **mcpgit** - Внешний сервис git
- **ragfirst** - CLI утилита для индексации документов

### Clean Architecture (tgbot)
```
domain/          # Бизнес-логика и модели
├── model/       # Сущности предметной области
├── repository/  # Интерфейсы репозиториев
├── usecase/     # Бизнес-логика (use cases)
└── util/        # Утилиты

data/            # Реализация работы с данными
├── remote/      # API клиенты и DTO
└── repository/  # Реализации репозиториев

app/             # Точка входа
└── TelegramBot.kt
```

### Управление зависимостями
Проект использует Version Catalog (`gradle/libs.versions.toml`) для централизованного управления версиями зависимостей:
- Единственный источник версий для всех модулей
- Type-safe доступ через `libs.` в build скриптах
- Плагины управляются через `alias(libs.plugins.*)`

## Безопасность

- Все API ключи и токены хранятся в `local.properties` (добавлен в .gitignore)
- Маскировка токенов в логах HTTP запросов
- Инъекция credentials через BuildConfig во время компиляции
- Не коммитятся чувствительные данные

## Документация модулей

Подробная документация по каждому модулю:

- [TGBot - Telegram бот](tgbot/README_TG_BOT.md)
- [MCPWeather - MCP сервер погоды](mcpweather/README_MCP_WEATHER.md)
- [MCPGit - MCP сервер git](mcpgit/README_MCP_GIT.md)
- [RAGFirst - Индексация документов](ragfirst/README_RAG_FIRST.md)

## Инструкции для Claude Code

Проект содержит файл [CLAUDE.md](CLAUDE.md) с подробными инструкциями для работы с Claude Code, включая:
- Команды сборки и тестирования
- Архитектуру проекта
- Конфигурацию модулей
- Ключевые детали реализации

## Используемые API

### Telegram Bot API
- **Документация**: https://core.telegram.org/bots/api
- **Метод**: Long Polling
- **Таймаут**: 30 секунд

### OpenAI API (через ProxyAPI)
- **Endpoint**: https://api.proxyapi.ru/openai/v1/chat/completions
- **Модель**: gpt-4o-mini
- **Документация**: https://platform.openai.com/docs/api-reference

### Claude API (через ProxyAPI)
- **Endpoint**: https://api.proxyapi.ru/anthropic/v1/messages
- **Модель**: claude-3-5-haiku-20241022
- **Документация**: https://docs.anthropic.com/claude/reference

### YandexGPT API
- **Endpoint**: https://llm.api.cloud.yandex.net/foundationModels/v1/completion
- **Модель**: yandexgpt-lite
- **Документация**: https://cloud.yandex.ru/docs/foundation-models/

### HuggingFace Router API
- **Endpoint**: https://router.huggingface.co/v1/chat/completions
- **Модели**: Qwen, Llama, Hermes, Mixtral, Mistral, Phi
- **Документация**: https://huggingface.co/docs/api-inference/

### Weather.gov API
- **Endpoint**: https://api.weather.gov
- **Инструменты**: Прогнозы и предупреждения для США
- **Документация**: https://www.weather.gov/documentation/services-web-api

## Лицензия

Образовательный проект для демонстрации современных подходов к разработке на Kotlin.

## Контакты и поддержка

Для вопросов и предложений создавайте Issues в репозитории проекта.
