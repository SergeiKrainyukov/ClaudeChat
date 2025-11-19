package com.example.claudechat.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с уведомлениями
 */
@Dao
interface NotificationDao {

    /**
     * Получить все уведомления, отсортированные по времени (новые первые)
     */
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    /**
     * Получить последние N уведомлений
     */
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentNotifications(limit: Int): List<NotificationEntity>

    /**
     * Вставить новое уведомление
     */
    @Insert
    suspend fun insert(notification: NotificationEntity)

    /**
     * Удалить все уведомления
     */
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    /**
     * Получить количество уведомлений
     */
    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getCount(): Int
}