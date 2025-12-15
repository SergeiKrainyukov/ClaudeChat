package com.example.claudechat.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Модель данных для персонализированной информации о пользователе
 */
data class UserProfile(
    val age: Int? = null,
    val name: String? = null,
    val occupation: String? = null,
    val hobbies: List<String> = emptyList(),
    val habits: List<String> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
    val goals: List<String> = emptyList(),
    val languageLevel: String? = null,
    val dietaryRestrictions: List<String> = emptyList(),
    val medicalInfo: String? = null,
    val timezone: String? = null,
    val customFields: Map<String, String> = emptyMap()
) {
    companion object {
        private val gson = Gson()

        fun fromJsonStrings(
            age: Int?,
            name: String?,
            occupation: String?,
            hobbiesJson: String?,
            habitsJson: String?,
            preferencesJson: String?,
            goalsJson: String?,
            languageLevel: String?,
            dietaryRestrictionsJson: String?,
            medicalInfo: String?,
            timezone: String?,
            customFieldsJson: String?
        ): UserProfile {
            return UserProfile(
                age = age,
                name = name,
                occupation = occupation,
                hobbies = parseStringList(hobbiesJson),
                habits = parseStringList(habitsJson),
                preferences = parseStringMap(preferencesJson),
                goals = parseStringList(goalsJson),
                languageLevel = languageLevel,
                dietaryRestrictions = parseStringList(dietaryRestrictionsJson),
                medicalInfo = medicalInfo,
                timezone = timezone,
                customFields = parseStringMap(customFieldsJson)
            )
        }

        private fun parseStringList(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun parseStringMap(json: String?): Map<String, String> {
            if (json.isNullOrBlank()) return emptyMap()
            return try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    fun toJsonStrings(): JsonStrings {
        return JsonStrings(
            hobbiesJson = if (hobbies.isEmpty()) null else gson.toJson(hobbies),
            habitsJson = if (habits.isEmpty()) null else gson.toJson(habits),
            preferencesJson = if (preferences.isEmpty()) null else gson.toJson(preferences),
            goalsJson = if (goals.isEmpty()) null else gson.toJson(goals),
            dietaryRestrictionsJson = if (dietaryRestrictions.isEmpty()) null else gson.toJson(dietaryRestrictions),
            customFieldsJson = if (customFields.isEmpty()) null else gson.toJson(customFields)
        )
    }

    data class JsonStrings(
        val hobbiesJson: String?,
        val habitsJson: String?,
        val preferencesJson: String?,
        val goalsJson: String?,
        val dietaryRestrictionsJson: String?,
        val customFieldsJson: String?
    )

    /**
     * Генерирует текстовое описание профиля для system prompt
     */
    fun toSystemPrompt(): String {
        val parts = mutableListOf<String>()

        parts.add("# Персонализированная информация о пользователе:\n")

        name?.let { parts.add("Имя: $it") }
        age?.let { parts.add("Возраст: $it лет") }
        occupation?.let { parts.add("Профессия/Род деятельности: $it") }

        if (hobbies.isNotEmpty()) {
            parts.add("Хобби и интересы: ${hobbies.joinToString(", ")}")
        }

        if (habits.isNotEmpty()) {
            parts.add("Привычки: ${habits.joinToString(", ")}")
        }

        if (goals.isNotEmpty()) {
            parts.add("Цели: ${goals.joinToString("; ")}")
        }

        languageLevel?.let { parts.add("Уровень владения языками: $it") }

        if (dietaryRestrictions.isNotEmpty()) {
            parts.add("Пищевые ограничения: ${dietaryRestrictions.joinToString(", ")}")
        }

        medicalInfo?.let {
            if (it.isNotBlank()) {
                parts.add("Медицинская информация: $it")
            }
        }

        timezone?.let { parts.add("Часовой пояс: $it") }

        if (preferences.isNotEmpty()) {
            parts.add("Предпочтения:")
            preferences.forEach { (key, value) ->
                parts.add("  - $key: $value")
            }
        }

        if (customFields.isNotEmpty()) {
            parts.add("Дополнительная информация:")
            customFields.forEach { (key, value) ->
                parts.add("  - $key: $value")
            }
        }

        parts.add("\nИспользуй эту информацию для персонализации ответов и рекомендаций. Учитывай особенности, предпочтения и цели пользователя при формировании ответов.")

        return parts.joinToString("\n")
    }
}
