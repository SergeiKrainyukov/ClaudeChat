package com.example.claudechat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.claudechat.ui.components.RagMessageBubble
import com.example.claudechat.viewmodel.RagChatViewModel
import com.example.claudechat.api.Source
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagChatScreen(
    viewModel: RagChatViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.messages.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()
    val documents by viewModel.documents.observeAsState(emptyList())
    val stats by viewModel.stats.observeAsState()
    val serverHealth by viewModel.serverHealth.observeAsState()

    var messageText by remember { mutableStateOf("") }
    var showDocumentsSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSourcesScreen by remember { mutableStateOf(false) }
    var topK by remember { mutableStateOf(3) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Launcher для выбора файла
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = "document_${System.currentTimeMillis()}.md"
            viewModel.uploadDocument(it, fileName)
        }
    }

    // Инициализация данных при первом открытии экрана
    LaunchedEffect(Unit) {
        viewModel.checkServerHealth()
        viewModel.loadDocuments()
        viewModel.loadStats()
    }

    // Автоматическая прокрутка при новых сообщениях
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Snackbar для ошибок
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RAG Чат с документами") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    // Кнопка настроек
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // Кнопка документов
                    IconButton(onClick = { showDocumentsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Документы",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // Кнопка очистки
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Очистить чат",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Индикатор статуса сервера
            ServerStatusBar(
                serverHealth = serverHealth,
                stats = stats,
                onRefresh = {
                    viewModel.checkServerHealth()
                    viewModel.loadStats()
                }
            )

            // Список сообщений
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    RagMessageBubble(
                        message = message,
                        onShowSources = { sources ->
                            viewModel.setCurrentSources(sources)
                            showSourcesScreen = true
                        }
                    )
                }

                // Индикатор загрузки
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            // Быстрые действия
            QuickActionsBar(
                onUploadDocument = { filePickerLauncher.launch("*/*") },
                onViewDocuments = { showDocumentsSheet = true },
                enabled = !isLoading
            )

            // Поле ввода
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp, max = 150.dp),
                        placeholder = { Text("Задайте вопрос по документам...") },
                        enabled = !isLoading,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = MaterialTheme.shapes.large
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendQuestion(messageText, topK)
                                messageText = ""
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Отправить"
                        )
                    }
                }
            }
        }
    }

    // Диалог настроек
    if (showSettingsDialog) {
        SettingsDialog(
            currentTopK = topK,
            onDismiss = { showSettingsDialog = false },
            onConfirm = { newTopK ->
                topK = newTopK
                showSettingsDialog = false
            }
        )
    }

    // Диалог с документами
    if (showDocumentsSheet) {
        DocumentsDialog(
            documents = documents,
            onDismiss = { showDocumentsSheet = false },
            onDeleteDocument = { documentId ->
                viewModel.deleteDocument(documentId)
            },
            onRefresh = {
                viewModel.loadDocuments()
            }
        )
    }

    // Экран источников
    if (showSourcesScreen) {
        val currentSources by viewModel.currentSources.observeAsState(emptyList())
        SourcesScreen(
            sources = currentSources,
            onBack = { showSourcesScreen = false }
        )
    }
}

/**
 * Панель статуса сервера
 */
@Composable
private fun ServerStatusBar(
    serverHealth: com.example.claudechat.api.HealthResponse?,
    stats: com.example.claudechat.api.DocumentStatsResponse?,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when {
            serverHealth?.status == "healthy" -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (serverHealth?.status == "healthy") "Сервер подключен" else "Сервер недоступен",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (stats != null) {
                    Text(
                        text = "Документов: ${stats.totalDocuments} | Фрагментов: ${stats.totalChunks}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Обновить"
                )
            }
        }
    }
}

/**
 * Панель быстрых действий
 */
@Composable
private fun QuickActionsBar(
    onUploadDocument: () -> Unit,
    onViewDocuments: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onUploadDocument,
                label = { Text("Загрузить документ") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                enabled = enabled
            )
            AssistChip(
                onClick = onViewDocuments,
                label = { Text("Просмотр документов") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null
                    )
                },
                enabled = enabled
            )
        }
    }
}

/**
 * Диалог настроек
 */
@Composable
private fun SettingsDialog(
    currentTopK: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var topKText by remember { mutableStateOf(currentTopK.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки RAG") },
        text = {
            Column {
                Text(
                    text = "Количество источников (Top-K)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextField(
                    value = topKText,
                    onValueChange = { topKText = it },
                    label = { Text("Top-K") },
                    placeholder = { Text("3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Количество наиболее релевантных фрагментов документов для поиска (1-10)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val topK = topKText.toIntOrNull()?.coerceIn(1, 10) ?: currentTopK
                    onConfirm(topK)
                }
            ) {
                Text("OK")
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
 * Диалог с документами
 */
@Composable
private fun DocumentsDialog(
    documents: List<com.example.claudechat.api.DocumentInfo>,
    onDismiss: () -> Unit,
    onDeleteDocument: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Документы (${documents.size})",
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Обновить"
                    )
                }
            }
        },
        text = {
            if (documents.isEmpty()) {
                Text(
                    text = "Нет загруженных документов",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    documents.forEach { document ->
                        DocumentCard(
                            document = document,
                            onDelete = { onDeleteDocument(document.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

/**
 * Карточка документа
 */
@Composable
private fun DocumentCard(
    document: com.example.claudechat.api.DocumentInfo,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Фрагментов: ${document.chunksCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Размер: ${document.fileSize} байт",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
