package com.example.claudechat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.claudechat.model.GoalType
import com.example.claudechat.repository.ChatRepository
import com.example.claudechat.ui.components.AlgorithmView
import com.example.claudechat.ui.components.GoalChatBubble
import com.example.claudechat.viewmodel.GoalUiState
import com.example.claudechat.viewmodel.GoalViewModel
import kotlinx.coroutines.launch

/**
 * Главный экран для работы с целями
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalScreen(
    chatRepository: ChatRepository,
    onBack: () -> Unit
) {
    val viewModel: GoalViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return GoalViewModel(chatRepository) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Достижение целей") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (uiState is GoalUiState.AlgorithmGenerated) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Начать заново")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is GoalUiState.Initial -> {
                    GoalTypeSelection(
                        onGoalSelected = { goalType, goalTitle ->
                            viewModel.startGoalConversation(goalType, goalTitle)
                        }
                    )
                }
                is GoalUiState.Loading -> {
                    LoadingScreen(message = "Начинаю диалог...")
                }
                is GoalUiState.Chatting -> {
                    ChatScreen(
                        state = state,
                        onSendMessage = { viewModel.sendMessage(it) },
                        onGenerateAlgorithm = { viewModel.forceGenerateAlgorithm() }
                    )
                }
                is GoalUiState.GeneratingAlgorithm -> {
                    LoadingScreen(message = "Генерирую алгоритм достижения цели...")
                }
                is GoalUiState.AlgorithmGenerated -> {
                    AlgorithmView(
                        algorithm = state.algorithm,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is GoalUiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.reset() }
                    )
                }
            }
        }
    }
}

/**
 * Экран выбора типа цели
 */
@Composable
private fun GoalTypeSelection(
    onGoalSelected: (GoalType, String) -> Unit
) {
    var selectedGoalType by remember { mutableStateOf<GoalType?>(null) }
    var goalTitle by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Какую цель вы хотите достичь?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Выберите тип цели, и AI-ассистент задаст вам уточняющие вопросы в чате, чтобы создать персонализированный план.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Кнопки выбора типа цели
        val goalTypes = listOf(
            GoalType.GAME_DEVELOPMENT to "🎮 Создать игру",
            GoalType.MOBILE_APP to "📱 Разработать мобильное приложение",
            GoalType.WEB_APP to "🌐 Создать веб-приложение",
            GoalType.LEARNING to "📚 Изучить что-то новое",
            GoalType.BUSINESS to "💼 Запустить бизнес",
            GoalType.CREATIVE_PROJECT to "🎨 Реализовать творческий проект",
            GoalType.OTHER to "✨ Другое"
        )

        goalTypes.forEach { (type, label) ->
            GoalTypeButton(
                label = label,
                isSelected = selectedGoalType == type,
                onClick = { selectedGoalType = type }
            )
        }

        // Поле ввода названия цели
        AnimatedVisibility(visible = selectedGoalType != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Опишите вашу цель в нескольких словах:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = goalTitle,
                    onValueChange = { goalTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Например: Создать мобильную RPG игру") },
                    minLines = 2,
                    maxLines = 3
                )

                Button(
                    onClick = {
                        selectedGoalType?.let { type ->
                            onGoalSelected(type, goalTitle)
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Начать диалог")
                }
            }
        }
    }
}

/**
 * Кнопка выбора типа цели
 */
@Composable
private fun GoalTypeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Экран чата с AI
 */
@Composable
private fun ChatScreen(
    state: GoalUiState.Chatting,
    onSendMessage: (String) -> Unit,
    onGenerateAlgorithm: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Автопрокрутка при появлении новых сообщений
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(state.messages.size - 1)
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
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.messages) { message ->
                GoalChatBubble(message = message)
            }

            // Индикатор загрузки
            if (state.isLoading) {
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

        // Кнопка принудительной генерации алгоритма
//        if (state.messages.size >= 8 && !state.isLoading) {
//            TextButton(
//                onClick = onGenerateAlgorithm,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(horizontal = 16.dp, vertical = 8.dp)
//            ) {
//                Text("✅ Достаточно информации? Создать план")
//            }
//        }

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
                    placeholder = { Text("Введите ответ...") },
                    enabled = !state.isLoading,
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
                            onSendMessage(messageText)
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

/**
 * Экран загрузки
 */
@Composable
private fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Экран ошибки
 */
@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "❌ Ошибка",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text("Начать заново")
            }
        }
    }
}