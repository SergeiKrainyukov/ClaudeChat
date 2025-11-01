package com.example.claudechat.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.claudechat.model.Message
import com.example.claudechat.repository.ChatRepository
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    
    private val repository = ChatRepository()
    
    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
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
                    // Добавляем ответ Claude
                    val assistantMessage = Message(text = response, isUser = false)
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
}
