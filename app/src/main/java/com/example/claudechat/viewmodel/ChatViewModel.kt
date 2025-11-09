package com.example.claudechat.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.claudechat.model.Message
import com.example.claudechat.repository.ChatRepository
import com.example.claudechat.utils.ChatType
import com.example.claudechat.utils.SystemPrompts
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentChatType: ChatType = ChatType.DEFAULT
    
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        // Добавляем сообщение пользователя
        val userMessage = Message(text = text, isUser = true)
        addMessage(userMessage)
        
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            repository.sendMessage(text)
                .onSuccess { response ->
                    // Добавляем ответ Claude с confidence
                    val assistantMessage = Message(
                        text = response.text,
                        isUser = false,
                        confidence = response.confidence,
                        useMarkdown = currentChatType == ChatType.MULTI_AGENT
                    )
                    addMessage(assistantMessage)
                    _isLoading.value = false
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
}
