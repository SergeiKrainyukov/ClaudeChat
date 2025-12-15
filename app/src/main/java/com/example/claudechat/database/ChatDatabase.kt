package com.example.claudechat.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database для хранения истории диалогов и суммаризаций
 */
@Database(
    entities = [ConversationEntity::class, NotificationEntity::class, UserProfileEntity::class],
    version = 4,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun notificationDao(): NotificationDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                )
                    .fallbackToDestructiveMigration() // При изменении схемы пересоздать БД
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}