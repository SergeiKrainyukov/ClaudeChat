package com.example.claudechat.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.claudechat.api.*
import com.example.claudechat.model.RagMessage
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ViewModel для RAG чата с embedding server
 */
class RagChatViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = EmbeddingApiClient.apiService

    private val _messages = MutableLiveData<List<RagMessage>>(emptyList())
    val messages: LiveData<List<RagMessage>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _documents = MutableLiveData<List<DocumentInfo>>(emptyList())
    val documents: LiveData<List<DocumentInfo>> = _documents

    private val _stats = MutableLiveData<DocumentStatsResponse?>()
    val stats: LiveData<DocumentStatsResponse?> = _stats

    private val _serverHealth = MutableLiveData<HealthResponse?>()
    val serverHealth: LiveData<HealthResponse?> = _serverHealth

    private val _currentSources = MutableLiveData<List<Source>>(emptyList())
    val currentSources: LiveData<List<Source>> = _currentSources

    // Убрали автоматическую инициализацию, чтобы не делать запросы при создании ViewModel

    /**
     * Проверка здоровья сервера
     */
    fun checkServerHealth() {
        viewModelScope.launch {
            try {
                val response = apiService.health()
                if (response.isSuccessful) {
                    _serverHealth.value = response.body()
                } else {
                    _error.value = "Сервер недоступен: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "Ошибка подключения к серверу: ${e.message}"
                _serverHealth.value = null
            }
        }
    }

    /**
     * Загрузка списка документов
     */
    fun loadDocuments() {
        viewModelScope.launch {
            try {
                val response = apiService.getDocuments()
                if (response.isSuccessful) {
                    _documents.value = response.body() ?: emptyList()
                } else {
                    _error.value = "Не удалось загрузить документы: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки документов: ${e.message}"
            }
        }
    }

    /**
     * Загрузка статистики
     */
    fun loadStats() {
        viewModelScope.launch {
            try {
                val response = apiService.getStats()
                if (response.isSuccessful) {
                    _stats.value = response.body()
                } else {
                    _error.value = "Не удалось загрузить статистику: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки статистики: ${e.message}"
            }
        }
    }

    /**
     * Отправка вопроса в RAG систему
     */
    fun sendQuestion(question: String, topK: Int = 3) {
        if (question.isBlank()) return

        // Добавляем сообщение пользователя
        val userMessage = RagMessage(text = question, isUser = true)
        addMessage(userMessage)

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val request = AskQuestionRequest(question = question, topK = topK)
                val response = apiService.askQuestion(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        // Добавляем ответ системы с источниками
                        val assistantMessage = RagMessage(
                            text = body.answer,
                            isUser = false,
                            sources = body.sources
                        )
                        addMessage(assistantMessage)
                    }
                } else {
                    _error.value = "Ошибка сервера: ${response.code()}"
                    val errorMessage = RagMessage(
                        text = "Не удалось получить ответ от сервера",
                        isUser = false
                    )
                    addMessage(errorMessage)
                }
            } catch (e: Exception) {
                _error.value = "Ошибка: ${e.message}"
                val errorMessage = RagMessage(
                    text = "Произошла ошибка при обработке запроса",
                    isUser = false
                )
                addMessage(errorMessage)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Загрузка документа из URI
     */
    fun uploadDocument(uri: Uri, fileName: String) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // Читаем содержимое файла
                val content = readFileContent(uri)

                if (content.isEmpty()) {
                    _error.value = "Файл пуст"
                    _isLoading.value = false
                    return@launch
                }

                // Отправляем на сервер
                val request = UploadDocumentRequest(fileName = fileName, content = content)
                val response = apiService.uploadDocument(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        _error.value = null
                        // Обновляем список документов
                        loadDocuments()
                        loadStats()

                        // Добавляем системное сообщение об успешной загрузке
                        val successMessage = RagMessage(
                            text = "Документ '${body.fileName}' успешно загружен. Создано ${body.chunksCreated} фрагментов.",
                            isUser = false
                        )
                        addMessage(successMessage)
                    }
                } else {
                    _error.value = "Не удалось загрузить документ: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "Ошибка загрузки файла: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Чтение содержимого файла из URI
     */
    private fun readFileContent(uri: Uri): String {
        val context = getApplication<Application>().applicationContext
        val contentResolver = context.contentResolver

        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: ""
        } catch (e: Exception) {
            throw Exception("Не удалось прочитать файл: ${e.message}")
        }
    }

    /**
     * Удаление документа
     */
    fun deleteDocument(documentId: Int) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteDocument(documentId)
                if (response.isSuccessful) {
                    // Обновляем список документов
                    loadDocuments()
                    loadStats()

                    // Добавляем системное сообщение
                    val successMessage = RagMessage(
                        text = "Документ успешно удален",
                        isUser = false
                    )
                    addMessage(successMessage)
                } else {
                    _error.value = "Не удалось удалить документ: ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "Ошибка удаления документа: ${e.message}"
            }
        }
    }

    /**
     * Очистка чата
     */
    fun clearChat() {
        _messages.value = emptyList()
    }

    /**
     * Добавление сообщения в список
     */
    private fun addMessage(message: RagMessage) {
        val currentMessages = _messages.value.orEmpty().toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages
    }

    /**
     * Очистка ошибки
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Установка текущих источников для просмотра
     */
    fun setCurrentSources(sources: List<Source>) {
        _currentSources.value = sources
    }
}
