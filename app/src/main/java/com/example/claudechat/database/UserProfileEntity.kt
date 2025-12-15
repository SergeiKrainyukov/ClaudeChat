package com.example.claudechat.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity для хранения персонализированной информации о пользователе
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1, // Всегда будет один профиль с id=1
    val age: Int? = null,
    val name: String? = null,
    val occupation: String? = null,
    val hobbies: String? = null, // JSON строка со списком хобби
    val habits: String? = null, // JSON строка со списком привычек
    val preferences: String? = null, // JSON строка с различными предпочтениями
    val goals: String? = null, // JSON строка с целями пользователя
    val languageLevel: String? = null, // Уровень владения языками
    val dietaryRestrictions: String? = null, // Пищевые ограничения
    val medicalInfo: String? = null, // Медицинская информация
    val timezone: String? = null, // Часовой пояс
    val customFields: String? = null, // JSON строка с дополнительными полями
    val updatedAt: Long = System.currentTimeMillis()
)
