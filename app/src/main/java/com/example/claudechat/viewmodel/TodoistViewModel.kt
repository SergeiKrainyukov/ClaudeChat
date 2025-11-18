package com.example.claudechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.claudechat.data.mcp.McpRepository
import com.example.claudechat.data.mcp.models.*
import com.example.claudechat.utils.TodoistAiInterpreter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Сообщение в чате Todoist
 */
data class TodoistChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ViewModel для управления Todoist через MCP
 */
class TodoistViewModel(application: Application) : AndroidViewModel(application) {

    private val mcpRepository = McpRepository(
        serverUrl = "ws://10.0.2.2:8080/mcp",
        enableDebugLogs = true
    )

    // AI интерпретатор для понимания естественного языка
    private val aiInterpreter = TodoistAiInterpreter(application.applicationContext)

    // Состояние подключения MCP
    val mcpConnectionState: StateFlow<McpConnectionState> = mcpRepository.connectionState

    // Кешированные задачи
    private val _tasks = MutableLiveData<List<TodoistTask>>(emptyList())
    val tasks: LiveData<List<TodoistTask>> = _tasks

    // Кешированные проекты
    private val _projects = MutableLiveData<List<TodoistProject>>(emptyList())
    val projects: LiveData<List<TodoistProject>> = _projects

    // Состояние загрузки
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Сообщения об ошибках
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Сообщения об успехе
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    // История чата
    private val _chatMessages = MutableLiveData<List<TodoistChatMessage>>(emptyList())
    val chatMessages: LiveData<List<TodoistChatMessage>> = _chatMessages

    init {
        // Подключаемся к MCP серверу при создании ViewModel
        mcpRepository.connect()

        // Приветственное сообщение в чате
        _chatMessages.value = listOf(
            TodoistChatMessage(
                text = "Привет! Я AI-помощник для управления задачами Todoist.\n\nПишите естественным языком, я пойму:\n• Создай задачу купить молоко\n• Покажи мои задачи\n• Добавь задачу позвонить маме завтра\n• Какие у меня проекты?\n• Удали задачу 12345\n\nМожете писать в любом формате - я использую Claude AI для понимания ваших намерений!",
                isUser = false
            )
        )
    }

