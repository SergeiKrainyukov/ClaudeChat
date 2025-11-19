package com.example.claudechat.data.mcp

import android.util.Log
import com.example.claudechat.data.mcp.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Репозиторий для управления MCP операциями и Todoist интеграцией
 *
 * Предоставляет высокоуровневый API для работы с задачами Todoist
 * через MCP сервер. Кеширует данные локально для оффлайн режима.
 */
class McpRepository(
    private val serverUrl: String = "ws://10.0.2.2:8080/mcp",
    private val enableDebugLogs: Boolean = true
) {
    companion object {
        private const val TAG = "McpRepository"
    }

    private val mcpClient = McpClient(serverUrl = serverUrl, debug = enableDebugLogs)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Локальный кеш для задач и проектов
    private val _cachedTasks = MutableStateFlow<List<TodoistTask>>(emptyList())
    val cachedTasks: StateFlow<List<TodoistTask>> = _cachedTasks.asStateFlow()

    private val _cachedProjects = MutableStateFlow<List<TodoistProject>>(emptyList())
    val cachedProjects: StateFlow<List<TodoistProject>> = _cachedProjects.asStateFlow()

    // История выполненных команд
    private val _commandHistory = MutableStateFlow<List<TodoistAction>>(emptyList())
    val commandHistory: StateFlow<List<TodoistAction>> = _commandHistory.asStateFlow()

    // Состояние подключения (проксируем из клиента)
    val connectionState: StateFlow<McpConnectionState> = mcpClient.connectionState

    init {
        // Автоматически подключаемся при создании
        connect()
    }

    // ==================== Connection Management ====================

    /**
     * Подключается к MCP серверу
     */
    fun connect() {
        try {
            mcpClient.connect()
            logDebug("Repository: Connecting to MCP server...")
        } catch (e: Exception) {
            logError("Failed to connect: ${e.message}", e)
        }
    }

    /**
     * Отключается от сервера
     */
    fun disconnect() {
        mcpClient.disconnect()
        logDebug("Repository: Disconnected from MCP server")
    }

    /**
     * Проверяет, подключен ли клиент
     */
    fun isConnected(): Boolean = mcpClient.isConnected()

    // ==================== Task Operations ====================

    /**
     * Создает новую задачу в Todoist
     */
    suspend fun createTask(
        content: String,
        description: String? = null,
        projectId: String? = null,
        dueString: String? = null,
        priority: Int? = null,
        labels: List<String>? = null
    ): McpResult<TodoistTask> {
        return withContext(Dispatchers.IO) {
            try {
                logDebug("Creating task: $content")

                val result = mcpClient.createTask(
                    content = content,
                    description = description,
                    projectId = projectId,
                    dueString = dueString,
                    priority = priority,
                    labels = labels
                )

                result.fold(
                    onSuccess = { task ->
                        // Добавляем в кеш
                        val currentTasks = _cachedTasks.value.toMutableList()
                        currentTasks.add(task)
                        _cachedTasks.value = currentTasks

                        // Добавляем в историю команд
                        addToHistory(
                            TodoistAction.CreateTask(
                                content = content,
                                description = description,
                                projectId = projectId,
                                dueString = dueString,
                                priority = priority
                            )
                        )

                        logDebug("Task created successfully: ${task.id}")
                        McpResult.Success(task)
                    },
                    onFailure = { error ->
                        logError("Failed to create task: ${error.message}", error)
                        McpResult.Error(error.message ?: "Unknown error")
                    }
                )
            } catch (e: Exception) {
                logError("Exception creating task: ${e.message}", e)
                McpResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Получает список задач (с кешированием)
     */
    suspend fun listTasks(
        projectId: String? = null,
        filter: String? = null,
        useCache: Boolean = false
    ): McpResult<List<TodoistTask>> {
        return withContext(Dispatchers.IO) {
            try {
                // Если запрошен кеш и он не пустой, возвращаем из кеша
                if (useCache && _cachedTasks.value.isNotEmpty()) {
                    logDebug("Returning cached tasks (${_cachedTasks.value.size} items)")
                    return@withContext McpResult.Success(_cachedTasks.value)
                }

                logDebug("Fetching tasks from server...")

                val result = mcpClient.listTasks(projectId = projectId, filter = filter)

                result.fold(
                    onSuccess = { tasks ->
                        // Обновляем кеш
                        _cachedTasks.value = tasks

                        // Добавляем в историю
                        if (projectId != null) {
                            addToHistory(TodoistAction.ListTasksForProject(projectId))
                        } else {
                            addToHistory(TodoistAction.ListTasks)
                        }

                        logDebug("Fetched ${tasks.size} tasks")
                        McpResult.Success(tasks)
                    },
                    onFailure = { error ->
                        logError("Failed to list tasks: ${error.message}", error)
                        // Возвращаем кеш в случае ошибки
                        if (_cachedTasks.value.isNotEmpty()) {
                            logDebug("Returning cached tasks due to error")
                            McpResult.Success(_cachedTasks.value)
                        } else {
                            McpResult.Error(error.message ?: "Unknown error")
                        }
                    }
                )
            } catch (e: Exception) {
                logError("Exception listing tasks: ${e.message}", e)
                McpResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Помечает задачу как выполненную
     */
    suspend fun completeTask(taskId: String): McpResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                logDebug("Completing task: $taskId")

                val result = mcpClient.completeTask(taskId)

                result.fold(
                    onSuccess = {
                        // Удаляем из кеша
                        val currentTasks = _cachedTasks.value.toMutableList()
                        currentTasks.removeIf { it.id == taskId }
                        _cachedTasks.value = currentTasks

                        // Добавляем в историю
                        addToHistory(TodoistAction.CompleteTask(taskId))

                        logDebug("Task completed successfully")
                        McpResult.Success(true)
                    },
                    onFailure = { error ->
                        logError("Failed to complete task: ${error.message}", error)
                        McpResult.Error(error.message ?: "Unknown error")
                    }
                )
            } catch (e: Exception) {
                logError("Exception completing task: ${e.message}", e)
                McpResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Обновляет существующую задачу
     */
    suspend fun updateTask(
        taskId: String,
        content: String? = null,
        description: String? = null,
        dueString: String? = null,
        priority: Int? = null,
        labels: List<String>? = null
    ): McpResult<TodoistTask> {
        return withContext(Dispatchers.IO) {
            try {
                logDebug("Updating task: $taskId")

                val result = mcpClient.updateTask(
                    taskId = taskId,
                    content = content,
                    description = description,
                    dueString = dueString,
                    priority = priority,
                    labels = labels
                )

                result.fold(
                    onSuccess = { task ->
                        // Обновляем в кеше
                        val currentTasks = _cachedTasks.value.toMutableList()
                        val index = currentTasks.indexOfFirst { it.id == taskId }
                        if (index != -1) {
                            currentTasks[index] = task
                            _cachedTasks.value = currentTasks
                        }

                        // Добавляем в историю
                        addToHistory(
                            TodoistAction.UpdateTask(
                                taskId = taskId,
                                content = content,
                                description = description,
                                dueString = dueString,
                                priority = priority
                            )
                        )

                        logDebug("Task updated successfully")
                        McpResult.Success(task)
                    },
                    onFailure = { error ->
                        logError("Failed to update task: ${error.message}", error)
                        McpResult.Error(error.message ?: "Unknown error")
                    }
                )
            } catch (e: Exception) {
                logError("Exception updating task: ${e.message}", e)
                McpResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Удаляет задачу
     */
    suspend fun deleteTask(taskId: String): McpResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                logDebug("Deleting task: $taskId")

                val result = mcpClient.deleteTask(taskId)

                result.fold(
                    onSuccess = {
                        // Удаляем из кеша
                        val currentTasks = _cachedTasks.value.toMutableList()
                        currentTasks.removeIf { it.id == taskId }
                        _cachedTasks.value = currentTasks

                        // Добавляем в историю
                        addToHistory(TodoistAction.DeleteTask(taskId))

                        logDebug("Task deleted successfully")
                        McpResult.Success(true)
                    },
                    onFailure = { error ->
                        logError("Failed to delete task: ${error.message}", error)
                        McpResult.Error(error.message ?: "Unknown error")
                    }
                )
            } catch (e: Exception) {
                logError("Exception deleting task: ${e.message}", e)
                McpResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ==================== Project Operations ====================

    /**
     * Получает список проектов (с кешированием)
     */
    suspend fun listProjects(
        limit: Int? = null,
        useCache: Boolean = false
    ): McpResult<List<TodoistProject>> {
        return withContext(Dispatchers.IO) {
            try {
                // Если запрошен кеш и он не пустой, возвращаем из кеша
                if (useCache && _cachedProjects.value.isNotEmpty()) {
                    logDebug("Returning cached projects (${_cachedProjects.value.size} items)")
                    return@withContext McpResult.Success(_cachedProjects.value)
                }

                logDebug("Fetching projects from server...")

                val result = mcpClient.listProjects(limit = limit)

                result.fold(
                    onSuccess = { projects ->
                        // Обновляем кеш
                        _cachedProjects.value = projects

                        // Добавляем в историю
                        addToHistory(TodoistAction.ListProjects)

                        logDebug("Fetched ${projects.size} projects")
                        McpResult.Success(projects)
                    },
                    onFailure = { error ->
                        logError("Failed to list projects: ${error.message}", error)
                        // Возвращаем кеш в случае ошибки
                        if (_cachedProjects.value.isNotEmpty()) {
                            logDebug("Returning cached projects due to error")
                            McpResult.Success(_cachedProjects.value)
                        } else {
                            McpResult.Error(error.message ?: "Unknown error")
                        }
                    }
                )
            } catch (e: Exception) {
                logError("Exception listing projects: ${e.message}", e)
                McpResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ==================== Notification Management ====================

    /**
     * Устанавливает callback для обработки уведомлений
     */
    fun setNotificationCallback(callback: ((NotificationData) -> Unit)?) {
        mcpClient.setNotificationCallback(callback)
    }

    /**
     * Включает периодические уведомления
     */
    suspend fun enableNotifications(intervalSeconds: Int = 60, maxTasks: Int = 20): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                mcpClient.enableNotifications(intervalSeconds, maxTasks)
            } catch (e: Exception) {
                logError("Failed to enable notifications: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Отключает уведомления
     */
    suspend fun disableNotifications(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                mcpClient.disableNotifications()
            } catch (e: Exception) {
                logError("Failed to disable notifications: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Изменяет интервал уведомлений
     */
    suspend fun setNotificationInterval(intervalSeconds: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                mcpClient.setNotificationInterval(intervalSeconds)
            } catch (e: Exception) {
                logError("Failed to set notification interval: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Изменяет максимальное количество задач в уведомлении
     */
    suspend fun setMaxTasks(maxTasks: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                mcpClient.setMaxTasks(maxTasks)
            } catch (e: Exception) {
                logError("Failed to set max tasks: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Получает текущий статус уведомлений
     */
    suspend fun getNotificationStatus(): NotificationStatus? {
        return withContext(Dispatchers.IO) {
            try {
                mcpClient.getNotificationStatus()
            } catch (e: Exception) {
                logError("Failed to get notification status: ${e.message}", e)
                null
            }
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Выполняет действие Todoist
     */
    suspend fun executeAction(action: TodoistAction): McpResult<*> {
        return when (action) {
            is TodoistAction.CreateTask -> createTask(
                content = action.content,
                description = action.description,
                projectId = action.projectId,
                dueString = action.dueString,
                priority = action.priority
            )
            is TodoistAction.CompleteTask -> completeTask(action.taskId)
            is TodoistAction.ListTasks -> listTasks()
            is TodoistAction.ListTasksForProject -> listTasks(projectId = action.projectId)
            is TodoistAction.ListProjects -> listProjects()
            is TodoistAction.UpdateTask -> updateTask(
                taskId = action.taskId,
                content = action.content,
                description = action.description,
                dueString = action.dueString,
                priority = action.priority
            )
            is TodoistAction.DeleteTask -> deleteTask(action.taskId)
        }
    }

    /**
     * Добавляет действие в историю команд
     */
    private fun addToHistory(action: TodoistAction) {
        val history = _commandHistory.value.toMutableList()
        history.add(action)
        // Ограничиваем историю последними 50 командами
        if (history.size > 50) {
            history.removeAt(0)
        }
        _commandHistory.value = history
    }

    /**
     * Очищает кеш
     */
    fun clearCache() {
        _cachedTasks.value = emptyList()
        _cachedProjects.value = emptyList()
        logDebug("Cache cleared")
    }

    /**
     * Очищает историю команд
     */
    fun clearHistory() {
        _commandHistory.value = emptyList()
        logDebug("Command history cleared")
    }

    /**
     * Освобождает ресурсы
     */
    fun dispose() {
        mcpClient.dispose()
        scope.cancel()
        logDebug("Repository disposed")
    }

    private fun logDebug(message: String) {
        if (enableDebugLogs) {
            Log.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable?) {
        Log.e(TAG, message, throwable)
    }
}