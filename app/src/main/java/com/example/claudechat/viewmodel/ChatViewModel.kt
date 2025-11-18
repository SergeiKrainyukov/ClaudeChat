package com.example.claudechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.example.claudechat.model.Message
import com.example.claudechat.repository.ChatRepository
import com.example.claudechat.utils.ChatType
import com.example.claudechat.utils.SystemPrompts
import com.example.claudechat.data.mcp.McpRepository
import com.example.claudechat.data.mcp.models.*
import com.example.claudechat.utils.McpCommandParser
import com.example.claudechat.utils.ParsedAction
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application.applicationContext)

    // MCP/Todoist integration
    private val mcpRepository = McpRepository(
        serverUrl = "ws://10.0.2.2:8080/mcp",
        enableDebugLogs = true
    )

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _temperature = MutableLiveData(1.0)
    val temperature: LiveData<Double> = _temperature

    private val _compressionStats = MutableLiveData<Triple<Int, Int, Int>>(Triple(0, 0, 0))
    val compressionStats: LiveData<Triple<Int, Int, Int>> = _compressionStats

    private var currentChatType: ChatType = ChatType.DEFAULT

    // MCP состояние
    val mcpConnectionState: LiveData<McpConnectionState> = mcpRepository.connectionState.asLiveData()
    val cachedTasks: LiveData<List<TodoistTask>> = mcpRepository.cachedTasks.asLiveData()
    val cachedProjects: LiveData<List<TodoistProject>> = mcpRepository.cachedProjects.asLiveData()
    
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Добавляем сообщение пользователя
        val userMessage = Message(text = text, isUser = true)
        addMessage(userMessage)

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            // Проверяем, нужна ли компрессия перед отправкой
            if (repository.shouldCompress()) {
                compressHistoryIfNeeded()
            }

            repository.sendMessage(text)
                .onSuccess { response ->
                    // Добавляем ответ Claude с confidence и токенами
                    val assistantMessage = Message(
                        text = response.text,
                        isUser = false,
                        confidence = response.confidence,
                        useMarkdown = currentChatType == ChatType.MULTI_AGENT,
                        inputTokens = response.inputTokens,
                        outputTokens = response.outputTokens,
                        totalTokens = response.totalTokens
                    )
                    addMessage(assistantMessage)
                    _isLoading.value = false

                    // Обновляем статистику компрессии
                    updateCompressionStats()
                }
                .onFailure { exception ->
                    _error.value = "Ошибка: ${exception.message}"
                    _isLoading.value = false
                }
        }
    }
    
    private fun addMessage(message: Message) {
        val currentMessages = _messages.value.orEmpty().toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages
    }

    fun clearChat() {
        _messages.value = emptyList()
        repository.clearHistory()
    }

    /**
     * Загружает начальное сообщение в чат
     * Если это summary, добавляет его в system prompt вместо отображения в чате
     */
    fun loadInitialMessage(content: String, isSummary: Boolean = false) {
        clearChat()

        if (isSummary) {
            // Если это summary, добавляем его в system prompt для контекста
            val systemPromptWithSummary = """
                Вы - AI-ассистент Claude. Используйте следующую информацию из предыдущего диалога для контекста:

                $content

                Продолжайте беседу, используя этот контекст, но не упоминайте напрямую, что получили резюме.
            """.trimIndent()

            repository.setSystemPrompt(systemPromptWithSummary)

            // Добавляем информационное сообщение в UI
            val infoMessage = Message(
                text = "📋 Загружен контекст из предыдущего диалога. Можете продолжить беседу.",
                isUser = false,
                useMarkdown = false,
                isSummary = false
            )
            addMessage(infoMessage)
        } else {
            // Если это обычное сообщение, просто отображаем его
            val initialMessage = Message(
                text = content,
                isUser = false,
                useMarkdown = true,
                isSummary = false
            )
            addMessage(initialMessage)
        }
    }

    /**
     * Создает summary текущего диалога и сохраняет в БД
     */
    fun saveSummary() {
        if (repository.getHistorySize() == 0) {
            _error.value = "История диалога пуста"
            return
        }

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = repository.createAndSaveSummary()

            result.fold(
                onSuccess = { (summary, savedTokens) ->
                    println("ChatViewModel: Summary успешно сохранен - savedTokens: $savedTokens")

                    // Добавляем summary как сообщение в UI
                    val summaryMessage = Message(
                        text = summary,
                        isUser = false,
                        useMarkdown = true,
                        isSummary = true,
                        savedTokens = savedTokens,
                        originalMessagesCount = repository.getHistorySize()
                    )
                    addMessage(summaryMessage)

                    _isLoading.value = false
                },
                onFailure = { e ->
                    println("ChatViewModel: Ошибка сохранения summary - ${e.message}")
                    _error.value = "Ошибка создания summary: ${e.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Получает количество сообщений в истории
     */
    fun getHistorySize(): Int = repository.getHistorySize()

    /**
     * Устанавливает режим чата (обычный или многоагентный)
     */
    fun setMultiAgentMode(isMultiAgent: Boolean) {
        val newChatType = if (isMultiAgent) ChatType.MULTI_AGENT else ChatType.DEFAULT

        // Если режим изменился, очищаем чат и устанавливаем новый system prompt
        if (currentChatType != newChatType) {
            currentChatType = newChatType
            clearChat()
            repository.setSystemPrompt(SystemPrompts.getPrompt(currentChatType))
        }
    }

    /**
     * Устанавливает температуру для запросов
     */
    fun setTemperature(temp: Double) {
        val validTemp = temp.coerceIn(0.0, 1.0)
        _temperature.value = validTemp
        repository.setTemperature(validTemp)
    }

    /**
     * Выполняет компрессию истории диалога
     */
    private suspend fun compressHistoryIfNeeded() {
        repository.compressHistory()
            .onSuccess { (summary, savedTokens) ->
                // Добавляем summary сообщение в UI
                val summaryMessage = Message(
                    text = "📊 История диалога сжата. Сэкономлено ~$savedTokens токенов.\n\nРезюме: $summary",
                    isUser = false,
                    isSummary = true,
                    originalMessagesCount = 10,
                    savedTokens = savedTokens,
                    useMarkdown = true
                )

                // Удаляем сжатые сообщения из UI (первые 10)
                val currentMessages = _messages.value.orEmpty().toMutableList()
                if (currentMessages.size >= 10) {
                    // Удаляем первые 10 сообщений
                    repeat(10) {
                        if (currentMessages.isNotEmpty()) {
                            currentMessages.removeAt(0)
                        }
                    }
                }
                // Добавляем summary в начало
                currentMessages.add(0, summaryMessage)
                _messages.value = currentMessages
            }
            .onFailure { exception ->
                // Логируем ошибку, но не показываем пользователю
                println("Ошибка компрессии: ${exception.message}")
            }
    }

    /**
     * Обновляет статистику компрессии
     */
    private fun updateCompressionStats() {
        _compressionStats.value = repository.getCompressionStats()
    }

    /**
     * Включает/выключает компрессию
     */
    fun setCompressionEnabled(enabled: Boolean) {
        repository.setCompressionEnabled(enabled)
    }

    // ==================== MCP/Todoist Methods ====================

    /**
     * Отправляет сообщение с проверкой на Todoist команды
     */
    fun sendMessageWithMcp(text: String) {
        if (text.isBlank()) return

        // Парсим сообщение на наличие Todoist команд
        val parsedActions = McpCommandParser.parseActions(text)

        // Добавляем сообщение пользователя с распарсенными действиями
        val userMessage = Message(
            text = text,
            isUser = true,
            todoistActions = parsedActions,
            hasTodoistSuggestion = parsedActions.isNotEmpty()
        )
        addMessage(userMessage)

        // Если есть команды с высоким confidence, выполняем автоматически
        parsedActions
            .filter { it.confidence >= 0.9 }
            .forEach { parsedAction ->
                executeTodoistAction(parsedAction.action)
            }

        // Отправляем обычное сообщение Claude
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            if (repository.shouldCompress()) {
                compressHistoryIfNeeded()
            }

            repository.sendMessage(text)
                .onSuccess { response ->
                    // Парсим ответ Claude на наличие предложений Todoist
                    val claudeActions = McpCommandParser.parseActions(response.text)

                    val assistantMessage = Message(
                        text = response.text,
                        isUser = false,
                        confidence = response.confidence,
                        useMarkdown = currentChatType == ChatType.MULTI_AGENT,
                        inputTokens = response.inputTokens,
                        outputTokens = response.outputTokens,
                        totalTokens = response.totalTokens,
                        todoistActions = claudeActions,
                        hasTodoistSuggestion = claudeActions.isNotEmpty()
                    )
                    addMessage(assistantMessage)
                    _isLoading.value = false

                    updateCompressionStats()
                }
                .onFailure { exception ->
                    _error.value = "Ошибка: ${exception.message}"
                    _isLoading.value = false
                }
        }
    }

    /**
     * Выполняет действие Todoist
     */
    fun executeTodoistAction(action: TodoistAction) {
        viewModelScope.launch {
            _isLoading.value = true

            when (val result = mcpRepository.executeAction(action)) {
                is McpResult.Success<*> -> {
                    val successMessage = when (action) {
                        is TodoistAction.CreateTask -> {
                            val task = result.data as? TodoistTask
                            "✅ Задача создана: ${task?.content ?: action.content}"
                        }
                        is TodoistAction.CompleteTask -> "✅ Задача выполнена"
                        is TodoistAction.ListTasks -> {
                            val tasks = result.data as? List<*>
                            "📋 Найдено задач: ${tasks?.size ?: 0}"
                        }
                        is TodoistAction.ListProjects -> {
                            val projects = result.data as? List<*>
                            "📁 Найдено проектов: ${projects?.size ?: 0}"
                        }
                        is TodoistAction.UpdateTask -> "✅ Задача обновлена"
                        is TodoistAction.DeleteTask -> "✅ Задача удалена"
                        else -> "✅ Действие выполнено"
                    }

                    // Добавляем сообщение об успехе
                    val successMsg = Message(
                        text = successMessage,
                        isUser = false,
                        useMarkdown = false
                    )
                    addMessage(successMsg)
                }
                is McpResult.Error -> {
                    _error.value = "Ошибка MCP: ${result.message}"
                }
                else -> {}
            }

            _isLoading.value = false
        }
    }

    /**
     * Создает задачу в Todoist
     */
    fun createTodoistTask(
        content: String,
        description: String? = null,
        dueString: String? = null,
        priority: Int? = null
    ) {
        executeTodoistAction(
            TodoistAction.CreateTask(
                content = content,
                description = description,
                dueString = dueString,
                priority = priority
            )
        )
    }

    /**
     * Получает список задач из Todoist
     */
    fun listTodoistTasks() {
        executeTodoistAction(TodoistAction.ListTasks)
    }

    /**
     * Помечает задачу как выполненную
     */
    fun completeTodoistTask(taskId: String) {
        executeTodoistAction(TodoistAction.CompleteTask(taskId))
    }

    /**
     * Получает список проектов
     */
    fun listTodoistProjects() {
        executeTodoistAction(TodoistAction.ListProjects)
    }

    /**
     * Проверяет, подключен ли MCP сервер
     */
    fun isMcpConnected(): Boolean {
        return mcpRepository.isConnected()
    }

    /**
     * Переподключается к MCP серверу
     */
    fun reconnectMcp() {
        mcpRepository.connect()
    }

    /**
     * Очищает ресурсы при уничтожении ViewModel
     */
    override fun onCleared() {
        super.onCleared()
        mcpRepository.dispose()
    }
}
