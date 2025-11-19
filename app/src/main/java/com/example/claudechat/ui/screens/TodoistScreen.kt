package com.example.claudechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.claudechat.data.mcp.models.McpConnectionState
import com.example.claudechat.ui.components.McpStatusIndicator
import com.example.claudechat.ui.components.TodoistCard
import com.example.claudechat.viewmodel.TodoistChatMessage
import com.example.claudechat.viewmodel.TodoistViewModel
import kotlinx.coroutines.launch

/**
 * Экран для работы с Todoist через MCP
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoistScreen(
    viewModel: TodoistViewModel,
    onBack: () -> Unit
) {
    val mcpConnectionState by viewModel.mcpConnectionState.collectAsState()
    val tasks by viewModel.tasks.observeAsState(emptyList())
    val projects by viewModel.projects.observeAsState(emptyList())
    val chatMessages by viewModel.chatMessages.observeAsState(emptyList())
    val notifications by viewModel.notifications.observeAsState(emptyList())
    val notificationStatus by viewModel.notificationStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.observeAsState()
    val successMessage by viewModel.successMessage.observeAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }

    // Загружаем задачи и проекты при первом открытии
    LaunchedEffect(Unit) {
        viewModel.listTasks()
        viewModel.listProjects()
    }

    // Показываем Snackbar для ошибок и успехов
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Todoist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    // Компактный индикатор подключения (только иконка)
                    IconButton(onClick = { viewModel.reconnect() }) {
                        Icon(
                            imageVector = when (mcpConnectionState) {
                                is McpConnectionState.Connected -> Icons.Default.CheckCircle
                                is McpConnectionState.Connecting -> Icons.Default.Info
                                is McpConnectionState.Disconnected -> Icons.Default.Warning
                                is McpConnectionState.Error -> Icons.Default.Warning
                            },
                            contentDescription = when (mcpConnectionState) {
                                is McpConnectionState.Connected -> "Todoist подключен"
                                is McpConnectionState.Connecting -> "Подключение..."
                                is McpConnectionState.Disconnected -> "Todoist отключен"
                                is McpConnectionState.Error -> "Ошибка подключения"
                            },
                            tint = when (mcpConnectionState) {
                                is McpConnectionState.Connected -> Color.White
                                is McpConnectionState.Connecting -> Color.White
                                is McpConnectionState.Disconnected -> Color.White.copy(alpha = 0.7f)
                                is McpConnectionState.Error -> Color.White.copy(alpha = 0.7f)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showCreateTaskDialog = true }
                ) {
                    Icon(Icons.Default.Add, "Создать задачу")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Полный индикатор подключения с текстом
            McpStatusIndicator(
                connectionState = mcpConnectionState,
                onReconnect = { viewModel.reconnect() }
            )

            // Вкладки
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Задачи (${tasks.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Проекты (${projects.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Чат") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = {
                        selectedTab = 3
                        viewModel.refreshNotificationStatus()
                    },
                    text = { Text("Уведомления") }
                )
            }

            // Контент вкладок
            when (selectedTab) {
                0 -> TasksTab(
                    viewModel = viewModel,
                    tasks = tasks,
                    isLoading = isLoading,
                    onRefresh = { viewModel.listTasks() }
                )
                1 -> ProjectsTab(
                    projects = projects,
                    isLoading = isLoading,
                    onRefresh = { viewModel.listProjects() }
                )
                2 -> ChatTab(
                    viewModel = viewModel,
                    chatMessages = chatMessages
                )
                3 -> NotificationsTab(
                    viewModel = viewModel,
                    notifications = notifications,
                    notificationStatus = notificationStatus
                )
            }
        }
    }

    // Диалог создания задачи
    if (showCreateTaskDialog) {
        CreateTaskDialog(
            projects = projects,
            onDismiss = { showCreateTaskDialog = false },
            onCreateTask = { content, description, projectId, dueString, priority ->
                viewModel.createTask(
                    content = content,
                    description = description,
                    projectId = projectId,
                    dueString = dueString,
                    priority = priority
                )
                showCreateTaskDialog = false
            }
        )
    }
}

/**
 * Вкладка со списком задач
 */
