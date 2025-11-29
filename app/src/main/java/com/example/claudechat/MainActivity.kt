package com.example.claudechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.claudechat.repository.ChatRepository
import com.example.claudechat.ui.theme.ClaudeChatTheme
import com.example.claudechat.ui.screens.ChatScreen
import com.example.claudechat.ui.screens.GoalScreen
import com.example.claudechat.ui.screens.MainMenuScreen
import com.example.claudechat.ui.screens.MemoryScreen
import com.example.claudechat.ui.screens.ModelComparisonScreen
import com.example.claudechat.ui.screens.TodoistScreen
import com.example.claudechat.ui.screens.RagChatScreen
import com.example.claudechat.viewmodel.ChatViewModel
import com.example.claudechat.viewmodel.MemoryViewModel
import com.example.claudechat.viewmodel.TodoistViewModel
import com.example.claudechat.viewmodel.RagChatViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClaudeChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

/**
 * Навигация между экранами
 */
@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf(Screen.MAIN_MENU) }
    var initialMessage by remember { mutableStateOf<String?>(null) }
    var initialMessageIsSummary by remember { mutableStateOf(false) }

    val context = LocalContext.current

    when (currentScreen) {
        Screen.MAIN_MENU -> {
            MainMenuScreen(
                onChatSelected = { currentScreen = Screen.CHAT },
                onGoalsSelected = { currentScreen = Screen.GOALS },
                onMultiAgentSelected = { currentScreen = Screen.MULTI_AGENT },
                onModelComparisonSelected = { currentScreen = Screen.MODEL_COMPARISON },
                onMemorySelected = { currentScreen = Screen.MEMORY },
                onTodoistSelected = { currentScreen = Screen.TODOIST },
                onRagChatSelected = { currentScreen = Screen.RAG_CHAT }
            )
        }
        Screen.CHAT -> {
            val chatViewModel: ChatViewModel = viewModel(
                factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as android.app.Application
                )
            )
            ChatScreen(
                viewModel = chatViewModel,
                onBack = { currentScreen = Screen.MAIN_MENU },
                initialMessage = initialMessage,
                initialMessageIsSummary = initialMessageIsSummary
            )
        }
        Screen.GOALS -> {
            val chatRepository = remember { ChatRepository(context.applicationContext) }
            GoalScreen(
                chatRepository = chatRepository,
                onBack = { currentScreen = Screen.MAIN_MENU }
            )
        }
        Screen.MULTI_AGENT -> {
            val chatViewModel: ChatViewModel = viewModel(
                factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as android.app.Application
                )
            )
            ChatScreen(
                viewModel = chatViewModel,
                onBack = { currentScreen = Screen.MAIN_MENU },
                isMultiAgentMode = true
            )
        }
        Screen.MODEL_COMPARISON -> {
            ModelComparisonScreen(
                onBackClick = { currentScreen = Screen.MAIN_MENU }
            )
        }
        Screen.MEMORY -> {
            val memoryViewModel: MemoryViewModel = viewModel()
            MemoryScreen(
                viewModel = memoryViewModel,
                onBack = { currentScreen = Screen.MAIN_MENU },
                onMessageClick = { content, isSummary ->
                    initialMessage = content
                    initialMessageIsSummary = isSummary
                    currentScreen = Screen.CHAT
                }
            )
        }
        Screen.TODOIST -> {
            val todoistViewModel: TodoistViewModel = viewModel(
                factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as android.app.Application
                )
            )
            TodoistScreen(
                viewModel = todoistViewModel,
                onBack = { currentScreen = Screen.MAIN_MENU }
            )
        }
        Screen.RAG_CHAT -> {
            val ragChatViewModel: RagChatViewModel = viewModel(
                factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as android.app.Application
                )
            )
            RagChatScreen(
                viewModel = ragChatViewModel,
                onBack = { currentScreen = Screen.MAIN_MENU }
            )
        }
    }
}

/**
 * Экраны приложения
 */
enum class Screen {
    MAIN_MENU,
    CHAT,
    GOALS,
    MULTI_AGENT,
    MODEL_COMPARISON,
    MEMORY,
    TODOIST,
    RAG_CHAT
}
