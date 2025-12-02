# ragfirst - RAG (Retrieval-Augmented Generation) CLI

CLI инструмент для индексации документов и семантического поиска с использованием векторных эмбеддингов.

## Обзор

ragfirst - это инструмент командной строки для построения RAG (Retrieval-Augmented Generation) индекса. Модуль позволяет индексировать документы (Markdown, PDF), разбивать их на чанки, генерировать векторные эмбеддинги через Ollama и выполнять семантический поиск с использованием косинусного сходства.

### Основные возможности

- ✅ Индексация документов из директории (рекурсивный обход)
- ✅ Индексация отдельных файлов (.md, .markdown, .pdf)
- ✅ Рекурсивное разбиение текста на чанки с перекрытием
- ✅ Генерация векторных эмбеддингов через Ollama API (bge-m3 модель)
- ✅ Семантический поиск по косинусному сходству
- ✅ SQLite база данных с WAL режимом
- ✅ Статистика индекса (количество документов, чанков, эмбеддингов)
- ✅ Обработка частичной индексации (partial indexing)
- ✅ Rate limiting для API запросов (100ms между запросами)

## Архитектура

Проект следует принципам Clean Architecture с тремя основными слоями:

### Структура директорий

```
ragfirst/
├── build.gradle.kts
├── data/
│   └── rag.db                           # SQLite база данных
└── src/main/java/com/example/ragfirst/
    ├── app/
    │   └── RagCli.kt                    # CLI приложение + dependency injection
    ├── domain/
    │   ├── model/
    │   │   ├── Document.kt              # Domain model: документ
    │   │   ├── Chunk.kt                 # Domain model: чанк текста
    │   │   ├── Embedding.kt             # Domain model: векторный эмбеддинг
    │   │   └── SearchResult.kt          # Domain model: результат поиска
    │   ├── repository/
    │   │   ├── RagRepository.kt         # Repository interface для работы с индексом
    │   │   └── OllamaRepository.kt      # Repository interface для Ollama API
    │   └── usecase/
    │       ├── IndexDocumentsUseCase.kt # Business logic: индексация
    │       ├── SearchSimilarUseCase.kt  # Business logic: поиск
    │       ├── GetStatisticsUseCase.kt  # Business logic: статистика
    │       └── ClearIndexUseCase.kt     # Business logic: очистка индекса
    ├── data/
    │   ├── local/
    │   │   ├── db/
    │   │   │   ├── DatabaseFactory.kt   # Инициализация SQLite + WAL
    │   │   │   ├── RagDao.kt            # Data Access Object
    │   │   │   ├── DocumentsTable.kt    # Exposed таблица: documents
    │   │   │   ├── ChunksTable.kt       # Exposed таблица: chunks
    │   │   │   └── EmbeddingsTable.kt   # Exposed таблица: embeddings
    │   │   └── parser/
    │   │       ├── MarkdownParser.kt    # Парсинг Markdown файлов
    │   │       ├── PdfParser.kt         # Парсинг PDF файлов (Apache PDFBox)
    │   │       └── CompositeDocumentParser.kt # Композитный парсер
    │   ├── remote/
    │   │   └── OllamaApiClient.kt       # HTTP клиент для Ollama API
    │   └── repository/
    │       ├── RagRepositoryImpl.kt     # Repository implementation
    │       └── OllamaRepositoryImpl.kt  # Repository implementation
    └── util/
        ├── RecursiveTextChunker.kt      # Рекурсивное разбиение текста
        └── SimilarityCalculator.kt      # Расчёт косинусного сходства
```

### Слои

**Domain Layer** (business logic, framework-independent):
- `Document`, `Chunk`, `Embedding`, `SearchResult` - domain модели
- `RagRepository`, `OllamaRepository` - интерфейсы репозиториев
- `IndexDocumentsUseCase` - индексация файлов и директорий
- `SearchSimilarUseCase` - семантический поиск по запросу
- `GetStatisticsUseCase`, `ClearIndexUseCase` - вспомогательные use cases

