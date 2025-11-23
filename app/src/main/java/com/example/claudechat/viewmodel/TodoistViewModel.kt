package com.example.claudechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.claudechat.data.mcp.McpRepository
import com.example.claudechat.data.mcp.models.*
import com.example.claudechat.database.ChatDatabase
import com.example.claudechat.database.NotificationEntity
import com.example.claudechat.services.LearningResourcesService
import com.example.claudechat.services.PdfGeneratorService
import com.example.claudechat.services.TaskPlanningService
import com.example.claudechat.utils.TodoistAiInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сообщение в чате Todoist
 */
data class TodoistChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Уведомление о задачах
 */
data class TaskNotification(
    val id: Long = 0,
    val message: String,
    val taskCount: Int,
    val timestamp: Long,
    val pdfPath: String? = null,
    val isGeneratingPlan: Boolean = false
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

    // Сервисы для генерации планов и PDF
    private val taskPlanningService = TaskPlanningService()
    private val pdfGeneratorService = PdfGeneratorService(application.applicationContext)
    private val learningResourcesService = LearningResourcesService()

    // База данных для сохранения уведомлений
    private val database = ChatDatabase.getDatabase(application.applicationContext)
    private val notificationDao = database.notificationDao()

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

    // Список уведомлений
    private val _notifications = MutableLiveData<List<TaskNotification>>(emptyList())
    val notifications: LiveData<List<TaskNotification>> = _notifications

    // Статус уведомлений
    private val _notificationStatus = MutableStateFlow<NotificationStatus?>(null)
    val notificationStatus: StateFlow<NotificationStatus?> = _notificationStatus.asStateFlow()

    init {
        // Загружаем уведомления из БД при старте
        loadNotificationsFromDatabase()

        // Настраиваем callback для получения уведомлений
        mcpRepository.setNotificationCallback { notificationData ->
            // Сохраняем уведомление в БД и запускаем генерацию плана
            viewModelScope.launch {
                // Создаем уведомление с флагом генерации плана
                val notification = TaskNotification(
                    message = notificationData.message,
                    taskCount = notificationData.taskCount,
                    timestamp = notificationData.timestamp,
                    isGeneratingPlan = true
                )

                // Сначала сохраняем в БД и получаем ID
                val notificationId = saveNotificationToDatabase(notification)

                // Добавляем уведомление в список с правильным ID
                val notificationWithId = notification.copy(id = notificationId)
                val currentNotifications = _notifications.value ?: emptyList()
                _notifications.postValue(currentNotifications + notificationWithId)

                // Запускаем генерацию плана
                generatePlanForNotification(notificationId, notificationData.message)
            }
        }

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

    // ==================== Notification Methods ====================

    /**
     * Включает уведомления
     */
    fun enableNotifications(intervalSeconds: Int = 60, maxTasks: Int = 20) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = mcpRepository.enableNotifications(intervalSeconds, maxTasks)
            if (success) {
                _successMessage.value = "Уведомления включены (интервал: ${intervalSeconds}с, макс. задач: $maxTasks)"
                // Обновляем статус
                refreshNotificationStatus()
            } else {
                _errorMessage.value = "Не удалось включить уведомления"
            }
            _isLoading.value = false
        }
    }

    /**
     * Отключает уведомления
     */
    fun disableNotifications() {
        viewModelScope.launch {
            _isLoading.value = true
            val success = mcpRepository.disableNotifications()
            if (success) {
                _successMessage.value = "Уведомления отключены"
                // Обновляем статус
                refreshNotificationStatus()
            } else {
                _errorMessage.value = "Не удалось отключить уведомления"
            }
            _isLoading.value = false
        }
    }

    /**
     * Изменяет интервал уведомлений
     */
    fun setNotificationInterval(intervalSeconds: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = mcpRepository.setNotificationInterval(intervalSeconds)
            if (success) {
                _successMessage.value = "Интервал изменен: ${intervalSeconds}с"
                // Обновляем статус
                refreshNotificationStatus()
            } else {
                _errorMessage.value = "Не удалось изменить интервал"
            }
            _isLoading.value = false
        }
    }

    /**
     * Изменяет максимальное количество задач в уведомлении
     */
    fun setMaxTasks(maxTasks: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = mcpRepository.setMaxTasks(maxTasks)
            if (success) {
                _successMessage.value = "Макс. задач изменено: $maxTasks"
                // Обновляем статус
                refreshNotificationStatus()
            } else {
                _errorMessage.value = "Не удалось изменить макс. задач"
            }
            _isLoading.value = false
        }
    }

    /**
     * Обновляет статус уведомлений
     */
    fun refreshNotificationStatus() {
        viewModelScope.launch {
            val status = mcpRepository.getNotificationStatus()
            _notificationStatus.value = status
        }
    }

    /**
     * Очищает список уведомлений
     */
    fun clearNotifications() {
        _notifications.value = emptyList()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                notificationDao.deleteAll()
            }
        }
    }

    /**
     * Загружает уведомления из базы данных
     */
    private fun loadNotificationsFromDatabase() {
        viewModelScope.launch {
            val entities: List<NotificationEntity> = withContext(Dispatchers.IO) {
                notificationDao.getRecentNotifications(100) // Загружаем последние 100
            }

            val notifications = entities.map { entity: NotificationEntity ->
                TaskNotification(
                    id = entity.id,
                    message = entity.message,
                    taskCount = entity.taskCount,
                    timestamp = entity.timestamp,
                    pdfPath = entity.pdfPath,
                    isGeneratingPlan = entity.isGeneratingPlan
                )
            }

            _notifications.postValue(notifications)
        }
    }

    /**
     * Сохраняет уведомление в базу данных
     * @return ID созданного уведомления
     */
    private suspend fun saveNotificationToDatabase(notification: TaskNotification): Long {
        return withContext(Dispatchers.IO) {
            val entity = NotificationEntity(
                message = notification.message,
                taskCount = notification.taskCount,
                timestamp = notification.timestamp,
                pdfPath = notification.pdfPath,
                isGeneratingPlan = notification.isGeneratingPlan
            )
            notificationDao.insert(entity)
        }
    }

    /**
     * Генерирует план реализации задач для уведомления
     */
    private suspend fun generatePlanForNotification(notificationId: Long, message: String) {
        withContext(Dispatchers.IO) {
            try {
                // Шаг 1: Генерируем план через Claude API
                val planResult = taskPlanningService.generateTaskPlan(message)

                planResult.fold(
                    onSuccess = { planText ->
                        // Шаг 2: Получаем рекомендации по обучению на основе плана
                        val recommendationsResult = learningResourcesService.getLearningRecommendations(planText)

                        recommendationsResult.fold(
                            onSuccess = { recommendations ->
                                // Шаг 3: Объединяем план с рекомендациями
                                val fullText = planText + recommendations

                                // Шаг 4: Генерируем PDF из объединенного текста
                                val fileName = "plan_${System.currentTimeMillis()}"
                                val pdfResult = pdfGeneratorService.generatePdf(fullText, fileName)

                                pdfResult.fold(
                                    onSuccess = { pdfPath ->
                                        // Шаг 5: Обновляем уведомление в БД
                                        val entity = notificationDao.getById(notificationId)
                                        if (entity != null) {
                                            val updatedEntity = entity.copy(
                                                pdfPath = pdfPath,
                                                isGeneratingPlan = false
                                            )
                                            notificationDao.update(updatedEntity)

                                            // Обновляем UI
                                            updateNotificationInList(notificationId, pdfPath, false)
                                        }
                                    },
                                    onFailure = { error ->
                                        // Ошибка генерации PDF
                                        markNotificationAsFailed(notificationId)
                                        _errorMessage.postValue("Ошибка генерации PDF: ${error.message}")
                                    }
                                )
                            },
                            onFailure = { error ->
                                // Если не удалось получить рекомендации, генерируем PDF только с планом
                                // (не прерываем процесс)
                                _errorMessage.postValue("Предупреждение: не удалось получить рекомендации (${error.message}), генерируем PDF только с планом")

                                val fileName = "plan_${System.currentTimeMillis()}"
                                val pdfResult = pdfGeneratorService.generatePdf(planText, fileName)

                                pdfResult.fold(
                                    onSuccess = { pdfPath ->
                                        val entity = notificationDao.getById(notificationId)
                                        if (entity != null) {
                                            val updatedEntity = entity.copy(
                                                pdfPath = pdfPath,
                                                isGeneratingPlan = false
                                            )
                                            notificationDao.update(updatedEntity)
                                            updateNotificationInList(notificationId, pdfPath, false)
                                        }
                                    },
                                    onFailure = { pdfError ->
                                        markNotificationAsFailed(notificationId)
                                        _errorMessage.postValue("Ошибка генерации PDF: ${pdfError.message}")
                                    }
                                )
                            }
                        )
                    },
                    onFailure = { error ->
                        // Ошибка генерации плана
                        markNotificationAsFailed(notificationId)
                        _errorMessage.postValue("Ошибка генерации плана: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                markNotificationAsFailed(notificationId)
                _errorMessage.postValue("Ошибка: ${e.message}")
            }
        }
    }

    /**
     * Обновляет уведомление в списке (для обновления UI)
     */
    private fun updateNotificationInList(notificationId: Long, pdfPath: String, isGeneratingPlan: Boolean) {
        val currentNotifications = _notifications.value ?: emptyList()
        val updatedNotifications = currentNotifications.map { notification ->
            if (notification.id == notificationId) {
                notification.copy(pdfPath = pdfPath, isGeneratingPlan = isGeneratingPlan)
            } else {
                notification
            }
        }
        _notifications.postValue(updatedNotifications)
    }

    /**
     * Отмечает уведомление как неудачное (сбрасывает флаг генерации)
     */
    private suspend fun markNotificationAsFailed(notificationId: Long) {
        withContext(Dispatchers.IO) {
            val entity = notificationDao.getById(notificationId)
            if (entity != null) {
                val updatedEntity = entity.copy(isGeneratingPlan = false)
                notificationDao.update(updatedEntity)
                updateNotificationInList(notificationId, "", false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mcpRepository.dispose()
        aiInterpreter.clearHistory()
    }
}