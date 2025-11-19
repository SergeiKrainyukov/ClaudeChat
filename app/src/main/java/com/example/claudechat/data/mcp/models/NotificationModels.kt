package com.example.claudechat.data.mcp.models

import kotlinx.serialization.Serializable

/**
 * Структура данных для входящего уведомления о задачах
 */
@Serializable
data class NotificationData(
    val message: String,
    val taskCount: Int,
    val timestamp: Long
)

/**
 * Структура данных для статуса уведомлений
 */
@Serializable
data class NotificationStatus(
    val enabled: Boolean,
    val intervalSeconds: Int
)

/**
 * JSON-RPC notification (без поля id)
 */
@Serializable
data class McpNotification(
    val jsonrpc: String,
    val method: String,
    val params: NotificationData? = null
)