**Data Layer** (implementation details):
- `DatabaseFactory` - инициализация SQLite с WAL режимом
- `RagDao` - SQL-запросы через Exposed ORM
- `MarkdownParser`, `PdfParser` - парсинг документов
- `OllamaApiClient` - HTTP клиент для эмбеддингов
- `RagRepositoryImpl`, `OllamaRepositoryImpl` - реализации репозиториев

**App Layer** (framework integration):
- `RagCli` - CLI приложение, парсинг аргументов, dependency injection

## Установка и настройка

### 1. Зависимости

Убедитесь, что Ollama запущен локально:

```bash
# Установка Ollama (если не установлен)
# https://ollama.ai/

# Запуск Ollama
ollama serve

# Загрузка модели bge-m3 (если не загружена)
ollama pull bge-m3
```

### 2. Конфигурация

Добавьте в файл `local.properties` в корне проекта (опционально):

```properties
# Ollama API
OLLAMA_BASE_URL=http://localhost:11434
```

По умолчанию используется `http://localhost:11434`.

### 3. Сборка

```bash
./gradlew :ragfirst:build
```

## CLI Команды

### `index` - Индексация директории

Рекурсивно индексирует все поддерживаемые файлы (.md, .markdown, .pdf) из указанной директории.

```bash
./gradlew :ragfirst:run --args="index /path/to/docs"
```

**Процесс:**
1. Рекурсивный обход директории
2. Фильтрация файлов по расширению (.md, .markdown, .pdf)
3. Парсинг каждого файла
4. Разбиение на чанки (400 символов, 40 символов overlap)
5. Генерация эмбеддингов через Ollama (bge-m3)
6. Сохранение в SQLite базу данных

**Особенности:**
- Пропуск уже проиндексированных файлов (по абсолютному пути)
- Rate limiting: 100ms задержка между запросами к Ollama
- Прогресс: каждые 10 чанков выводится статус
- Partial indexing: продолжение при ошибках отдельных чанков

### `index-file` - Индексация одного файла

Индексирует отдельный файл (полезно для тестирования).

```bash
./gradlew :ragfirst:run --args="index-file CLAUDE.md"
./gradlew :ragfirst:run --args="index-file /Users/../AndroidProjects/../README.md"
```

**Поддерживаемые форматы:**
- `.md`, `.markdown` - Markdown файлы
- `.pdf` - PDF документы (через Apache PDFBox)

**Вывод при успехе:**
```
✓ File indexed successfully!
```

**Вывод при частичной индексации:**
```
⚠ Warning: File partially indexed
  Successful chunks: 42
  Failed chunks: 3
  Error: Partially indexed file.pdf: 42/45 chunks succeeded, 3 failed
```

### `search` - Семантический поиск

Выполняет поиск по индексу с использованием векторных эмбеддингов.

```bash
./gradlew :ragfirst:run --args="search 'how to use RAG'"
```

**Процесс:**
1. Генерация эмбеддинга для запроса (через Ollama bge-m3)
2. Вычисление косинусного сходства со всеми чанками в БД
3. Фильтрация по порогу (threshold ≥ 0.7)
4. Сортировка по убыванию сходства
5. Возврат топ-5 результатов

**Параметры поиска:**
- `topK = 5` - максимум 5 результатов
- `threshold = 0.7` - минимальное сходство 70%

**Пример вывода:**
```
Searching for: 'how to use RAG'

Result 1 (similarity: 0.892)
Document: /Users/.../CLAUDE.md
Chunk #42
Content: RAG Integration (Retrieval-Augmented Generation):
  - Full-text semantic search through indexed documents using Ollama embeddings (nomic-embed-text model)
  - `/rag <query>` - Direct search with similarity...

Result 2 (similarity: 0.834)
Document: /Users/.../README.md
Chunk #15
Content: The ragfirst module provides semantic search capabilities...
```

### `stats` - Статистика индекса

Показывает количество документов, чанков и эмбеддингов в индексе.

```bash
./gradlew :ragfirst:run --args="stats"
```

**Вывод:**
```
=== RAG Index Statistics ===
Documents: 15
Chunks: 324
Embeddings: 324
```

