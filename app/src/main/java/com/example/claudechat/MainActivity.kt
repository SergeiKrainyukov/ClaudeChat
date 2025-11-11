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
import com.example.claudechat.repository.ChatRepository
import com.example.claudechat.ui.theme.ClaudeChatTheme
import com.example.claudechat.ui.screens.ChatScreen
import com.example.claudechat.ui.screens.GoalScreen
import com.example.claudechat.ui.screens.MainMenuScreen
import com.example.claudechat.ui.screens.ModelComparisonScreen
import com.example.claudechat.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private val chatRepository = ChatRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClaudeChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        viewModel = viewModel,
                        chatRepository = chatRepository
                    )
                }
            }
        }
    }
}

/**
 * Навигация между экранами
 */
@Composable
fun AppNavigation(
    viewModel: ChatViewModel,
    chatRepository: ChatRepository
) {
    var currentScreen by remember { mutableStateOf(Screen.MAIN_MENU) }

    when (currentScreen) {
        Screen.MAIN_MENU -> {
            MainMenuScreen(
                onChatSelected = { currentScreen = Screen.CHAT },
                onGoalsSelected = { currentScreen = Screen.GOALS },
                onMultiAgentSelected = { currentScreen = Screen.MULTI_AGENT },
                onModelComparisonSelected = { currentScreen = Screen.MODEL_COMPARISON }
            )
        }
        Screen.CHAT -> {
            ChatScreen(
                viewModel = viewModel,
                onBack = { currentScreen = Screen.MAIN_MENU }
            )
        }
        Screen.GOALS -> {
            GoalScreen(
                chatRepository = chatRepository,
                onBack = { currentScreen = Screen.MAIN_MENU }
            )
        }
        Screen.MULTI_AGENT -> {
            ChatScreen(
                viewModel = viewModel,
                onBack = { currentScreen = Screen.MAIN_MENU },
                isMultiAgentMode = true
            )
        }
        Screen.MODEL_COMPARISON -> {
            ModelComparisonScreen(
                onBackClick = { currentScreen = Screen.MAIN_MENU }
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
    MODEL_COMPARISON
}
