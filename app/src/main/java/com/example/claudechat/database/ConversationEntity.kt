package com.example.claudechat.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity для хранения сообщений в долговременной памяти
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val role: String, // "user" или "assistant"
    val content: String, // Текст сообщения
    val timestamp: Long = System.currentTimeMillis(),

    // Флаг summary сообщения
    val isSummary: Boolean = false,

    // Количество сжатых сообщений (для summary)
    val compressedMessagesCount: Int = 0,

    // Сэкономленные токены (для summary)
    val savedTokens: Int = 0,

    // Приблизительное количество токенов в сообщении
    val estimatedTokens: Int = 0,

    // ID сессии чата (для поддержки нескольких диалогов)
    val sessionId: String = "default"
)