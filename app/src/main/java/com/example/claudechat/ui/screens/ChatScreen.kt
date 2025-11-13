package com.example.claudechat.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.claudechat.ui.components.MessageBubble
import com.example.claudechat.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: (() -> Unit)? = null,
    isMultiAgentMode: Boolean = false
) {
    val messages by viewModel.messages.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState()

    var messageText by remember { mutableStateOf("") }
    var showTemperatureDialog by remember { mutableStateOf(false) }
    var showCompressionStats by remember { mutableStateOf(false) }
    val currentTemperature by viewModel.temperature.observeAsState(1.0)
    val compressionStats by viewModel.compressionStats.observeAsState(Triple(0, 0, 0))
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Устанавливаем режим при первой загрузке
    LaunchedEffect(isMultiAgentMode) {
        viewModel.setMultiAgentMode(isMultiAgentMode)
    }
    
    // Автоматическая прокрутка при появлении новых сообщений
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    // Показываем ошибку
    error?.let {
        LaunchedEffect(it) {
            // Можно добавить Snackbar для показа ошибок
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isMultiAgentMode) "Многоагентный совет" else "Claude Chat") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Назад",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showTemperatureDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки температуры",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Очистить чат",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                    MessageBubble(message = message)
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

            // Панель тестовых запросов (только в обычном режиме)
            if (!isMultiAgentMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Тестирование токенов:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            item {
                                AssistChip(
                                    onClick = {
                                        viewModel.sendMessage("Привет")
                                    },
                                    label = { Text("Короткий запрос") },
                                    enabled = !isLoading
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        viewModel.sendMessage(
                                            "Объясни подробно, что такое искусственный интеллект, " +
                                            "как он работает, какие существуют виды ИИ, где применяется, " +
                                            "какие есть преимущества и недостатки использования ИИ в современном мире."
                                        )
                                    },
                                    label = { Text("Длинный запрос") },
                                    enabled = !isLoading
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        val longPrompt = buildString {
                                            append("Расскажи максимально подробно про ")
                                            repeat(100) {
                                                append("историю развития компьютеров, ")
                                            }
                                            append("и их влияние на современное общество.")
                                        }
                                        viewModel.sendMessage(longPrompt)
                                    },
                                    label = { Text("Превышение лимита") },
                                    enabled = !isLoading
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = {
                                        // Запускаем серию сообщений для демонстрации компрессии
                                        coroutineScope.launch {
                                            val compressionTestMessages = listOf(
                                                "Привет! Меня зовут Алексей, и я мечтаю стать космонавтом. Расскажи, с чего начать?",
                                                "Какое образование нужно для того, чтобы стать космонавтом?",
                                                "Сколько времени занимает подготовка космонавтов?",
                                                "Расскажи про физические требования к космонавтам",
                                                "Какие психологические качества важны для работы в космосе?",
                                                "Что такое невесомость и как к ней готовятся?",
                                                "Расскажи про МКС - что это и как там живут космонавты?",
                                                "Какие эксперименты проводят на МКС?",
                                                "Как космонавты питаются в космосе?",
                                                "Что будет после того как я стану космонавтом - какие миссии меня могут ждать?"
                                            )

                                            compressionTestMessages.forEach { message ->
                                                viewModel.sendMessage(message)
                                                kotlinx.coroutines.delay(5000) // Задержка 5 секунд между сообщениями
                                            }
                                        }
                                    },
                                    label = { Text("🚀 Тест компрессии") },
                                    enabled = !isLoading
                                )
                            }
                        }
                    }
                }
            }

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
                        placeholder = { Text("Введите сообщение...") },
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
                                viewModel.sendMessage(messageText)
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

    // Диалоговое окно настройки температуры
    if (showTemperatureDialog) {
        TemperatureDialog(
            currentTemperature = currentTemperature,
            onDismiss = { showTemperatureDialog = false },
            onConfirm = { newTemp ->
                viewModel.setTemperature(newTemp)
                showTemperatureDialog = false
            }
        )
    }
}

@Composable
fun TemperatureDialog(
    currentTemperature: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var temperatureText by remember { mutableStateOf(currentTemperature.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка температуры") },
        text = {
            Column {
                Text(
                    text = "Введите значение температуры (0.0 - 1.0)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                TextField(
                    value = temperatureText,
                    onValueChange = { temperatureText = it },
                    label = { Text("Температура") },
                    placeholder = { Text("1.0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "По умолчанию: 1.0\nБолее низкие значения - более предсказуемые ответы",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val temp = temperatureText.toDoubleOrNull() ?: currentTemperature
                    onConfirm(temp)
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
