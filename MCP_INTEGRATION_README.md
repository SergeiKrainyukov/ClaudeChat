# MCP (Model Context Protocol) Integration для Android

## Обзор

Интеграция MCP сервера позволяет вашему Android приложению взаимодействовать с Todoist через WebSocket подключение. Claude может автоматически создавать задачи, просматривать списки задач и управлять проектами в Todoist.

## Структура проекта

```
app/src/main/java/com/example/claudechat/
├── data/
│   └── mcp/
│       ├── McpClient.kt              # WebSocket клиент для MCP
│       ├── McpRepository.kt          # Репозиторий для MCP операций
│       └── models/
│           ├── McpRequest.kt         # JSON-RPC request модели
│           ├── McpResponse.kt        # JSON-RPC response модели
│           └── TodoistModels.kt      # Модели Todoist данных
├── utils/
│   ├── McpCommandParser.kt           # Парсер команд из текста
│   └── SystemPrompts.kt              # Обновлен с Todoist промптом
├── viewmodel/
│   └── ChatViewModel.kt              # Расширен с MCP функциями
└── ui/
    ├── components/
    │   ├── TodoistCard.kt            # Карточка задачи
    │   ├── McpStatusIndicator.kt     # Индикатор статуса MCP
    │   └── McpActionButton.kt        # Кнопки действий Todoist
    └── screens/
        └── ChatScreen.kt             # Обновлен с MCP UI
```

## Настройка

### 1. Настройка MCP сервера

Убедитесь, что ваш локальный MCP сервер запущен и доступен:

```bash
# Для Android эмулятора
WebSocket URL: ws://10.0.2.2:8080/mcp

# Для физического устройства (замените на IP вашего компьютера)
WebSocket URL: ws://192.168.x.x:8080/mcp
```

### 2. Изменение URL сервера

Если ваш MCP сервер использует другой адрес, обновите `ChatViewModel.kt`:

```kotlin
private val mcpRepository = McpRepository(
    serverUrl = "ws://YOUR_IP:YOUR_PORT/mcp",
    enableDebugLogs = true
)
```

### 3. Зависимости

Все необходимые зависимости уже добавлены в `app/build.gradle.kts`:

- `kotlinx-serialization-json` - для JSON сериализации
- `okhttp` - для WebSocket соединения
- Плагин `kotlinx.serialization` для кодогенерации

## Использование

### Основные возможности

#### 1. Автоматическое распознавание команд

Приложение автоматически распознает команды в тексте пользователя:

```kotlin
// Примеры фраз, которые распознаются:
"Создай задачу: Купить молоко"
"Напомни мне позвонить врачу завтра"
"Покажи мои задачи"
"Не забыть отправить отчет в пятницу"
```

#### 2. Быстрые действия

UI предоставляет кнопки для быстрых действий:

- **Новая задача** - создание задачи
- **Мои задачи** - просмотр задач
- **Проекты** - просмотр проектов

#### 3. Статус подключения

Индикатор показывает текущее состояние MCP соединения:

- 🟢 **Connected** - подключено к Todoist
- 🟡 **Connecting** - подключение...
- 🔴 **Disconnected** - отключено
- ⚠️ **Error** - ошибка подключения

### API методы

#### ChatViewModel методы:

```kotlin
// Создать задачу
viewModel.createTodoistTask(
    content = "Купить молоко",
    description = "Обезжиренное, 2 литра",
    dueString = "tomorrow",
    priority = 3
)

// Получить список задач
viewModel.listTodoistTasks()

// Выполнить задачу
viewModel.completeTodoistTask(taskId = "12345")

// Получить проекты
viewModel.listTodoistProjects()

// Проверить подключение
val isConnected = viewModel.isMcpConnected()

// Переподключиться
viewModel.reconnectMcp()
```

#### McpRepository методы:

