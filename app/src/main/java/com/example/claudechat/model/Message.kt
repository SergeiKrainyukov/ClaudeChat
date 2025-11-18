package com.example.claudechat.model

import com.example.claudechat.data.mcp.models.TodoistAction
import com.example.claudechat.utils.ParsedAction

data class Message(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Double? = null,
    val useMarkdown: Boolean = false,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val isSummary: Boolean = false, // Флаг, что это summary сообщение
    val originalMessagesCount: Int = 0, // Количество оригинальных сообщений в summary
    val savedTokens: Int = 0, // Количество сэкономленных токенов благодаря сжатию

    // MCP/Todoist интеграция
    val todoistActions: List<ParsedAction>? = null, // Распарсенные Todoist действия
    val hasTodoistSuggestion: Boolean = false // Флаг наличия предложений Todoist
)