### `clear` - Очистка индекса

Удаляет все данные из индекса (документы, чанки, эмбеддинги).

```bash
./gradlew :ragfirst:run --args="clear"
```

**Вывод:**
```
Clearing index...
Index cleared successfully.
```

## База данных

### Путь к БД

SQLite база данных автоматически создаётся в:
```
ragfirst/data/rag.db
```

### Режим WAL

База данных использует Write-Ahead Logging (WAL) для:
- Улучшенной производительности записи
- Concurrent reads во время записи
- Лучшей надёжности при сбоях

Конфигурация:
```kotlin
Database.connect(
    url = "jdbc:sqlite:$DB_PATH?journal_mode=WAL&busy_timeout=5000",
    driver = "org.sqlite.JDBC"
)
exec("PRAGMA journal_mode=WAL;")
exec("PRAGMA busy_timeout=5000;")
```

### Схема БД

#### Таблица `documents`

Хранит метаданные индексированных документов.

| Поле | Тип | Описание |
|------|-----|----------|
| id | INTEGER PRIMARY KEY | Автоинкремент ID |
| path | TEXT | Абсолютный путь к файлу |
| type | VARCHAR(50) | Тип документа (MARKDOWN, PDF) |
| content | TEXT | Полный текст документа |
| metadata | TEXT | JSON с метаданными (filename, size, headers) |
| created_at | DATETIME | Timestamp индексации |

#### Таблица `chunks`

Хранит текстовые чанки из документов.

| Поле | Тип | Описание |
|------|-----|----------|
| id | INTEGER PRIMARY KEY | Автоинкремент ID |
| document_id | INTEGER FOREIGN KEY | Ссылка на documents.id |
| content | TEXT | Текст чанка (≤400 символов) |
| chunk_index | INTEGER | Порядковый номер чанка в документе |
| metadata | TEXT | JSON с метаданными чанка |

#### Таблица `embeddings`

Хранит векторные представления чанков.

| Поле | Тип | Описание |
|------|-----|----------|
| chunk_id | INTEGER PRIMARY KEY | Ссылка на chunks.id (1-to-1) |
| vector | BLOB | Бинарное представление FloatArray |

**Размерность вектора:** зависит от модели (bge-m3: 1024 измерения)

## Парсинг документов

### Markdown Parser

Парсит Markdown файлы (.md, .markdown) с извлечением метаданных.

