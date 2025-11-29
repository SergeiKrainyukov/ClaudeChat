package com.example.claudechat.model

import com.example.claudechat.api.Source

/**
 * Модель сообщения для RAG чата
 */
data class RagMessage(
    val text: String,
    val isUser: Boolean,
    val sources: List<Source>? = null,
    val timestamp: Long = System.currentTimeMillis()
)