@Composable
private fun TasksTab(
    viewModel: TodoistViewModel,
    tasks: List<com.example.claudechat.data.mcp.models.TodoistTask>,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && tasks.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (tasks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Нет задач",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Создайте первую задачу",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Обновить")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks) { task ->
                    TodoistCard(
                        task = task,
                        onComplete = { viewModel.completeTask(task.id) },
                        onDelete = { viewModel.deleteTask(task.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

/**
 * Вкладка со списком проектов
 */
@Composable
private fun ProjectsTab(
    projects: List<com.example.claudechat.data.mcp.models.TodoistProject>,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && projects.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (projects.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Create,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Нет проектов",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Обновить")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects) { project ->
                    ProjectCard(project = project)
                }
            }
        }
    }
}

/**
 * Карточка проекта
 */
@Composable
private fun ProjectCard(
    project: com.example.claudechat.data.mcp.models.TodoistProject
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Create,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (project.color != null) {
                    Text(
                        text = project.color,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Диалог создания задачи
 */
@Composable
private fun CreateTaskDialog(
    projects: List<com.example.claudechat.data.mcp.models.TodoistProject>,
    onDismiss: () -> Unit,
    onCreateTask: (String, String?, String?, String?, Int?) -> Unit
) {
    var taskContent by remember { mutableStateOf("") }
    var taskDescription by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var dueString by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать задачу") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = taskContent,
                    onValueChange = { taskContent = it },
                    label = { Text("Название задачи *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = taskDescription,
                    onValueChange = { taskDescription = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                OutlinedTextField(
                    value = dueString,
                    onValueChange = { dueString = it },
                    label = { Text("Срок (например: tomorrow, next monday)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    "Приоритет: ${priority}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = priority.toFloat(),
                    onValueChange = { priority = it.toInt() },
                    valueRange = 1f..4f,
                    steps = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (taskContent.isNotBlank()) {
                        onCreateTask(
                            taskContent,
                            taskDescription.ifBlank { null },
                            selectedProjectId,
                            dueString.ifBlank { null },
                            if (priority > 1) priority else null
                        )
                    }
                },
                enabled = taskContent.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/**
 * Вкладка чата с Todoist
 */
@Composable
private fun ChatTab(
    viewModel: TodoistViewModel,
    chatMessages: List<TodoistChatMessage>
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Автопрокрутка к последнему сообщению
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(chatMessages.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Список сообщений
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatMessages) { message ->
                ChatMessageBubble(message)
            }
        }

        // Поле ввода
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Введите команду...") },
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendChatMessage(messageText)
                        messageText = ""
                    }
                },
                enabled = messageText.isNotBlank()
            ) {
                Icon(Icons.Default.Send, "Отправить")
            }
        }
    }
}

/**
 * Пузырь сообщения в чате
 */
@Composable
private fun ChatMessageBubble(message: TodoistChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}

/**
 * Вкладка уведомлений
 */
@Composable
private fun NotificationsTab(
    viewModel: TodoistViewModel,
    notifications: List<com.example.claudechat.viewmodel.TaskNotification>,
    notificationStatus: com.example.claudechat.data.mcp.models.NotificationStatus?
) {
    var intervalInput by remember { mutableStateOf("60") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Панель управления уведомлениями
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Управление уведомлениями",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Статус
                notificationStatus?.let { status ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (status.enabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (status.enabled) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (status.enabled) {
                                "Уведомления включены (интервал: ${status.intervalSeconds}с)"
                            } else {
                                "Уведомления отключены"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Divider()

                // Настройка интервала
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = intervalInput,
                        onValueChange = { intervalInput = it },
                        label = { Text("Интервал (сек)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val interval = intervalInput.toIntOrNull() ?: 60
                            viewModel.setNotificationInterval(interval)
                        }
                    ) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Установить")
                    }
                }

                // Кнопки управления
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val interval = intervalInput.toIntOrNull() ?: 60
                            viewModel.enableNotifications(interval)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(Icons.Default.Notifications, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Включить")
                    }

                    Button(
                        onClick = { viewModel.disableNotifications() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5722)
                        )
                    ) {
                        Icon(Icons.Default.Close, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Выключить")
                    }
                }

                // Кнопка обновления статуса и очистки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.refreshNotificationStatus() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Обновить статус")
                    }

                    OutlinedButton(
                        onClick = { viewModel.clearNotifications() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Очистить список")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Список уведомлений
        Text(
            text = "История уведомлений (${notifications.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Нет уведомлений",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Включите уведомления для получения",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications.reversed()) { notification ->
                    NotificationCard(notification)
                }
            }
        }
    }
}

/**
 * Карточка уведомления
 */
@Composable
private fun NotificationCard(
    notification: com.example.claudechat.viewmodel.TaskNotification
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Задачи: ${notification.taskCount}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(notification.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}