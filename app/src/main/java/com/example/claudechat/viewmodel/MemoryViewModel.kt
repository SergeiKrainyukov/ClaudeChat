package com.example.claudechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.claudechat.database.ChatDatabase
import com.example.claudechat.database.ConversationEntity
import kotlinx.coroutines.launch

data class MemoryStats(
    val totalMessages: Int = 0,
    val totalSummaries: Int = 0,
    val totalTokens: Int = 0,
    val savedTokens: Int = 0
)

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ChatDatabase.getDatabase(application.applicationContext)
    private val conversationDao = database.conversationDao()
    private val sessionId = "default"

    private val _messages = MutableLiveData<List<ConversationEntity>>(emptyList())
    val messages: LiveData<List<ConversationEntity>> = _messages

    private val _stats = MutableLiveData<MemoryStats>(MemoryStats())
    val stats: LiveData<MemoryStats> = _stats

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadMessages()
        loadStats()
    }

    /**
     * Загружает все сообщения из БД
     */
    fun loadMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allMessages = conversationDao.getMessagesForSession(sessionId)
                println("MemoryViewModel: Загружено сообщений: ${allMessages.size} для sessionId: $sessionId")
                allMessages.forEachIndexed { index, msg ->
                    println("MemoryViewModel: Сообщение $index - role: ${msg.role}, isSummary: ${msg.isSummary}, content: ${msg.content.take(50)}")
                }
                _messages.value = allMessages
            } catch (e: Exception) {
                println("Ошибка загрузки сообщений: ${e.message}")
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Загружает статистику из БД
     */
    fun loadStats() {
        viewModelScope.launch {
            try {
                val messageCount = conversationDao.getMessageCount(sessionId)
                val summaries = conversationDao.getAllSummaries(sessionId)
                val totalTokens = conversationDao.getTotalTokens(sessionId) ?: 0
                val savedTokens = conversationDao.getTotalSavedTokens(sessionId) ?: 0

                _stats.value = MemoryStats(
                    totalMessages = messageCount,
                    totalSummaries = summaries.size,
                    totalTokens = totalTokens,
                    savedTokens = savedTokens
                )
            } catch (e: Exception) {
                println("Ошибка загрузки статистики: ${e.message}")
            }
        }
    }

    /**
     * Очищает всю память (БД)
     */
    fun clearMemory() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                conversationDao.clearSession(sessionId)
                _messages.value = emptyList()
                _stats.value = MemoryStats()
            } catch (e: Exception) {
                println("Ошибка очистки памяти: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Удаляет старые сообщения (старше N дней)
     */
    fun deleteOldMessages(daysOld: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
                conversationDao.deleteOldMessages(sessionId, cutoffTime)
                loadMessages()
                loadStats()
            } catch (e: Exception) {
                println("Ошибка удаления старых сообщений: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}