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
    val chatMessages by viewModel.chatMessages.observeAsState(emptyList())
    val notifications by viewModel.notifications.observeAsState(emptyList())
    val notificationStatus by viewModel.notificationStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.observeAsState()
    val successMessage by viewModel.successMessage.observeAsState()

    var selectedTab by remember { mutableStateOf(0) }

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
                    text = { Text("Чат") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.refreshNotificationStatus()
                    },
                    text = { Text("Уведомления") }
                )
            }

            // Контент вкладок
            when (selectedTab) {
                0 -> ChatTab(
                    viewModel = viewModel,
                    chatMessages = chatMessages
                )
                1 -> NotificationsTab(
                    viewModel = viewModel,
                    notifications = notifications,
                    notificationStatus = notificationStatus
                )
            }
        }
    }
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
    var showSettingsDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Заголовок с кнопками
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Уведомления (${notifications.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Краткий статус
                    notificationStatus?.let { status ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (status.enabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (status.enabled) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (status.enabled) {
                                    "Включены (${status.intervalSeconds}с)"
                                } else {
                                    "Отключены"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Кнопка очистки
                    IconButton(
                        onClick = { viewModel.clearNotifications() },
                        enabled = notifications.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, "Очистить список")
                    }

                    // Кнопка настроек
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, "Настройки")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Список уведомлений
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
                            "Нажмите на шестеренку для настройки",
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

    // Диалог настроек
    if (showSettingsDialog) {
        NotificationSettingsDialog(
            viewModel = viewModel,
            notificationStatus = notificationStatus,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

/**
 * Диалог настроек уведомлений
 */
@Composable
private fun NotificationSettingsDialog(
    viewModel: TodoistViewModel,
    notificationStatus: com.example.claudechat.data.mcp.models.NotificationStatus?,
    onDismiss: () -> Unit
) {
    var intervalInput by remember {
        mutableStateOf(notificationStatus?.intervalSeconds?.toString() ?: "60")
    }
    var maxTasksInput by remember {
        mutableStateOf(notificationStatus?.maxTasks?.toString() ?: "20")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Управление уведомлениями")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Текущий статус
                notificationStatus?.let { status ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (status.enabled) {
                                Color(0xFF4CAF50).copy(alpha = 0.1f)
                            } else {
                                Color(0xFFFF9800).copy(alpha = 0.1f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (status.enabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (status.enabled) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (status.enabled) "Уведомления включены" else "Уведомления отключены",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (status.enabled) {
                                    Text(
                                        text = "Интервал: ${status.intervalSeconds}с, Макс. задач: ${status.maxTasks}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                Divider()

                // Настройка параметров
                Text(
                    text = "Настройки уведомлений",
                    style = MaterialTheme.typography.labelLarge
                )

                OutlinedTextField(
                    value = intervalInput,
                    onValueChange = { intervalInput = it },
                    label = { Text("Интервал (секунды)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Минимум: 1 секунда") }
                )

                OutlinedTextField(
                    value = maxTasksInput,
                    onValueChange = { maxTasksInput = it },
                    label = { Text("Макс. задач") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Количество задач в уведомлении (мин: 1)") }
                )

                // Кнопки управления
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val interval = intervalInput.toIntOrNull() ?: 60
                            val maxTasks = maxTasksInput.toIntOrNull() ?: 20
                            viewModel.enableNotifications(interval, maxTasks)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = notificationStatus?.enabled != true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(Icons.Default.Notifications, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Включить уведомления")
                    }

                    OutlinedButton(
                        onClick = {
                            val interval = intervalInput.toIntOrNull() ?: 60
                            viewModel.setNotificationInterval(interval)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = notificationStatus?.enabled == true
                    ) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Изменить интервал")
                    }

                    OutlinedButton(
                        onClick = {
                            val maxTasks = maxTasksInput.toIntOrNull() ?: 20
                            viewModel.setMaxTasks(maxTasks)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = notificationStatus?.enabled == true
                    ) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Изменить макс. задач")
                    }

                    OutlinedButton(
                        onClick = { viewModel.disableNotifications() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = notificationStatus?.enabled == true
                    ) {
                        Icon(Icons.Default.Close, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выключить уведомления")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.refreshNotificationStatus()
                onDismiss()
            }) {
                Text("Закрыть")
            }
        }
    )
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