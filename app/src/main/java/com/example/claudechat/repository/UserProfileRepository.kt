package com.example.claudechat.repository

import android.content.Context
import com.example.claudechat.database.ChatDatabase
import com.example.claudechat.database.UserProfileEntity
import com.example.claudechat.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository для управления персональным профилем пользователя
 */
class UserProfileRepository(context: Context) {

    private val database = ChatDatabase.getDatabase(context)
    private val userProfileDao = database.userProfileDao()

    /**
     * Получает профиль пользователя как Flow
     */
    fun getUserProfile(): Flow<UserProfile?> {
        return userProfileDao.getUserProfile().map { entity ->
            entity?.let { convertEntityToProfile(it) }
        }
    }

    /**
     * Получает профиль пользователя единожды
     */
    suspend fun getUserProfileOnce(): UserProfile? {
        val entity = userProfileDao.getUserProfileOnce()
        return entity?.let { convertEntityToProfile(it) }
    }

    /**
     * Сохраняет или обновляет профиль пользователя
     */
    suspend fun saveUserProfile(profile: UserProfile) {
        val jsonStrings = profile.toJsonStrings()
        val entity = UserProfileEntity(
            id = 1,
            age = profile.age,
            name = profile.name,
            occupation = profile.occupation,
            hobbies = jsonStrings.hobbiesJson,
            habits = jsonStrings.habitsJson,
            preferences = jsonStrings.preferencesJson,
            goals = jsonStrings.goalsJson,
            languageLevel = profile.languageLevel,
            dietaryRestrictions = jsonStrings.dietaryRestrictionsJson,
            medicalInfo = profile.medicalInfo,
            timezone = profile.timezone,
            customFields = jsonStrings.customFieldsJson,
            updatedAt = System.currentTimeMillis()
        )
        userProfileDao.insertOrUpdate(entity)
    }

    /**
     * Удаляет профиль пользователя
     */
    suspend fun deleteUserProfile() {
        userProfileDao.deleteProfile()
    }

    /**
     * Конвертирует Entity в модель UserProfile
     */
    private fun convertEntityToProfile(entity: UserProfileEntity): UserProfile {
        return UserProfile.fromJsonStrings(
            age = entity.age,
            name = entity.name,
            occupation = entity.occupation,
            hobbiesJson = entity.hobbies,
            habitsJson = entity.habits,
            preferencesJson = entity.preferences,
            goalsJson = entity.goals,
            languageLevel = entity.languageLevel,
            dietaryRestrictionsJson = entity.dietaryRestrictions,
            medicalInfo = entity.medicalInfo,
            timezone = entity.timezone,
            customFieldsJson = entity.customFields
        )
    }
}