    /**
     * Создает новую задачу
     */
    fun createTask(
        content: String,
        description: String? = null,
        projectId: String? = null,
        dueString: String? = null,
        priority: Int? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val action = TodoistAction.CreateTask(
                content = content,
                description = description,
                projectId = projectId,
                dueString = dueString,
                priority = priority
            )

            when (val result = mcpRepository.executeAction(action)) {
                is McpResult.Success -> {
                    _successMessage.value = "Задача создана: $content"
                    // Обновляем список задач
                    listTasks()
                }
                is McpResult.Error -> {
                    _errorMessage.value = "Ошибка создания задачи: ${result.message}"
                }
                is McpResult.Loading -> {}
            }

            _isLoading.value = false
        }
    }

    /**
     * Получает список всех задач
     */
    fun listTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            when (val result = mcpRepository.executeAction(TodoistAction.ListTasks)) {
                is McpResult.Success -> {
                    _tasks.value = result.data as? List<TodoistTask> ?: emptyList()
                }
                is McpResult.Error -> {
                    _errorMessage.value = "Ошибка загрузки задач: ${result.message}"
                }
                is McpResult.Loading -> {}
            }

            _isLoading.value = false
        }
    }

    /**
     * Помечает задачу как выполненную
     */
    fun completeTask(taskId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val action = TodoistAction.CompleteTask(taskId = taskId)

            when (val result = mcpRepository.executeAction(action)) {
                is McpResult.Success -> {
                    _successMessage.value = "Задача выполнена"
                    // Обновляем список задач
                    listTasks()
                }
                is McpResult.Error -> {
                    _errorMessage.value = "Ошибка выполнения задачи: ${result.message}"
                }
                is McpResult.Loading -> {}
            }

            _isLoading.value = false
        }
    }

    /**
     * Удаляет задачу
     */
    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val action = TodoistAction.DeleteTask(taskId = taskId)

            when (val result = mcpRepository.executeAction(action)) {
                is McpResult.Success -> {
                    _successMessage.value = "Задача удалена"
                    // Обновляем список задач
                    listTasks()
                }
                is McpResult.Error -> {
                    _errorMessage.value = "Ошибка удаления задачи: ${result.message}"
                }
                is McpResult.Loading -> {}
            }

            _isLoading.value = false
        }
    }

    /**
     * Получает список проектов
     */
    fun listProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            when (val result = mcpRepository.executeAction(TodoistAction.ListProjects)) {
                is McpResult.Success -> {
                    _projects.value = result.data as? List<TodoistProject> ?: emptyList()
                }
                is McpResult.Error -> {
                    _errorMessage.value = "Ошибка загрузки проектов: ${result.message}"
                }
                is McpResult.Loading -> {}
            }

            _isLoading.value = false
        }
    }

    /**
     * Переподключается к MCP серверу
     */
    fun reconnect() {
        mcpRepository.disconnect()
        mcpRepository.connect()
    }

    /**
     * Проверяет подключение к MCP
     */
    fun isMcpConnected(): Boolean {
        return mcpConnectionState.value is McpConnectionState.Connected
    }

    /**
     * Очищает сообщение об ошибке
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Очищает сообщение об успехе
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    // ==================== Chat Methods ====================

    /**
     * Отправляет сообщение в чат и обрабатывает команды через Claude AI
     */
    fun sendChatMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Добавляем сообщение пользователя
            addChatMessage(TodoistChatMessage(text, isUser = true))

            // Используем AI для интерпретации сообщения
            val interpretationResult = aiInterpreter.interpretMessage(text)

            interpretationResult.fold(
                onSuccess = { result ->
                    // Сначала показываем ответ AI пользователю
                    if (result.responseText.isNotBlank()) {
                        addChatMessage(
                            TodoistChatMessage(
                                text = result.responseText,
                                isUser = false
                            )
                        )
                    }

                    // Если нужна дополнительная информация, не выполняем команду
                    if (result.needsMoreInfo) {
                        return@fold
                    }

                    // Выполняем команду, если она определена
                    val action = result.action
                    if (action != null) {
                        when (action) {
                            is TodoistAction.CreateTask -> {
                                executeCreateTaskFromChat(action)
                            }
                            is TodoistAction.ListTasks -> {
                                executeListTasksFromChat()
                            }
                            is TodoistAction.ListProjects -> {
                                executeListProjectsFromChat()
                            }
                            is TodoistAction.CompleteTask -> {
                                executeCompleteTaskFromChat(action.taskId)
                            }
                            is TodoistAction.DeleteTask -> {
                                executeDeleteTaskFromChat(action.taskId)
                            }
                            else -> {
                                // Другие типы команд не поддерживаются
                            }
                        }
                    }
                },
                onFailure = { error ->
                    addChatMessage(
                        TodoistChatMessage(
                            text = "Ошибка обработки сообщения: ${error.message}",
                            isUser = false
                        )
                    )
                }
            )
        }
    }

    private suspend fun executeCreateTaskFromChat(action: TodoistAction.CreateTask) {
        when (val result = mcpRepository.executeAction(action)) {
            is McpResult.Success -> {
                val task = result.data as? TodoistTask
                addChatMessage(
                    TodoistChatMessage(
                        "✓ Задача создана: ${action.content}\nID: ${task?.id ?: "неизвестен"}",
                        isUser = false
                    )
                )
                listTasks() // Обновляем список
            }
            is McpResult.Error -> {
                addChatMessage(
                    TodoistChatMessage("✗ Ошибка: ${result.message}", isUser = false)
                )
            }
            is McpResult.Loading -> {}
        }
    }

    private suspend fun executeListTasksFromChat() {
        when (val result = mcpRepository.executeAction(TodoistAction.ListTasks)) {
            is McpResult.Success -> {
                val tasks = result.data as? List<TodoistTask> ?: emptyList()
                _tasks.value = tasks

                val message = if (tasks.isEmpty()) {
                    "У вас нет задач"
                } else {
                    buildString {
                        append("Найдено задач: ${tasks.size}\n\n")
                        tasks.take(10).forEach { task ->
                            append("• ${task.content}\n  ID: ${task.id}\n")
                        }
                        if (tasks.size > 10) {
                            append("\n...и еще ${tasks.size - 10} задач")
                        }
                    }
                }
                addChatMessage(TodoistChatMessage(message, isUser = false))
            }
            is McpResult.Error -> {
                addChatMessage(
                    TodoistChatMessage("✗ Ошибка загрузки задач: ${result.message}", isUser = false)
                )
            }
            is McpResult.Loading -> {}
        }
    }

    private suspend fun executeListProjectsFromChat() {
        when (val result = mcpRepository.executeAction(TodoistAction.ListProjects)) {
            is McpResult.Success -> {
                val projects = result.data as? List<TodoistProject> ?: emptyList()
                _projects.value = projects

                val message = if (projects.isEmpty()) {
                    "У вас нет проектов"
                } else {
                    buildString {
                        append("Найдено проектов: ${projects.size}\n\n")
                        projects.forEach { project ->
                            append("• ${project.name}\n  ID: ${project.id}\n")
                        }
                    }
                }
                addChatMessage(TodoistChatMessage(message, isUser = false))
            }
            is McpResult.Error -> {
                addChatMessage(
                    TodoistChatMessage("✗ Ошибка загрузки проектов: ${result.message}", isUser = false)
                )
            }
            is McpResult.Loading -> {}
        }
    }

    private suspend fun executeCompleteTaskFromChat(taskId: String) {
        when (val result = mcpRepository.executeAction(TodoistAction.CompleteTask(taskId))) {
            is McpResult.Success -> {
                addChatMessage(
                    TodoistChatMessage("✓ Задача #$taskId отмечена как выполненная", isUser = false)
                )
                listTasks() // Обновляем список
            }
            is McpResult.Error -> {
                addChatMessage(
                    TodoistChatMessage("✗ Ошибка: ${result.message}", isUser = false)
                )
            }
            is McpResult.Loading -> {}
        }
    }

    private suspend fun executeDeleteTaskFromChat(taskId: String) {
        when (val result = mcpRepository.executeAction(TodoistAction.DeleteTask(taskId))) {
            is McpResult.Success -> {
                addChatMessage(
                    TodoistChatMessage("✓ Задача #$taskId удалена", isUser = false)
                )
                listTasks() // Обновляем список
            }
            is McpResult.Error -> {
                addChatMessage(
                    TodoistChatMessage("✗ Ошибка: ${result.message}", isUser = false)
                )
            }
            is McpResult.Loading -> {}
        }
    }

    private fun addChatMessage(message: TodoistChatMessage) {
        val currentMessages = _chatMessages.value ?: emptyList()
        _chatMessages.value = currentMessages + message
    }

    override fun onCleared() {
        super.onCleared()
        mcpRepository.dispose()
        aiInterpreter.clearHistory()
    }
}