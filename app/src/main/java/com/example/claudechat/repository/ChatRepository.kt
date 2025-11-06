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

    suspend fun sendMessage(userMessage: String, useJsonFormat: Boolean = false): Result<MessageWithConfidence> {
        return try {
            // Добавляем сообщение пользователя в историю
            conversationHistory.add(ClaudeMessage(role = "user", content = userMessage))

            // System prompt (только для JSON формата если нужно)
            val systemPrompt = if (useJsonFormat) {
                """
                You MUST respond ONLY with valid JSON in this exact format:
                {
                    "text": "your response here",
                    "metadata": {
                        "confidence": 0.95
                    }
                }
                Do not include any text before or after the JSON. Only output valid JSON.
                """.trimIndent()
            } else {
                null
            }

            // Создаём запрос
            val request = ClaudeRequest(
                messages = conversationHistory.toList(),
                system = systemPrompt
            )

            // Отправляем запрос
            val response = apiService.sendMessage(request)

            // Извлекаем текст ответа
            val rawResponse = response.content.firstOrNull()?.text ?: throw Exception("Нет ответа")

            var assistantMessage: String
            var confidence: Double?

            if (useJsonFormat) {
                // Очищаем от markdown code blocks если присутствуют
                val cleanedResponse = rawResponse
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                // Парсим JSON ответ
                val jsonResponse = gson.fromJson(cleanedResponse, ClaudeJsonResponse::class.java)
                assistantMessage = jsonResponse.text
                confidence = jsonResponse.metadata?.confidence
            } else {
                // Используем сырой текст
                assistantMessage = rawResponse
                confidence = null
            }

            // Добавляем ответ ассистента в историю
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
