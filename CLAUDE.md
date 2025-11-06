# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Advent4Kt is a multi-module Kotlin project consisting of:
- **app**: Android application module (API 29+, target SDK 36)
- **tgbot**: Telegram bot with AI integration (GPT-4o-mini, Claude 3.5 Haiku)

Both modules target Java 11 and use Kotlin 2.0.21.

## Build Commands

### Build the entire project
```bash
./gradlew build
```

### Build specific modules
```bash
./gradlew :app:build
./gradlew :tgbot:build
```

### Clean build
```bash
./gradlew clean build
```

### Run Android app
```bash
./gradlew :app:installDebug
# or with device running
./gradlew :app:installDebug && adb shell am start -n com.example.advent4kt/.MainActivity
```

## Testing

### Run all tests
```bash
./gradlew test
```

### Run unit tests for specific module
```bash
./gradlew :app:test
./gradlew :tgbot:test
```

### Run Android instrumentation tests
```bash
./gradlew :app:connectedAndroidTest
```

### Run a specific test class
```bash
./gradlew :app:test --tests com.example.advent4kt.ExampleUnitTest
```

## Project Architecture

### Module Structure
- **Root**: Multi-module Gradle project using Kotlin DSL and version catalogs (`gradle/libs.versions.toml`)
- **app module**: Standard Android app with single MainActivity, uses Material Design components and edge-to-edge display
- **tgbot module**: Pure Kotlin JVM library (no Android dependencies)

### Key Configuration Details
- Version catalog pattern in `gradle/libs.versions.toml` for dependency management
- Android app namespace: `com.example.advent4kt`
- TGBot package: `com.example.tgbot`
- Minimum Android SDK: 29 (Android 10)
- Target/Compile SDK: 36
- Uses `alias(libs.plugins.*)` pattern for plugin management

### Source Locations
- Android app code: `app/src/main/java/com/example/advent4kt/`
- Android tests: `app/src/test/` (unit) and `app/src/androidTest/` (instrumentation)
- TGBot code: `tgbot/src/main/java/com/example/tgbot/`
- Android resources: `app/src/main/res/`
- Android manifest: `app/src/main/AndroidManifest.xml`

## TGBot Module Details

### Overview
Telegram bot implemented with Clean Architecture, featuring AI integration with OpenAI (GPT-4o-mini) and Anthropic (Claude 3.5 Haiku) via ProxyAPI.

### Architecture
The bot follows Clean Architecture with three layers:

**Domain Layer** (`domain/`):
- `model/` - Business entities (User, Message, Update, InlineKeyboard, AI models)
- `repository/` - Repository interfaces (TelegramRepository, AiRepository)
- `usecase/` - Business logic (HandleMessageUseCase, HandleCommandUseCase, HandleCallbackUseCase)

**Data Layer** (`data/`):
- `remote/dto/` - DTOs for Telegram API, OpenAI API, Claude API
- `remote/` - API clients (TelegramApi, OpenAiApiClient, ClaudeApiClient)
- `repository/` - Repository implementations (TelegramRepositoryImpl, AiRepositoryImpl)

**App Layer** (`app/`):
- `TelegramBot.kt` - Main bot class with long polling and HTTP client configuration

### Technologies
- **Ktor Client** (v2.3.7) - HTTP client for API calls (CIO engine, ContentNegotiation, Logging, HttpTimeout)
- **Kotlinx Serialization** (v1.6.2) - JSON serialization/deserialization
- **Kotlinx Coroutines** (v1.7.3) - Asynchronous operations
- **SLF4J Simple** (v2.0.9) - Logging
- **BuildConfig Plugin** (v5.3.5) - Secure credential management

### Features
- **Long Polling** - Receives updates from Telegram Bot API with 30-second timeout
- **Echo Messages** - Echoes back user messages
- **Commands**:
  - `/models` - Shows inline keyboard with AI model selection (GPT-4o-mini, Claude 3.5 Haiku)
  - `/consultant` - Activates consultant mode
- **Inline Keyboards** - Interactive buttons with callback handling
- **AI Integration**:
  - GPT-4o-mini via OpenAI Chat Completions API
  - Claude 3.5 Haiku via Claude Messages API
  - Unified interface through AiRepository
  - Automatic routing to appropriate API client based on selected model

### Configuration

Create `local.properties` in the project root with the following credentials:

```properties
TELEGRAM_BOT_TOKEN=your_telegram_bot_token
OPENAI_API_KEY=your_proxyapi_openai_key
CLAUDE_API_KEY=your_proxyapi_claude_key
```

**API Endpoints:**
- Telegram Bot API: `https://api.telegram.org/bot{token}/`
- OpenAI (via ProxyAPI): `https://api.proxyapi.ru/openai/v1/chat/completions`
- Claude (via ProxyAPI): `https://api.proxyapi.ru/anthropic/v1/messages`

**Authentication:**
- OpenAI: `Authorization: Bearer {OPENAI_API_KEY}`
- Claude: `x-api-key: {CLAUDE_API_KEY}` + `anthropic-version: 2023-06-01`

### Security Features
- Token masking in HTTP request logs (replaces actual token with "***TOKEN***")
- Credentials stored in local.properties (gitignored)
- BuildConfig fields for compile-time injection

### Key Implementation Details
- **Long Polling Timeout**: 35 seconds (30s polling + 5s buffer) configured in HttpTimeout plugin
- **Claude System Messages**: Handled separately via `system` parameter (not in messages array)
- **OpenAI System Messages**: Included in messages array with role "system"
- **DTO Mapping**: Extension functions (toDto(), toDomain()) for clean layer separation
- **Error Handling**: IllegalStateException for missing API responses

### Running the Bot
The bot needs a main function to start. Implementation pending - should instantiate TelegramBot and call startPolling().
