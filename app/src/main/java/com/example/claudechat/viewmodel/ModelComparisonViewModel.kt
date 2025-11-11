package com.example.claudechat.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.claudechat.repository.HuggingFaceRepository
import kotlinx.coroutines.launch

data class ModelResponse(
    val modelName: String,
    val response: String,
    val responseTime: Long,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCost: Double = 0.0,
    val error: String? = null
)

data class ComparisonState(
    val model1Response: ModelResponse? = null,
    val model2Response: ModelResponse? = null,
    val isLoading: Boolean = false,
    val currentPrompt: String = ""
)

class ModelComparisonViewModel : ViewModel() {

    private val repository = HuggingFaceRepository()

    private val _comparisonState = MutableLiveData<ComparisonState>(ComparisonState())
    val comparisonState: LiveData<ComparisonState> = _comparisonState

    // Модели по умолчанию (можно выбрать другие)
    // Используем модели, которые работают через Router API
    // Формат: organization/model-name или organization/model-name:provider
    private var model1Id = "meta-llama/Llama-3.2-3B-Instruct"
    private var model2Id = "deepseek-ai/DeepSeek-OCR:novita"

    /**
     * Получает текущие модели
     */
    fun getModels(): Pair<String, String> = Pair(model1Id, model2Id)

    /**
     * Отправляет запрос к обеим моделям и сравнивает результаты
     */
    fun compareModels(prompt: String) {
        viewModelScope.launch {
            // Устанавливаем состояние загрузки
            _comparisonState.value = _comparisonState.value?.copy(
                isLoading = true,
                currentPrompt = prompt,
                model1Response = null,
                model2Response = null
            )

            try {
                // Запрос к первой модели
                val startTime1 = System.currentTimeMillis()
                val result1 = repository.generateText(
                    modelId = model1Id,
                    prompt = prompt,
                    maxTokens = 256,
                    temperature = 0.7
                )
                val responseTime1 = System.currentTimeMillis() - startTime1

                // Запрос ко второй модели
                val startTime2 = System.currentTimeMillis()
                val result2 = repository.generateText(
                    modelId = model2Id,
                    prompt = prompt,
                    maxTokens = 256,
                    temperature = 0.7
                )
                val responseTime2 = System.currentTimeMillis() - startTime2

                // Обработка результатов
                val model1Response = result1.fold(
                    onSuccess = {
                        ModelResponse(
                            modelName = model1Id,
                            response = it.text,
                            responseTime = responseTime1,
                            promptTokens = it.promptTokens,
                            completionTokens = it.completionTokens,
                            totalTokens = it.totalTokens,
                            estimatedCost = it.estimatedCost
                        )
                    },
                    onFailure = {
                        ModelResponse(
                            modelName = model1Id,
                            response = "",
                            responseTime = responseTime1,
                            error = it.message
                        )
                    }
                )

                val model2Response = result2.fold(
                    onSuccess = {
                        ModelResponse(
                            modelName = model2Id,
                            response = it.text,
                            responseTime = responseTime2,
                            promptTokens = it.promptTokens,
                            completionTokens = it.completionTokens,
                            totalTokens = it.totalTokens,
                            estimatedCost = it.estimatedCost
                        )
                    },
                    onFailure = {
                        ModelResponse(
                            modelName = model2Id,
                            response = "",
                            responseTime = responseTime2,
                            error = it.message
                        )
                    }
                )

                // Обновляем состояние
                _comparisonState.value = _comparisonState.value?.copy(
                    model1Response = model1Response,
                    model2Response = model2Response,
                    isLoading = false
                )
            } catch (e: Exception) {
                // В случае ошибки
                _comparisonState.value = _comparisonState.value?.copy(
                    isLoading = false,
                    model1Response = ModelResponse(
                        modelName = model1Id,
                        response = "",
                        responseTime = 0,
                        error = e.message
                    ),
                    model2Response = ModelResponse(
                        modelName = model2Id,
                        response = "",
                        responseTime = 0,
                        error = e.message
                    )
                )
            }
        }
    }

    /**
     * Очищает результаты сравнения
     */
    fun clearResults() {
        _comparisonState.value = ComparisonState()
    }
}