**Извлекаемые метаданные:**
- `filename` - имя файла
- `size` - размер файла в байтах
- `first_header` - первый заголовок в документе
- `headers_count` - количество заголовков (# - ######)

**Регулярное выражение для заголовков:**
```kotlin
Regex("^#{1,6}\\s+(.+)$", RegexOption.MULTILINE)
```

### PDF Parser

Парсит PDF файлы через Apache PDFBox.

**Извлекаемые метаданные:**
- `filename` - имя файла
- `size` - размер файла в байтах
- `pages` - количество страниц

**Зависимость:**
```kotlin
implementation("org.apache.pdfbox:pdfbox:3.0.1")
```

### Composite Document Parser

Объединяет несколько парсеров с автоматическим выбором по расширению файла.

```kotlin
val documentParser = CompositeDocumentParser(
    listOf(MarkdownParser(), PdfParser())
)
```

**Логика:**
1. Проверка `supports(file)` для каждого парсера
2. Выбор первого подходящего парсера
3. Делегирование вызова `parse(file)`

## Разбиение текста на чанки

### RecursiveTextChunker

Рекурсивный алгоритм разбиения с перекрытием (overlap) для сохранения контекста.

**Параметры:**
```kotlin
RecursiveTextChunker(
    chunkSize = 400,  // Максимум символов в чанке
    overlap = 40      // Перекрытие между чанками
)
```

**Алгоритм:**

1. Если текст ≤ `chunkSize` → возвращаем как есть
2. Иначе пытаемся разделить по сепараторам (в порядке приоритета):
   - `"\n\n"` - двойной перенос строки (абзацы)
   - `"\n"` - одинарный перенос строки
   - `". "` - точка с пробелом (предложения)
   - `" "` - пробел (слова)
   - `""` - посимвольное разбиение (крайний случай)
3. Рекурсивно применяем к подчастям
4. Добавляем overlap: последние N символов предыдущего чанка к началу следующего

**Пример:**
```
Исходный текст: "Hello world. This is a test. More text here."
chunkSize = 20, overlap = 5

Чанки:
1. "Hello world. This "
2. "his is a test. "
3. "est. More text here."
```

**Преимущества:**
- Сохранение семантического контекста на границах чанков
- Адаптивное разбиение по естественным границам (абзацы → предложения → слова)
- Рекурсивный подход обрабатывает любые длинные тексты

## Векторные эмбеддинги

### Ollama API Client

HTTP клиент для генерации эмбеддингов через Ollama API.

**Конфигурация:**
```kotlin
OllamaApiClient(model = "bge-m3")
```

**HTTP Endpoint:**
```
POST http://localhost:11434/api/embeddings
Content-Type: application/json

{
  "model": "bge-m3",
  "prompt": "текст для эмбеддинга"
}
```

**Response:**
```json
{
  "embedding": [0.123, -0.456, 0.789, ...]
}
```

**Таймауты:**
- Request timeout: 60 секунд
- Connect timeout: 10 секунд

**Retry механизм:**
- 3 попытки при сетевых ошибках
- Экспоненциальная задержка между попытками

### Модель bge-m3

- **Название:** BAAI General Embedding Model v3
- **Размерность:** 1024 измерения
- **Особенности:** Мультиязычная, оптимизирована для информационного поиска
- **Загрузка:** `ollama pull bge-m3`

## Семантический поиск

### Cosine Similarity

Расчёт сходства между векторами через косинусное расстояние.

**Формула:**
```
similarity(A, B) = (A · B) / (||A|| * ||B||)
```

**Реализация:**
```kotlin
fun cosineSimilarity(vector1: FloatArray, vector2: FloatArray): Float {
    require(vector1.size == vector2.size) {
        "Vectors must have the same dimension"
    }

    var dotProduct = 0.0
    var magnitude1 = 0.0
    var magnitude2 = 0.0

    for (i in vector1.indices) {
        dotProduct += vector1[i] * vector2[i]
        magnitude1 += vector1[i] * vector1[i]
        magnitude2 += vector2[i] * vector2[i]
    }

    magnitude1 = sqrt(magnitude1)
    magnitude2 = sqrt(magnitude2)

    return if (magnitude1 == 0.0 || magnitude2 == 0.0) {
        0f
    } else {
        (dotProduct / (magnitude1 * magnitude2)).toFloat()
    }
}
```

**Диапазон значений:**
- 1.0 - полное сходство (идентичные векторы)
- 0.7 - хорошее сходство (threshold по умолчанию)
- 0.0 - отсутствие сходства (ортогональные векторы)
- -1.0 - противоположные векторы

### SearchSimilarUseCase

Поисковый алгоритм:

1. **Генерация эмбеддинга запроса:**
   ```kotlin
   val queryEmbedding = ollamaRepository.generateEmbedding(query)
   ```

2. **Загрузка всех чанков с эмбеддингами:**
   ```kotlin
   val chunksWithEmbeddings = ragRepository.getAllChunksWithEmbeddings()
   ```

3. **Вычисление сходства для каждого чанка:**
   ```kotlin
   val similarities = chunksWithEmbeddings.map { (chunk, embedding) ->
       val similarity = SimilarityCalculator.cosineSimilarity(queryEmbedding, embedding.vector)
       Triple(chunk, similarity, embedding)
   }
   ```

4. **Фильтрация по порогу:**
   ```kotlin
   val filtered = similarities.filter { it.second >= threshold }
   ```

5. **Сортировка и топ-K:**
   ```kotlin
   val topResults = filtered
       .sortedByDescending { it.second }
       .take(topK)
   ```

6. **Загрузка метаданных документов:**
   ```kotlin
   topResults.map { (chunk, similarity, _) ->
       val document = ragRepository.getDocument(chunk.documentId)
       SearchResult(chunk, document, similarity)
   }
   ```

**Оптимизация:**
- In-memory расчёт сходства (без SQL)
- Единственный SELECT для загрузки всех чанков
- Ленивая загрузка метаданных документов только для топ-K результатов

## Технические детали

### Зависимости

- **Ktor Client** (3.0.3) - HTTP клиент для Ollama API
  - ktor-client-core
  - ktor-client-cio
  - ktor-client-content-negotiation
  - ktor-client-logging
- **Kotlinx Serialization** (1.7.3) - JSON serialization
- **Kotlinx Coroutines** (1.7.3) - Async operations
- **SLF4J Simple** (2.0.9) - Logging
- **Exposed ORM** (0.50.1) - SQL library
  - exposed-core
  - exposed-dao
  - exposed-jdbc
  - exposed-java-time
- **SQLite JDBC** (3.45.1.0) - SQLite driver
- **Apache PDFBox** (3.0.1) - PDF parsing

### Обработка ошибок

**PartialIndexingException:**
```kotlin
class PartialIndexingException(
    message: String,
    val successfulChunks: Int,
    val failedChunks: Int
) : Exception(message)
```

Выбрасывается, когда часть чанков успешно проиндексирована, но некоторые чанки провалились (например, из-за таймаута Ollama API).

**Стратегия обработки:**
- Продолжать индексацию остальных чанков при ошибке
- Логировать каждую ошибку
- Пробрасывать PartialIndexingException для отчётности в CLI

### Rate Limiting

Для предотвращения перегрузки Ollama API используется задержка между запросами:

```kotlin
delay(100) // 100ms между запросами эмбеддингов
```

**Макс. throughput:**
- 10 чанков/секунду
- 600 чанков/минуту
- ~36,000 чанков/час

### Database Dispatcher

Все операции с БД выполняются на выделенном single-threaded dispatcher:

```kotlin
val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

suspend fun saveDocument(document: Document): Int = withContext(databaseDispatcher) {
    RagDao.insertDocument(document)
}
```

**Причины:**
- SQLite не поддерживает concurrent writes
- Гарантия последовательности операций
- Избежание race conditions

## Troubleshooting

### Ollama не запущен

**Проблема:**
```
Failed to connect to localhost:11434
```

**Решение:**
```bash
# Запустить Ollama
ollama serve

# Проверить, что Ollama доступен
curl http://localhost:11434/api/version
```

### Модель bge-m3 не загружена

**Проблема:**
```
Model 'bge-m3' not found
```

**Решение:**
```bash
# Загрузить модель (размер ~500MB)
ollama pull bge-m3

# Проверить список моделей
ollama list
```

### Ошибка парсинга PDF

**Проблема:**
```
Failed to parse PDF: Encrypted PDF documents are not supported
```

**Решение:**
- PDF файл зашифрован - требуется расшифровка
- Некоторые PDF могут быть повреждены или несовместимы с Apache PDFBox

### База данных заблокирована

**Проблема:**
```
Database is locked
```

**Решение:**
- Закрыть другие процессы, использующие rag.db
- Увеличить `busy_timeout` в DatabaseFactory.kt
- Проверить, что не запущено несколько экземпляров CLI одновременно

### Partial indexing

**Проблема:**
```
⚠ Warning: File partially indexed
  Successful chunks: 42
  Failed chunks: 3
```

**Возможные причины:**
- Таймаут Ollama API (увеличьте requestTimeoutMillis)
- Некорректный текст в чанке (encoding issues)
- Временная недоступность Ollama

**Решение:**
- Проверить логи для деталей ошибки
- Повторить индексацию файла командой `index-file`
- Увеличить таймауты в OllamaApiClient

### Медленный поиск

**Проблема:**
Поиск занимает 10+ секунд на больших индексах.

**Причина:**
Алгоритм загружает все эмбеддинги в память и вычисляет сходство последовательно.

**Решение (future work):**
- Использовать векторную БД (Qdrant, Milvus, Weaviate)
- Добавить ANN (Approximate Nearest Neighbors) индекс
- Использовать FAISS или Annoy для быстрого поиска
