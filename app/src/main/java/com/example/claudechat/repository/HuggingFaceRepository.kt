package com.example.claudechat.repository

import com.example.claudechat.api.ChatMessage
import com.example.claudechat.api.HuggingFaceApiClient
import com.example.claudechat.api.HuggingFaceRequest

data class HuggingFaceModelResult(
    val text: String,
    val modelName: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCost: Double = 0.0,
    val error: String? = null
)

class HuggingFaceRepository {

    private val apiService = HuggingFaceApiClient.apiService

    /**
     * Рассчитывает примерную стоимость запроса
     * Hugging Face Inference API бесплатен для большинства моделей,
     * но некоторые платные модели имеют ценообразование по токенам
     */
    private fun calculateCost(modelId: String, promptTokens: Int, completionTokens: Int): Double {
        // Цены в долларах за 1M токенов
        val pricing = when {
            // Бесплатные модели через Inference API
            modelId.contains("llama", ignoreCase = true) -> 0.0
            modelId.contains("qwen", ignoreCase = true) -> 0.0
            modelId.contains("mistral", ignoreCase = true) -> 0.0
            modelId.contains("gemma", ignoreCase = true) -> 0.0

            // DeepSeek с провайдером может быть платным
            modelId.contains("deepseek") && modelId.contains(":") -> {
                // Примерные цены для платных провайдеров
                Pair(0.10, 0.30) // $0.10 за 1M prompt, $0.30 за 1M completion
            }

            // По умолчанию считаем бесплатным
            else -> 0.0
        }

        return when (pricing) {
            is Pair<*, *> -> {
                val (promptPrice, completionPrice) = pricing as Pair<Double, Double>
                (promptTokens * promptPrice / 1_000_000.0) +
                (completionTokens * completionPrice / 1_000_000.0)
            }
            is Double -> pricing
            else -> 0.0
        }
    }

    /**
     * Отправляет запрос к указанной модели на Hugging Face
     * @param modelId - ID модели на Hugging Face (например, "gpt2" или "meta-llama/Llama-2-7b-chat-hf")
     * @param prompt - текст запроса
     * @param maxTokens - максимальное количество токенов в ответе
     * @param temperature - температура генерации (0.0 - 1.0)
     */
    suspend fun generateText(
        modelId: String,
        prompt: String,
        maxTokens: Int = 512,
        temperature: Double = 0.7
    ): Result<HuggingFaceModelResult> {
        return try {
            // Новый формат запроса для Router API
            val request = HuggingFaceRequest(
                model = modelId,
                messages = listOf(
                    ChatMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                stream = false,
                max_tokens = maxTokens,
                temperature = temperature,
                topP = 0.9
            )

            val response = apiService.generateText(request)

            // Обработка ответа
            if (response.error != null) {
                Result.failure(Exception(response.error.message ?: "Неизвестная ошибка"))
            } else if (response.choices != null && response.choices.isNotEmpty()) {
                val generatedText = response.choices[0].message?.content
                    ?: throw Exception("Нет текста в ответе")

                // Извлекаем информацию о токенах
                val usage = response.usage
                val promptTokens = usage?.promptTokens ?: 0
                val completionTokens = usage?.completionTokens ?: 0
                val totalTokens = usage?.totalTokens ?: 0

                // Рассчитываем стоимость
                val estimatedCost = calculateCost(modelId, promptTokens, completionTokens)

                Result.success(
                    HuggingFaceModelResult(
                        text = generatedText.trim(),
                        modelName = modelId,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens,
                        estimatedCost = estimatedCost
                    )
                )
            } else {
                Result.failure(Exception("Пустой ответ от API"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отправляет один и тот же запрос к двум разным моделям одновременно
     */
    suspend fun compareModels(
        model1Id: String,
        model2Id: String,
        prompt: String,
        maxTokens: Int = 512,
        temperature: Double = 0.7
    ): Pair<Result<HuggingFaceModelResult>, Result<HuggingFaceModelResult>> {
        // Запускаем оба запроса параллельно
        val result1 = generateText(model1Id, prompt, maxTokens, temperature)
        val result2 = generateText(model2Id, prompt, maxTokens, temperature)

        return Pair(result1, result2)
    }
}