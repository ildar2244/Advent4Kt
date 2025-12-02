# mcpgit - MCP Server для Git интеграции

MCP (Model Context Protocol) сервер для интеграции с git-репозиторием. Предоставляет инструменты для получения информации о текущем состоянии git-репозитория через WebSocket с JSON-RPC 2.0 протоколом.

## Обзор

mcpgit - это легковесный MCP сервер, который позволяет внешним приложениям (например, Telegram ботам, AI агентам) получать информацию о текущем git-репозитории через WebSocket соединение.

### Основные возможности

- ✅ Получение текущей ветки (`git branch --show-current`)
- ✅ WebSocket + JSON-RPC 2.0 протокол
- ✅ Clean Architecture (Domain/Data/App слои)
- ✅ Конфигурация через local.properties
- ✅ Обработка ошибок и edge cases (detached HEAD)
- ✅ Минималистичный дизайн (только необходимый функционал)

## Архитектура

Проект следует принципам Clean Architecture с тремя основными слоями:

### Структура директорий

```
mcpgit/
├── build.gradle.kts
└── src/main/java/com/example/mcpgit/
    ├── app/
    │   └── GitMcpServer.kt              # WebSocket сервер + JSON-RPC обработка
    ├── domain/
    │   ├── model/
    │   │   └── GitBranch.kt             # Domain model
    │   ├── repository/
    │   │   └── GitRepository.kt         # Repository interface
    │   └── usecase/
    │       └── GetCurrentBranchUseCase.kt  # Business logic
    └── data/
        ├── repository/
        │   └── GitRepositoryImpl.kt     # Repository implementation
        └── executor/
            └── GitCommandExecutor.kt    # Git command execution via ProcessBuilder
```

### Слои

**Domain Layer** (business logic, framework-independent):
- `GitBranch` - простая модель для представления git-ветки
- `GitRepository` - интерфейс репозитория
- `GetCurrentBranchUseCase` - use case для получения текущей ветки

**Data Layer** (implementation details):
- `GitCommandExecutor` - выполнение git команд через ProcessBuilder
- `GitRepositoryImpl` - реализация GitRepository

**App Layer** (framework integration):
- `GitMcpServer` - WebSocket сервер, JSON-RPC обработка, dependency injection

## Установка и настройка

### 1. Конфигурация

Добавьте в файл `local.properties` в корне проекта:

```properties
# MCP Git Server
MCP_GIT_WS_HOST=localhost
MCP_GIT_WS_PORT=8767
MCP_GIT_WS_URL=ws://localhost:8767/mcp
GIT_REPO_PATH=/Users/your-username/path/to/your/project
```

**Параметры:**
- `MCP_GIT_WS_HOST` - хост для WebSocket сервера (по умолчанию: localhost)
- `MCP_GIT_WS_PORT` - порт для WebSocket сервера (по умолчанию: 8767)
- `MCP_GIT_WS_URL` - полный URL для подключения клиентов
- `GIT_REPO_PATH` - абсолютный путь к git-репозиторию

### 2. Сборка

```bash
./gradlew :mcpgit:build
```

### 3. Запуск

```bash
./gradlew :mcpgit:run
```

Вывод при успешном запуске:
```
Starting MCP Git WebSocket Server on ws://localhost:8767/mcp
Git repository path: /Users/your-username/path/to/your/project
```

## MCP Protocol

### Доступные инструменты

#### 1. `get_current_branch`

Получает имя текущей активной git-ветки.

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "get_current_branch",
    "arguments": {}
  },
  "id": 1
}
```

**Успешный ответ:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "main"
      }
    ]
  },
  "id": 1
}
```

