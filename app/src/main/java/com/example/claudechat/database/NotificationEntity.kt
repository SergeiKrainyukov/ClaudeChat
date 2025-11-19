package com.example.claudechat.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity для хранения уведомлений о задачах Todoist
 */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val message: String,
    val taskCount: Int,
    val timestamp: Long
)