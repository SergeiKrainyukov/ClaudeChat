package com.example.claudechat.data.mcp

import android.util.Log
import com.example.claudechat.data.mcp.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * WebSocket клиент для работы с MCP (Model Context Protocol) сервером
 *
 * Поддерживает:
 * - JSON-RPC 2.0 через WebSocket
 * - Асинхронные запросы с корутинами
 * - Автоматический reconnect
 * - Логирование всех операций
 */
@OptIn(ExperimentalSerializationApi::class)
class McpClient(
    private val serverUrl: String = DEFAULT_SERVER_URL,
    private val reconnectDelay: Long = 3000L,
    private val debug: Boolean = true
) {
    companion object {
        private const val TAG = "McpClient"
        private const val DEFAULT_SERVER_URL = "ws://10.0.2.2:8080/mcp"
        private const val REQUEST_TIMEOUT = 30000L // 30 секунд
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false  // Не сериализовать null значения
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callback для обработки входящих уведомлений
    @Volatile
    private var notificationCallback: ((NotificationData) -> Unit)? = null

    // Флаг инициализации MCP протокола
    @Volatile
    private var isInitialized = false

    // Flow для состояния подключения
    private val _connectionState = MutableStateFlow<McpConnectionState>(McpConnectionState.Disconnected)
    val connectionState: StateFlow<McpConnectionState> = _connectionState.asStateFlow()

    // Channel для обработки входящих сообщений
    private val messageChannel = Channel<String>(Channel.UNLIMITED)

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            logDebug("WebSocket opened: $response")
            _connectionState.value = McpConnectionState.Connected

            // Инициализация MCP протокола
            scope.launch {
                initializeMcp()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            logDebug("Received message: $text")
            scope.launch {
                messageChannel.send(text)
                handleMessage(text)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            logDebug("WebSocket closing: code=$code, reason=$reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logDebug("WebSocket closed: code=$code, reason=$reason")
            _connectionState.value = McpConnectionState.Disconnected

            // Автоматический reconnect
            if (code != 1000) { // 1000 = normal closure
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logError("WebSocket failure: ${t.message}", t)
            _connectionState.value = McpConnectionState.Error(t.message ?: "Unknown error")

            // Отменяем все ожидающие запросы
            pendingRequests.values.forEach { deferred ->
                deferred.completeExceptionally(t)
            }
            pendingRequests.clear()

            scheduleReconnect()
        }
    }

    /**
     * Подключается к MCP серверу
     */
    fun connect() {
        if (_connectionState.value is McpConnectionState.Connected) {
            logDebug("Already connected")
            return
        }

        _connectionState.value = McpConnectionState.Connecting
        logDebug("Connecting to $serverUrl")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, webSocketListener)
    }

    /**
     * Отключается от сервера
     */
    fun disconnect() {
        logDebug("Disconnecting...")
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isInitialized = false
        _connectionState.value = McpConnectionState.Disconnected
    }

    /**
     * Инициализирует MCP протокол (handshake)
     */
    private suspend fun initializeMcp() {
        try {
            logDebug("Initializing MCP protocol...")

            // Шаг 1: Отправка "initialize" запроса
            val initParams = buildJsonObject {
                put("protocolVersion", "2024-11-05")
                putJsonObject("capabilities") {
                    // Пустые capabilities для клиента
                }
                putJsonObject("clientInfo") {
                    put("name", "android-mcp-client")
                    put("version", "1.0.0")
                }
            }

            val initResult = sendRequestInternal("initialize", initParams)
            if (initResult.isFailure) {
                logError("MCP initialization failed", initResult.exceptionOrNull())
                return
            }

            logDebug("MCP initialize successful, sending initialized notification...")

            // Шаг 2: Отправка "initialized" уведомления (без params)
            val initializedResult = sendRequestInternal("initialized", null)
            if (initializedResult.isFailure) {
                logError("MCP initialized notification failed", initializedResult.exceptionOrNull())
                return
            }

            // Шаг 3: Установка флага инициализации
            isInitialized = true
            logDebug("MCP protocol initialized successfully!")

        } catch (e: Exception) {
            logError("MCP initialization exception: ${e.message}", e)
            isInitialized = false
        }
    }

    /**
     * Внутренний метод отправки запроса (без проверки инициализации)
     */
    private suspend fun sendRequestInternal(method: String, params: JsonObject? = null): Result<JsonElement> {
        if (_connectionState.value !is McpConnectionState.Connected) {
            return Result.failure(Exception("Not connected to MCP server"))
        }

        val requestId = UUID.randomUUID().toString()
        val request = McpRequest(
            id = requestId,
            method = method,
            params = params
        )

        val deferred = CompletableDeferred<JsonElement>()
        pendingRequests[requestId] = deferred

        return try {
            val requestJson = json.encodeToString(request)
            logDebug("Sending request: $requestJson")

            val sent = webSocket?.send(requestJson) ?: false
            if (!sent) {
                pendingRequests.remove(requestId)
                return Result.failure(Exception("Failed to send request"))
            }

            // Ожидание ответа с таймаутом
            val result = withTimeout(REQUEST_TIMEOUT) {
                deferred.await()
            }

            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(requestId)
            logError("Request timeout for method: $method", e)
            Result.failure(Exception("Request timeout"))
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            logError("Request failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Отправляет JSON-RPC запрос и возвращает результат
     */
    private suspend fun sendRequest(method: String, params: JsonObject? = null): Result<JsonElement> {
        if (_connectionState.value !is McpConnectionState.Connected) {
            return Result.failure(Exception("Not connected to MCP server"))
        }

        // Ожидание инициализации для всех методов кроме initialize и initialized
        if (method != "initialize" && method != "initialized") {
            var waitCount = 0
            while (!isInitialized && waitCount < 50) { // Максимум 5 секунд ожидания
                delay(100)
                waitCount++
            }

            if (!isInitialized) {
                return Result.failure(Exception("MCP protocol not initialized"))
            }
        }

        val requestId = UUID.randomUUID().toString()
        val request = McpRequest(
            id = requestId,
            method = method,
            params = params
        )

        val deferred = CompletableDeferred<JsonElement>()
        pendingRequests[requestId] = deferred

        return try {
            val requestJson = json.encodeToString(request)
            logDebug("Sending request: $requestJson")

            val sent = webSocket?.send(requestJson) ?: false
            if (!sent) {
                pendingRequests.remove(requestId)
                return Result.failure(Exception("Failed to send request"))
            }

            // Ожидание ответа с таймаутом
            val result = withTimeout(REQUEST_TIMEOUT) {
                deferred.await()
            }

            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(requestId)
            logError("Request timeout for method: $method", e)
            Result.failure(Exception("Request timeout"))
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            logError("Request failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Обрабатывает входящие сообщения от сервера
     */
    private fun handleMessage(text: String) {
        try {
            // Парсим как JsonObject чтобы проверить наличие полей
            val jsonObject = json.parseToJsonElement(text).jsonObject

            // Проверяем наличие поля id
            val id = jsonObject["id"]?.jsonPrimitive?.contentOrNull
            val method = jsonObject["method"]?.jsonPrimitive?.contentOrNull

            // Если id == null и есть method - это уведомление
            if (id == null && method != null) {
                handleNotification(method, jsonObject)
                return
            }

            // Если есть id - это обычный ответ на запрос
            if (id != null) {
                val response = json.decodeFromString<McpResponse>(text)

                val deferred = pendingRequests.remove(response.id)
                if (deferred == null) {
                    logError("No pending request found for id: ${response.id}", null)
                    return
                }

                when {
                    response.error != null -> {
                        logError("MCP error: ${response.error.message}", null)
                        deferred.completeExceptionally(
                            Exception("MCP error [${response.error.code}]: ${response.error.message}")
                        )
                    }
                    response.result != null -> {
                        logDebug("Request ${response.id} completed successfully")
                        deferred.complete(response.result)
                    }
                    else -> {
                        logError("Invalid response: no result or error", null)
                        deferred.completeExceptionally(Exception("Invalid response"))
                    }
                }
            }
        } catch (e: Exception) {
            logError("Failed to parse message: ${e.message}", e)
        }
    }

    /**
     * Обрабатывает входящее уведомление от сервера
     */
    private fun handleNotification(method: String, jsonObject: JsonObject) {
        try {
            logDebug("Received notification: method=$method")

            when (method) {
                "notifications/tasks" -> {
                    // Извлекаем params
                    val params = jsonObject["params"]?.jsonObject
                    if (params != null) {
                        val notificationData = json.decodeFromJsonElement<NotificationData>(params)
                        logDebug("Task notification: ${notificationData.taskCount} tasks")

                        // Вызываем callback если установлен
                        notificationCallback?.invoke(notificationData)
                    } else {
                        logError("Notification params is null", null)
                    }
                }
                else -> {
                    logDebug("Unknown notification method: $method")
                }
            }
        } catch (e: Exception) {
            logError("Failed to handle notification: ${e.message}", e)
        }
    }

    /**
     * Планирует переподключение
     */
    private fun scheduleReconnect() {
        scope.launch {
            delay(reconnectDelay)
            if (_connectionState.value !is McpConnectionState.Connected) {
                logDebug("Attempting to reconnect...")
                connect()
            }
        }
    }

    // ==================== Todoist API Methods ====================

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
    ): Result<TodoistTask> {
        val taskParams = McpParams.CreateTask(
            content = content,
            description = description,
            project_id = projectId,
            due_string = dueString,
            priority = priority,
            labels = labels
        )

        // Обертываем в tools/call
        val params = buildJsonObject {
            put("name", "create_task")
            put("arguments", json.encodeToJsonElement(taskParams))
        }

        return sendRequest("tools/call", params)
            .mapCatching { result ->
                // Извлекаем content из ответа MCP
                val contentObj = result.jsonObject["content"]?.jsonArray?.firstOrNull()
                val text = contentObj?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: throw Exception("Invalid response format")

                // Парсим текстовый ответ
                parseTaskFromText(text, content)
            }
    }

    /**
     * Парсит задачу из текстового ответа create_task
     */
    private fun parseTaskFromText(text: String, fallbackContent: String): TodoistTask {
        val lines = text.lines()
        var taskId: String? = null
        var taskContent: String = fallbackContent
        var projectId: String? = null

        lines.forEach { line ->
            when {
                line.startsWith("ID:") -> {
                    taskId = line.substringAfter("ID:").trim()
                }
                line.startsWith("Content:") -> {
                    taskContent = line.substringAfter("Content:").trim()
                }
                line.startsWith("Project ID:") -> {
                    val value = line.substringAfter("Project ID:").trim()
                    projectId = if (value != "None" && value.isNotBlank()) value else null
                }
            }
        }

        return TodoistTask(
            id = taskId ?: "",
            content = taskContent,
            description = "",
            projectId = projectId,
            isCompleted = false,
            priority = 1,
            labels = emptyList(),
            due = null,
            createdAt = null,
            url = null
        )
    }

    /**
     * Получает список задач
     */
    suspend fun listTasks(
        projectId: String? = null,
        filter: String? = null
    ): Result<List<TodoistTask>> {
        val taskParams = McpParams.ListTasks(
            project_id = projectId,
            filter = filter
        )

        // Обертываем в tools/call
        val params = buildJsonObject {
            put("name", "list_tasks")
            put("arguments", json.encodeToJsonElement(taskParams))
        }

        return sendRequest("tools/call", params)
            .mapCatching { result ->
                // Извлекаем content из ответа MCP
                val content = result.jsonObject["content"]?.jsonArray?.firstOrNull()
                val text = content?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: throw Exception("Invalid response format")

                // Парсим markdown список задач
                parseTasksFromMarkdown(text)
            }
    }

    /**
     * Парсит список задач из markdown формата
     */
    private fun parseTasksFromMarkdown(markdown: String): List<TodoistTask> {
        val tasks = mutableListOf<TodoistTask>()
        val taskRegex = """- \[ \] (.+?) \(ID: (\d+)\)""".toRegex()

        markdown.lines().forEach { line ->
            taskRegex.find(line)?.let { match ->
                val content = match.groupValues[1]
                val id = match.groupValues[2]
                tasks.add(
                    TodoistTask(
                        id = id,
                        content = content,
                        description = "",
                        projectId = null,
                        isCompleted = false,
                        priority = 1,
                        labels = emptyList(),
                        due = null,
                        createdAt = null,
                        url = null
                    )
                )
            }
        }

        return tasks
    }

    /**
     * Помечает задачу как выполненную
     */
    suspend fun completeTask(taskId: String): Result<Boolean> {
        val taskParams = McpParams.CompleteTask(task_id = taskId)

        // Обертываем в tools/call
        val params = buildJsonObject {
            put("name", "complete_task")
            put("arguments", json.encodeToJsonElement(taskParams))
        }

        return sendRequest("tools/call", params)
            .map { true }
    }

    /**
     * Получает список проектов
     */
    suspend fun listProjects(limit: Int? = null): Result<List<TodoistProject>> {
        val projectParams = McpParams.ListProjects(limit = limit)

        // Обертываем в tools/call
        val params = buildJsonObject {
            put("name", "list_projects")
            put("arguments", json.encodeToJsonElement(projectParams))
        }

        return sendRequest("tools/call", params)
            .mapCatching { result ->
                // Извлекаем content из ответа MCP
                val content = result.jsonObject["content"]?.jsonArray?.firstOrNull()
                val text = content?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: throw Exception("Invalid response format")

                // Парсим markdown список проектов
                parseProjectsFromMarkdown(text)
            }
    }

    /**
     * Парсит список проектов из markdown формата
     */
    private fun parseProjectsFromMarkdown(markdown: String): List<TodoistProject> {
        val projects = mutableListOf<TodoistProject>()
        val projectRegex = """- (.+?) \(ID: (\d+)\) \[(\w+)\]""".toRegex()

        markdown.lines().forEach { line ->
            projectRegex.find(line)?.let { match ->
                val name = match.groupValues[1]
                val id = match.groupValues[2]
                val color = match.groupValues[3]
                projects.add(
                    TodoistProject(
                        id = id,
                        name = name,
                        color = color,
                        parentId = null,
                        order = 0,
                        isFavorite = false,
                        isShared = false,
                        url = null
                    )
                )
            }
        }

        return projects
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
    ): Result<TodoistTask> {
        val taskParams = McpParams.UpdateTask(
            task_id = taskId,
            content = content,
            description = description,
            due_string = dueString,
            priority = priority,
            labels = labels
        )

        // Обертываем в tools/call
        val params = buildJsonObject {
            put("name", "update_task")
            put("arguments", json.encodeToJsonElement(taskParams))
        }

        return sendRequest("tools/call", params)
            .mapCatching { result ->
                // Извлекаем content из ответа MCP
                val content = result.jsonObject["content"]?.jsonArray?.firstOrNull()
                val text = content?.jsonObject?.get("text")?.jsonPrimitive?.content
                    ?: throw Exception("Invalid response format")
                json.decodeFromString<TodoistTask>(text)
            }
    }

    /**
     * Удаляет задачу
     */
    suspend fun deleteTask(taskId: String): Result<Boolean> {
        val taskParams = McpParams.DeleteTask(task_id = taskId)

        // Обертываем в tools/call
        val params = buildJsonObject {
            put("name", "delete_task")
            put("arguments", json.encodeToJsonElement(taskParams))
        }

        return sendRequest("tools/call", params)
            .map { true }
    }

    // ==================== Notification Management Methods ====================

    /**
     * Включает периодические уведомления о задачах
     *
     * @param intervalSeconds Интервал отправки в секундах (минимум 1, по умолчанию 60)
     * @param maxTasks Максимальное количество задач в уведомлении (минимум 1, по умолчанию 20)
     * @return true если уведомления успешно включены, false в случае ошибки
     */
    suspend fun enableNotifications(intervalSeconds: Int = 60, maxTasks: Int = 20): Boolean {
        return try {
            val params = buildJsonObject {
                put("intervalSeconds", intervalSeconds)
                put("maxTasks", maxTasks)
            }

            val result = sendRequest("notifications/enable", params)
            if (result.isSuccess) {
                val success = result.getOrNull()?.jsonObject?.get("success")?.jsonPrimitive?.boolean
                success ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            logError("Failed to enable notifications: ${e.message}", e)
            false
        }
    }

    /**
     * Отключает периодические уведомления о задачах
     *
     * @return true если уведомления успешно отключены, false в случае ошибки
     */
    suspend fun disableNotifications(): Boolean {
        return try {
            val result = sendRequest("notifications/disable", null)
            if (result.isSuccess) {
                val success = result.getOrNull()?.jsonObject?.get("success")?.jsonPrimitive?.boolean
                success ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            logError("Failed to disable notifications: ${e.message}", e)
            false
        }
    }

    /**
     * Изменяет интервал уведомлений (работает даже если уведомления уже включены)
     *
     * @param intervalSeconds Новый интервал в секундах (минимум 1)
     * @return true если интервал успешно изменен, false в случае ошибки
     */
    suspend fun setNotificationInterval(intervalSeconds: Int): Boolean {
        return try {
            val params = buildJsonObject {
                put("intervalSeconds", intervalSeconds)
            }

            val result = sendRequest("notifications/setInterval", params)
            if (result.isSuccess) {
                val success = result.getOrNull()?.jsonObject?.get("success")?.jsonPrimitive?.boolean
                success ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            logError("Failed to set notification interval: ${e.message}", e)
            false
        }
    }

    /**
     * Изменяет максимальное количество задач в уведомлении (работает даже если уведомления уже включены)
     *
     * @param maxTasks Максимальное количество задач (минимум 1)
     * @return true если значение успешно изменено, false в случае ошибки
     */
    suspend fun setMaxTasks(maxTasks: Int): Boolean {
        return try {
            val params = buildJsonObject {
                put("maxTasks", maxTasks)
            }

            val result = sendRequest("notifications/setMaxTasks", params)
            if (result.isSuccess) {
                val success = result.getOrNull()?.jsonObject?.get("success")?.jsonPrimitive?.boolean
                success ?: false
            } else {
                false
            }
        } catch (e: Exception) {
            logError("Failed to set max tasks: ${e.message}", e)
            false
        }
    }

    /**
     * Получает текущий статус уведомлений
     *
     * @return NotificationStatus с информацией о статусе или null в случае ошибки
     */
    suspend fun getNotificationStatus(): NotificationStatus? {
        return try {
            val result = sendRequest("notifications/getStatus", null)
            if (result.isSuccess) {
                val statusJson = result.getOrNull()
                if (statusJson != null) {
                    json.decodeFromJsonElement<NotificationStatus>(statusJson)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logError("Failed to get notification status: ${e.message}", e)
            null
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Устанавливает callback для обработки уведомлений
     * Должен быть вызван ПЕРЕД подключением к серверу
     */
    fun setNotificationCallback(callback: ((NotificationData) -> Unit)?) {
        notificationCallback = callback
        logDebug("Notification callback ${if (callback != null) "set" else "cleared"}")
    }

    /**
     * Проверяет, подключен ли клиент
     */
    fun isConnected(): Boolean {
        return _connectionState.value is McpConnectionState.Connected
    }

    /**
     * Очищает ресурсы
     */
    fun dispose() {
        disconnect()
        scope.cancel()
        messageChannel.close()
    }

    private fun logDebug(message: String) {
        if (debug) {
            Log.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable?) {
        Log.e(TAG, message, throwable)
    }
}