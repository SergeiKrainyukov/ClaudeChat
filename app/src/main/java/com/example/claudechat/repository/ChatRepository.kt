package com.example.claudechat.repository

import com.example.claudechat.api.ClaudeApiClient
import com.example.claudechat.api.ClaudeJsonResponse
import com.example.claudechat.api.ClaudeMessage
import com.example.claudechat.api.ClaudeRequest
import com.google.gson.Gson

data class MessageWithConfidence(
    val text: String,
    val confidence: Double?,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0
)

class ChatRepository {

    private val apiService = ClaudeApiClient.apiService
    private val conversationHistory = mutableListOf<ClaudeMessage>()
    private val gson = Gson()
    private var customSystemPrompt: String? = null
    private var temperature: Double = 1.0 // Значение по умолчанию

    // Настройки компрессии
    private var compressionEnabled: Boolean = true
    private val compressionThreshold: Int = 10 // Сжимаем каждые 10 сообщений (5 пар user-assistant)
    private var totalOriginalTokens: Int = 0 // Общее количество токенов без компрессии
    private var totalCompressedTokens: Int = 0 // Общее количество токенов с компрессией

    suspend fun sendMessage(userMessage: String, useJsonFormat: Boolean = false): Result<MessageWithConfidence> {
        return try {
            // Добавляем сообщение пользователя в историю
            conversationHistory.add(ClaudeMessage(role = "user", content = userMessage))

            // System prompt
            val systemPrompt = when {
                customSystemPrompt != null -> customSystemPrompt
                useJsonFormat -> """
                    You MUST respond ONLY with valid JSON in this exact format:
                    {
                        "text": "your response here",
                        "metadata": {
                            "confidence": 0.95
                        }
                    }
                    Do not include any text before or after the JSON. Only output valid JSON.
                    """.trimIndent()
                else -> null
            }

            // Создаём запрос
            val request = ClaudeRequest(
                messages = conversationHistory.toList(),
                system = systemPrompt,
                temperature = temperature
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

            // Извлекаем информацию о токенах
            val inputTokens = response.usage?.inputTokens ?: 0
            val outputTokens = response.usage?.outputTokens ?: 0
            val totalTokens = inputTokens + outputTokens

            Result.success(
                MessageWithConfidence(
                    text = assistantMessage,
                    confidence = confidence,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    totalTokens = totalTokens
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Устанавливает custom system prompt для режима чата
     */
    fun setSystemPrompt(prompt: String?) {
        customSystemPrompt = prompt
    }

    /**
     * Устанавливает температуру для запросов к API
     */
    fun setTemperature(temp: Double) {
        temperature = temp.coerceIn(0.0, 1.0)
    }

    /**
     * Получает текущую температуру
     */
    fun getTemperature(): Double = temperature

    /**
     * Включает/выключает компрессию истории
     */
    fun setCompressionEnabled(enabled: Boolean) {
        compressionEnabled = enabled
    }

    /**
     * Проверяет, нужна ли компрессия истории
     */
    fun shouldCompress(): Boolean {
        return compressionEnabled && conversationHistory.size >= compressionThreshold
    }

    /**
     * Создает summary для указанного количества сообщений
     */
    suspend fun createSummary(messagesToSummarize: List<ClaudeMessage>): Result<String> {
        return try {
            // Формируем текст для суммаризации
            val conversationText = messagesToSummarize.joinToString("\n\n") { message ->
                "${message.role.uppercase()}: ${message.content}"
            }

            // Создаем специальный запрос для суммаризации
            val summaryPrompt = """
                Создай краткое резюме следующего диалога, сохранив ключевую информацию и контекст.
                Резюме должно быть кратким, но информативным, чтобы можно было продолжить разговор.

                Диалог:
                $conversationText

                Резюме:
            """.trimIndent()

            val summaryMessages = listOf(ClaudeMessage(role = "user", content = summaryPrompt))
            val request = ClaudeRequest(
                messages = summaryMessages,
                system = "Ты помощник, который создает краткие резюме диалогов.",
                temperature = 0.3 // Низкая температура для более точного резюме
            )

            val response = apiService.sendMessage(request)
            val summary = response.content.firstOrNull()?.text ?: throw Exception("Не удалось создать резюме")

            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Выполняет компрессию истории диалога
     * Возвращает summary текст и количество сэкономленных токенов
     */
    suspend fun compressHistory(): Result<Pair<String, Int>> {
        return try {
            if (!shouldCompress()) {
                return Result.failure(Exception("Компрессия не требуется"))
            }

            // Берем первые compressionThreshold сообщений для сжатия
            val messagesToCompress = conversationHistory.take(compressionThreshold)

            // Подсчитываем токены в оригинальных сообщениях (приблизительно)
            val originalTokens = messagesToCompress.sumOf { estimateTokens(it.content) }

            // Создаем summary
            val summaryResult = createSummary(messagesToCompress)

            summaryResult.fold(
                onSuccess = { summary ->
                    // Подсчитываем токены в summary
                    val summaryTokens = estimateTokens(summary)
                    val savedTokens = originalTokens - summaryTokens

                    // Заменяем сжатые сообщения на summary
                    conversationHistory.removeAll(messagesToCompress)
                    conversationHistory.add(0, ClaudeMessage(
                        role = "assistant",
                        content = "РЕЗЮМЕ ПРЕДЫДУЩЕГО ДИАЛОГА: $summary"
                    ))

                    // Обновляем статистику
                    totalOriginalTokens += originalTokens
                    totalCompressedTokens += summaryTokens

                    Result.success(Pair(summary, savedTokens))
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Приблизительная оценка количества токенов в тексте
     * Используем простую эвристику: ~4 символа = 1 токен для английского,
     * ~2 символа = 1 токен для русского
     */
    private fun estimateTokens(text: String): Int {
        // Считаем количество русских и нерусских символов
        val russianChars = text.count { it in 'А'..'я' || it in 'Ё'..'ё' }
        val otherChars = text.length - russianChars

        return (russianChars / 2) + (otherChars / 4)
    }

    /**
     * Получает статистику по использованию токенов
     */
    fun getCompressionStats(): Triple<Int, Int, Int> {
        // Возвращает: (totalOriginalTokens, totalCompressedTokens, savedTokens)
        val savedTokens = totalOriginalTokens - totalCompressedTokens
        return Triple(totalOriginalTokens, totalCompressedTokens, savedTokens)
    }

    /**
     * Сбрасывает статистику компрессии
     */
    fun resetCompressionStats() {
        totalOriginalTokens = 0
        totalCompressedTokens = 0
    }
}