```kotlin
// Прямое использование репозитория
val mcpRepository = McpRepository()

// Создать задачу с полными параметрами
val result = mcpRepository.createTask(
    content = "Важная задача",
    description = "Подробное описание",
    projectId = "project_id",
    dueString = "friday",
    priority = 4,
    labels = listOf("urgent", "work")
)

when (result) {
    is McpResult.Success -> {
        val task = result.data
        println("Задача создана: ${task.id}")
    }
    is McpResult.Error -> {
        println("Ошибка: ${result.message}")
    }
}
```

### Парсер команд

`McpCommandParser` автоматически извлекает Todoist действия из текста:

```kotlin
val text = "Напомни мне купить молоко завтра"
val actions = McpCommandParser.parseActions(text)

// actions содержит:
// ParsedAction(
//     action = TodoistAction.CreateTask(
//         content = "купить молоко",
//         dueString = "tomorrow"
//     ),
//     confidence = 0.85
// )
```

#### Поддерживаемые паттерны:

- **Создание задач**: "создай задачу", "добавь задачу", "напомни мне", "не забыть"
- **Просмотр задач**: "покажи задачи", "мои задачи", "что на сегодня"
- **Выполнение задач**: "отметь задачу X как выполненную"
- **Проекты**: "покажи проекты", "мои проекты"

#### Извлечение параметров:

```kotlin
// Извлечь дату
val date = McpCommandParser.extractDueDate("напомни завтра")
// Returns: "tomorrow"

// Извлечь приоритет
val priority = McpCommandParser.extractPriority("срочная задача")
// Returns: TodoistPriority.HIGH
```

## UI Компоненты

### TodoistCard

Отображает задачу с возможностью выполнения и удаления:

```kotlin
TodoistCard(
    task = task,
    onComplete = { taskId -> viewModel.completeTodoistTask(taskId) },
    onDelete = { taskId -> viewModel.deleteTask(taskId) }
)
```

### McpStatusIndicator

Показывает статус подключения:

```kotlin
McpStatusIndicator(
    connectionState = mcpConnectionState,
    onReconnect = { viewModel.reconnectMcp() }
)
```

### TodoistQuickActions

Панель быстрых действий:

```kotlin
TodoistQuickActions(
    onCreateTask = { /* ... */ },
    onListTasks = { viewModel.listTodoistTasks() },
    onListProjects = { viewModel.listTodoistProjects() },
    enabled = !isLoading
)
```

## Обработка ошибок

### Graceful Degradation

Приложение продолжает работать даже если MCP сервер недоступен:

```kotlin
// Проверка доступности перед использованием
if (viewModel.isMcpConnected()) {
    viewModel.createTodoistTask(...)
} else {
    // Показать сообщение пользователю
    showMessage("Todoist недоступен")
}
```

### Автоматический Reconnect

McpClient автоматически пытается переподключиться при потере соединения:

```kotlin
// В McpClient.kt
private fun scheduleReconnect() {
    scope.launch {
        delay(reconnectDelay) // 3 секунды
        if (_connectionState.value !is McpConnectionState.Connected) {
            connect()
        }
    }
}
```

## Отладка

### Включение логов

Логирование включено по умолчанию:

```kotlin
private val mcpRepository = McpRepository(
    serverUrl = "ws://10.0.2.2:8080/mcp",
    enableDebugLogs = true // Включить/выключить логи
)
```

### Просмотр логов

```bash
# В Android Studio Logcat фильтруйте по тегам:
McpClient - WebSocket операции
McpRepository - Операции репозитория
ChatViewModel - ViewModel логи
```

### Примеры логов:

```
D/McpClient: Connecting to ws://10.0.2.2:8080/mcp
D/McpClient: WebSocket opened
D/McpClient: Sending request: {"jsonrpc":"2.0","id":"...","method":"create_task",...}
D/McpClient: Received message: {"jsonrpc":"2.0","id":"...","result":{...}}
D/McpRepository: Creating task: Купить молоко
D/McpRepository: Task created successfully: task_id_12345
```

## Тестирование

### Команды для тестирования в чате:

```
# Создание задач
"Создай задачу: Купить молоко"
"Напомни мне позвонить врачу завтра"
"Не забыть отправить отчет в пятницу"

# Просмотр задач
"Покажи мои задачи"
"Что у меня на сегодня?"
"Задачи на эту неделю"

# Управление задачами
"Отметь задачу 12345 как выполненную"

# Проекты
"Покажи мои проекты"
"Какие у меня есть проекты?"
```

