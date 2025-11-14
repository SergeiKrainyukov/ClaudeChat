package com.example.claudechat.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с долговременной памятью диалогов
 */
@Dao
interface ConversationDao {

    /**
     * Вставить новое сообщение
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationEntity): Long

    /**
     * Вставить несколько сообщений
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ConversationEntity>)

    /**
     * Получить все сообщения для сессии
     */
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<ConversationEntity>

    /**
     * Получить все сообщения для сессии как Flow (реактивно)
     */
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSessionFlow(sessionId: String): Flow<List<ConversationEntity>>

    /**
     * Получить последние N сообщений
     */
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastMessages(sessionId: String, limit: Int): List<ConversationEntity>

    /**
     * Получить все summary сообщения
     */
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId AND isSummary = 1 ORDER BY timestamp ASC")
    suspend fun getAllSummaries(sessionId: String): List<ConversationEntity>

    /**
     * Удалить все сообщения для сессии
     */
    @Query("DELETE FROM conversations WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    /**
     * Удалить все сообщения
     */
    @Query("DELETE FROM conversations")
    suspend fun clearAll()

    /**
     * Получить количество сообщений в сессии
     */
    @Query("SELECT COUNT(*) FROM conversations WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int

    /**
     * Получить общую статистику по токенам
     */
    @Query("SELECT SUM(estimatedTokens) FROM conversations WHERE sessionId = :sessionId")
    suspend fun getTotalTokens(sessionId: String): Int?

    /**
     * Получить общую статистику по сэкономленным токенам
     */
    @Query("SELECT SUM(savedTokens) FROM conversations WHERE sessionId = :sessionId AND isSummary = 1")
    suspend fun getTotalSavedTokens(sessionId: String): Int?

    /**
     * Удалить старые сообщения (старше определенной даты)
     */
    @Query("DELETE FROM conversations WHERE sessionId = :sessionId AND timestamp < :timestamp")
    suspend fun deleteOldMessages(sessionId: String, timestamp: Long)

    /**
     * Получить список уникальных сессий
     */
    @Query("SELECT DISTINCT sessionId FROM conversations GROUP BY sessionId ORDER BY MAX(timestamp) DESC")
    suspend fun getAllSessions(): List<String>
}