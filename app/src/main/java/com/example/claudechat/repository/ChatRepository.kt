package com.example.claudechat.repository

import com.example.claudechat.api.ClaudeApiClient
import com.example.claudechat.api.ClaudeJsonResponse
import com.example.claudechat.api.ClaudeMessage
import com.example.claudechat.api.ClaudeRequest
import com.google.gson.Gson

data class MessageWithConfidence(
    val text: String,
    val confidence: Double?
)

class ChatRepository {

    private val apiService = ClaudeApiClient.apiService
    private val conversationHistory = mutableListOf<ClaudeMessage>()
    private val gson = Gson()

    suspend fun sendMessage(userMessage: String): Result<MessageWithConfidence> {
        return try {
            // Добавляем сообщение пользователя в историю
            conversationHistory.add(ClaudeMessage(role = "user", content = userMessage))

            // System prompt для строгого JSON формата
            val systemPrompt = """
                You MUST respond ONLY with valid JSON in this exact format:
                {
                    "text": "your response here",
                    "metadata": {
                        "confidence": 0.95
                    }
                }
                Do not include any text before or after the JSON. Only output valid JSON.
            """.trimIndent()

            // Создаём запрос с system prompt
            val request = ClaudeRequest(
                messages = conversationHistory.toList(),
                system = systemPrompt
            )

            // Отправляем запрос
            val response = apiService.sendMessage(request)

            // Извлекаем текст ответа
            val rawResponse = response.content.firstOrNull()?.text ?: throw Exception("Нет ответа")

            // Парсим JSON ответ
            val jsonResponse = gson.fromJson(rawResponse, ClaudeJsonResponse::class.java)
            val assistantMessage = jsonResponse.text
            val confidence = jsonResponse.metadata?.confidence

            // Добавляем ответ ассистента в историю (оригинальный текст, не JSON)
            conversationHistory.add(ClaudeMessage(role = "assistant", content = assistantMessage))

            Result.success(MessageWithConfidence(text = assistantMessage, confidence = confidence))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun clearHistory() {
        conversationHistory.clear()
    }
}
