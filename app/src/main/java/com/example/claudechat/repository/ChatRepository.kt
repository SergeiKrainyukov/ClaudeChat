package com.example.claudechat.repository

import com.example.claudechat.api.ClaudeApiClient
import com.example.claudechat.api.ClaudeMessage
import com.example.claudechat.api.ClaudeRequest

class ChatRepository {
    
    private val apiService = ClaudeApiClient.apiService
    private val conversationHistory = mutableListOf<ClaudeMessage>()
    
    suspend fun sendMessage(userMessage: String): Result<String> {
        return try {
            // Добавляем сообщение пользователя в историю
            conversationHistory.add(ClaudeMessage(role = "user", content = userMessage))
            
            // Создаём запрос
            val request = ClaudeRequest(
                messages = conversationHistory.toList()
            )
            
            // Отправляем запрос
            val response = apiService.sendMessage(request)
            
            // Извлекаем текст ответа
            val assistantMessage = response.content.firstOrNull()?.text ?: "Нет ответа"
            
            // Добавляем ответ ассистента в историю
            conversationHistory.add(ClaudeMessage(role = "assistant", content = assistantMessage))
            
            Result.success(assistantMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun clearHistory() {
        conversationHistory.clear()
    }
}