**Ответ при detached HEAD:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "detached HEAD"
      }
    ]
  },
  "id": 1
}
```

**Ответ при ошибке:**
```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32603,
    "message": "Error getting current branch: <error details>"
  },
  "id": 1
}
```

### JSON-RPC 2.0 коды ошибок

| Код | Описание |
|-----|----------|
| -32700 | Parse error - невалидный JSON |
| -32601 | Method not found - метод не найден |
| -32602 | Invalid params - невалидные параметры |
| -32603 | Internal error - внутренняя ошибка сервера |

### Ping/Pong

Сервер поддерживает ping/pong механизм для поддержания соединения:

**Ping:**
```json
{"type": "ping"}
```

**Pong:**
```json
{"type": "pong"}
```

## Технические детали

### Зависимости

- **Ktor Server** (3.0.3) - WebSocket server
  - ktor-server-core
  - ktor-server-cio
  - ktor-server-websockets
  - ktor-server-sse (required by MCP SDK)
- **Kotlinx Serialization** (1.7.3) - JSON serialization
- **Kotlinx Coroutines** (1.7.3) - Async operations
- **SLF4J Simple** (2.0.9) - Logging
- **MCP Kotlin SDK** (0.7.7) - MCP utilities

### Git Command Execution

Используется `ProcessBuilder` для выполнения git команд:

```kotlin
suspend fun executeGitCommand(vararg command: String): String = withContext(Dispatchers.IO) {
    val process = ProcessBuilder(*command)
        .directory(File(BuildConfig.GIT_REPO_PATH))
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw RuntimeException("Git command failed (exit code $exitCode): $output")
    }

    output
}
```

**Особенности:**
- Async выполнение через `Dispatchers.IO`
- Working directory из BuildConfig
- Проверка exit code
- Объединение stdout/stderr
- Trim output для удаления whitespace

### WebSocket Configuration

```kotlin
install(WebSockets) {
    pingPeriod = 30.seconds      // Send ping every 30s
    timeout = 15.seconds          // Close if no response for 15s
    maxFrameSize = Long.MAX_VALUE // Allow large messages
    masking = false               // Don't mask frames
}
```

## Тестирование

### Standalone тест с websocat

```bash
# Terminal 1: Запуск сервера
./gradlew :mcpgit:run

# Terminal 2: Подключение и тест
websocat ws://localhost:8767/mcp

# Отправить JSON-RPC запрос
{"jsonrpc":"2.0","method":"tools/call","params":{"name":"get_current_branch","arguments":{}},"id":1}

# Ожидаемый ответ
{"jsonrpc":"2.0","result":{"content":[{"type":"text","text":"main"}]},"id":1}
```

### Проверка git команды

```bash
cd /path/to/your/repo
git branch --show-current
# Output: main (or your current branch)
```

## Безопасность

### Ограничения

1. **Только чтение** - сервер не выполняет команды, изменяющие репозиторий
2. **Фиксированный путь** - работает только с репозиторием, указанным в конфигурации
3. **Локальный доступ** - по умолчанию привязан к localhost
4. **Без аутентификации** - предполагается использование в доверенной среде


## Расширение функциональности

### Добавление новых git команд

1. Добавьте метод в `GitCommandExecutor.kt`:
```kotlin
suspend fun getLastCommit(): String {
    return executeGitCommand("git", "log", "-1", "--oneline")
}
```

2. Обновите интерфейс `GitRepository.kt`:
```kotlin
interface GitRepository {
    suspend fun getCurrentBranch(): String
    suspend fun getLastCommit(): String  // Новый метод
}
```

3. Реализуйте в `GitRepositoryImpl.kt`:
```kotlin
override suspend fun getLastCommit(): String {
    return gitCommandExecutor.getLastCommit()
}
```

4. Создайте use case `GetLastCommitUseCase.kt`

5. Добавьте обработчик в `GitMcpServer.kt`:
```kotlin
when (params.name) {
    "get_current_branch" -> handleGetCurrentBranch(...)
    "get_last_commit" -> handleGetLastCommit(...)  // Новый обработчик
    else -> ...
}
```

## Troubleshooting

### Сервер не запускается

**Проблема:** Port already in use
```
Address already in use
```

**Решение:** Измените порт в `local.properties`:
```properties
MCP_GIT_WS_PORT=8768
```

### Git команды не работают

**Проблема:** Git not found
```
Cannot run program "git": error=2, No such file or directory
```

**Решение:** Убедитесь, что git установлен и доступен в PATH:
```bash
which git
git --version
```

### Неправильная ветка

**Проблема:** Возвращает ветку из другого репозитория

**Решение:** Проверьте `GIT_REPO_PATH` в `local.properties`:
```bash
# Проверить текущую конфигурацию
cat local.properties | grep GIT_REPO_PATH

# Должен быть абсолютный путь к нужному репозиторию
GIT_REPO_PATH=/Users/username/correct/path
```
