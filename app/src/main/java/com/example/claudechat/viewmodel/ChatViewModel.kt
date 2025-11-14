package com.example.claudechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.claudechat.model.Message
import com.example.claudechat.repository.ChatRepository
import com.example.claudechat.utils.ChatType
import com.example.claudechat.utils.SystemPrompts
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application.applicationContext)

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _temperature = MutableLiveData(1.0)
    val temperature: LiveData<Double> = _temperature

    private val _compressionStats = MutableLiveData<Triple<Int, Int, Int>>(Triple(0, 0, 0))
    val compressionStats: LiveData<Triple<Int, Int, Int>> = _compressionStats

    private var currentChatType: ChatType = ChatType.DEFAULT
    
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Добавляем сообщение пользователя
        val userMessage = Message(text = text, isUser = true)
        addMessage(userMessage)

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            // Проверяем, нужна ли компрессия перед отправкой
            if (repository.shouldCompress()) {
                compressHistoryIfNeeded()
            }

            repository.sendMessage(text)
                .onSuccess { response ->
                    // Добавляем ответ Claude с confidence и токенами
                    val assistantMessage = Message(
                        text = response.text,
                        isUser = false,
                        confidence = response.confidence,
                        useMarkdown = currentChatType == ChatType.MULTI_AGENT,
                        inputTokens = response.inputTokens,
                        outputTokens = response.outputTokens,
                        totalTokens = response.totalTokens
                    )
                    addMessage(assistantMessage)
                    _isLoading.value = false

                    // Обновляем статистику компрессии
                    updateCompressionStats()
                }
                .onFailure { exception ->
                    _error.value = "Ошибка: ${exception.message}"
                    _isLoading.value = false
                }
        }
    }
    
    private fun addMessage(message: Message) {
        val currentMessages = _messages.value.orEmpty().toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages
    }

    fun clearChat() {
        _messages.value = emptyList()
        repository.clearHistory()
    }

    /**
     * Загружает начальное сообщение в чат
     * Если это summary, добавляет его в system prompt вместо отображения в чате
     */
    fun loadInitialMessage(content: String, isSummary: Boolean = false) {
        clearChat()

        if (isSummary) {
            // Если это summary, добавляем его в system prompt для контекста
            val systemPromptWithSummary = """
                Вы - AI-ассистент Claude. Используйте следующую информацию из предыдущего диалога для контекста:

                $content

                Продолжайте беседу, используя этот контекст, но не упоминайте напрямую, что получили резюме.
            """.trimIndent()

            repository.setSystemPrompt(systemPromptWithSummary)

            // Добавляем информационное сообщение в UI
            val infoMessage = Message(
                text = "📋 Загружен контекст из предыдущего диалога. Можете продолжить беседу.",
                isUser = false,
                useMarkdown = false,
                isSummary = false
            )
            addMessage(infoMessage)
        } else {
            // Если это обычное сообщение, просто отображаем его
            val initialMessage = Message(
                text = content,
                isUser = false,
                useMarkdown = true,
                isSummary = false
            )
            addMessage(initialMessage)
        }
    }

    /**
     * Создает summary текущего диалога и сохраняет в БД
     */
    fun saveSummary() {
        if (repository.getHistorySize() == 0) {
            _error.value = "История диалога пуста"
            return
        }

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = repository.createAndSaveSummary()

            result.fold(
                onSuccess = { (summary, savedTokens) ->
                    println("ChatViewModel: Summary успешно сохранен - savedTokens: $savedTokens")

                    // Добавляем summary как сообщение в UI
                    val summaryMessage = Message(
                        text = summary,
                        isUser = false,
                        useMarkdown = true,
                        isSummary = true,
                        savedTokens = savedTokens,
                        originalMessagesCount = repository.getHistorySize()
                    )
                    addMessage(summaryMessage)

                    _isLoading.value = false
                },
                onFailure = { e ->
                    println("ChatViewModel: Ошибка сохранения summary - ${e.message}")
                    _error.value = "Ошибка создания summary: ${e.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Получает количество сообщений в истории
     */
    fun getHistorySize(): Int = repository.getHistorySize()

    /**
     * Устанавливает режим чата (обычный или многоагентный)
     */
    fun setMultiAgentMode(isMultiAgent: Boolean) {
        val newChatType = if (isMultiAgent) ChatType.MULTI_AGENT else ChatType.DEFAULT

        // Если режим изменился, очищаем чат и устанавливаем новый system prompt
        if (currentChatType != newChatType) {
            currentChatType = newChatType
            clearChat()
            repository.setSystemPrompt(SystemPrompts.getPrompt(currentChatType))
        }
    }

    /**
     * Устанавливает температуру для запросов
     */
    fun setTemperature(temp: Double) {
        val validTemp = temp.coerceIn(0.0, 1.0)
        _temperature.value = validTemp
        repository.setTemperature(validTemp)
    }

    /**
     * Выполняет компрессию истории диалога
     */
    private suspend fun compressHistoryIfNeeded() {
        repository.compressHistory()
            .onSuccess { (summary, savedTokens) ->
                // Добавляем summary сообщение в UI
                val summaryMessage = Message(
                    text = "📊 История диалога сжата. Сэкономлено ~$savedTokens токенов.\n\nРезюме: $summary",
                    isUser = false,
                    isSummary = true,
                    originalMessagesCount = 10,
                    savedTokens = savedTokens,
                    useMarkdown = true
                )

                // Удаляем сжатые сообщения из UI (первые 10)
                val currentMessages = _messages.value.orEmpty().toMutableList()
                if (currentMessages.size >= 10) {
                    // Удаляем первые 10 сообщений
                    repeat(10) {
                        if (currentMessages.isNotEmpty()) {
                            currentMessages.removeAt(0)
                        }
                    }
                }
                // Добавляем summary в начало
                currentMessages.add(0, summaryMessage)
                _messages.value = currentMessages
            }
            .onFailure { exception ->
                // Логируем ошибку, но не показываем пользователю
                println("Ошибка компрессии: ${exception.message}")
            }
    }

    /**
     * Обновляет статистику компрессии
     */
    private fun updateCompressionStats() {
        _compressionStats.value = repository.getCompressionStats()
    }

    /**
     * Включает/выключает компрессию
     */
    fun setCompressionEnabled(enabled: Boolean) {
        repository.setCompressionEnabled(enabled)
    }
}