### Unit тесты (рекомендуется добавить):

```kotlin
// McpCommandParserTest.kt
@Test
fun testParseCreateTaskCommand() {
    val text = "Создай задачу: Купить молоко"
    val actions = McpCommandParser.parseActions(text)

    assertEquals(1, actions.size)
    assertTrue(actions[0].action is TodoistAction.CreateTask)
    assertEquals("Купить молоко", (actions[0].action as TodoistAction.CreateTask).content)
}
```

## Расширение функционала

### Добавление новых команд MCP

1. Добавьте метод в `McpClient.kt`:

```kotlin
suspend fun getTaskDetails(taskId: String): Result<TodoistTask> {
    val params = json.encodeToJsonElement(
        mapOf("task_id" to taskId)
    ) as JsonObject

    return sendRequest("get_task", params)
        .mapCatching { result ->
            json.decodeFromJsonElement<TodoistTask>(result)
        }
}
```

2. Добавьте действие в `TodoistModels.kt`:

```kotlin
sealed class TodoistAction {
    // ... существующие действия
    data class GetTaskDetails(val taskId: String) : TodoistAction()
}
```

3. Обновите `McpRepository.kt`:

```kotlin
suspend fun getTaskDetails(taskId: String): McpResult<TodoistTask> {
    return withContext(Dispatchers.IO) {
        try {
            val result = mcpClient.getTaskDetails(taskId)
            result.fold(
                onSuccess = { task -> McpResult.Success(task) },
                onFailure = { error -> McpResult.Error(error.message ?: "Unknown error") }
            )
        } catch (e: Exception) {
            McpResult.Error(e.message ?: "Unknown error")
        }
    }
}
```

### Кастомизация UI

Все UI компоненты используют Material 3 и легко кастомизируются:

```kotlin
// Изменить цвета приоритета в TodoistCard.kt
val color = when (priority) {
    TodoistPriority.HIGH -> Color.Red // Ваш цвет
    TodoistPriority.MEDIUM -> Color.Orange
    // ...
}
```

## Troubleshooting

### Проблема: "Not connected to MCP server"

**Решение:**
1. Проверьте, что MCP сервер запущен
2. Проверьте URL в `ChatViewModel.kt`
3. Для эмулятора используйте `10.0.2.2`, для устройства - IP компьютера
4. Проверьте firewall на компьютере

### Проблема: WebSocket timeout

**Решение:**
1. Увеличьте timeout в `McpClient.kt`:
```kotlin
private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS) // Увеличьте
    .readTimeout(60, TimeUnit.SECONDS)
    .build()
```

### Проблема: Задачи не создаются

**Решение:**
1. Проверьте логи на наличие ошибок
2. Убедитесь, что MCP сервер правильно настроен с Todoist API
3. Проверьте токен доступа Todoist

## Производительность

### Кеширование

Данные автоматически кешируются в `McpRepository`:

```kotlin
// Использовать кеш
val result = mcpRepository.listTasks(useCache = true)
```

### Оффлайн режим

При отсутствии подключения возвращаются кешированные данные:

```kotlin
// Автоматически возвращает кеш при ошибке
val tasks = mcpRepository.listTasks()
```

## Безопасность

⚠️ **Важно:**

1. **НЕ** храните токены в коде
2. Используйте `BuildConfig` или encrypted preferences для секретов
3. MCP сервер должен работать локально, не в продакшене
4. WebSocket соединение не зашифровано - используйте только в безопасной сети

## Дополнительные ресурсы

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Todoist API Documentation](https://developer.todoist.com/)
- [OkHttp WebSocket](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-web-socket/)
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)

## Поддержка

При возникновении проблем:
1. Проверьте логи в Android Studio
2. Убедитесь, что MCP сервер работает корректно
3. Проверьте сетевые настройки

---

**Автор интеграции:** Claude Code
**Дата:** 2025-11-18
**Версия:** 1.0.0