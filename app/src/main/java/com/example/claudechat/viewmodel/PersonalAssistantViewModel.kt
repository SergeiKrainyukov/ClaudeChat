package com.example.claudechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.example.claudechat.model.Message
import com.example.claudechat.model.UserProfile
import com.example.claudechat.repository.ChatRepository
import com.example.claudechat.repository.UserProfileRepository
import kotlinx.coroutines.launch

/**
 * ViewModel для персонального ассистента с учетом профиля пользователя
 */
class PersonalAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val chatRepository = ChatRepository(application.applicationContext)
    private val profileRepository = UserProfileRepository(application.applicationContext)

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Состояние для распознавания речи
    private val _isListening = MutableLiveData(false)
    val isListening: LiveData<Boolean> = _isListening

    private val _speechRecognitionError = MutableLiveData<String?>()
    val speechRecognitionError: LiveData<String?> = _speechRecognitionError

    // Автоматически наблюдаемый профиль пользователя
    val userProfile: LiveData<UserProfile?> = profileRepository.getUserProfile().asLiveData()

    private var currentUserProfile: UserProfile? = null

    init {
        // Загружаем профиль пользователя и устанавливаем персонализированный system prompt
        viewModelScope.launch {
            currentUserProfile = profileRepository.getUserProfileOnce()
            updateSystemPromptWithProfile()
        }

        // Автоматически отслеживаем изменения профиля
        observeProfileChanges()
    }

    /**
     * Отслеживает изменения профиля и автоматически обновляет system prompt
     */
    private fun observeProfileChanges() {
        viewModelScope.launch {
            profileRepository.getUserProfile().collect { profile ->
                if (profile != currentUserProfile) {
                    currentUserProfile = profile
                    updateSystemPromptWithProfile()
                }
            }
        }
    }

    /**
     * Обновляет system prompt с учетом профиля пользователя
     */
    private fun updateSystemPromptWithProfile() {
        val basePrompt = """
            Вы - персональный AI-ассистент пользователя. Вы всегда учитываете уникальные особенности,
            предпочтения и цели пользователя при формировании ответов.

            Ваши задачи:
            - Давать персонализированные рекомендации на основе профиля пользователя
            - Помогать достигать поставленных целей
            - Учитывать хобби, привычки и предпочтения пользователя
            - Быть полезным, дружелюбным и понимающим помощником
        """.trimIndent()

        val profilePrompt = currentUserProfile?.toSystemPrompt() ?: ""

        val fullPrompt = if (profilePrompt.isNotEmpty()) {
            "$basePrompt\n\n$profilePrompt"
        } else {
            "$basePrompt\n\nПримечание: Профиль пользователя еще не заполнен. Рекомендуйте пользователю заполнить персональную информацию для более точных рекомендаций."
        }

        chatRepository.setSystemPrompt(fullPrompt)
    }

    /**
     * Отправляет сообщение
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = Message(text = text, isUser = true)
        addMessage(userMessage)

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            chatRepository.sendMessage(text)
                .onSuccess { response ->
                    val assistantMessage = Message(
                        text = response.text,
                        isUser = false,
                        confidence = response.confidence,
                        inputTokens = response.inputTokens,
                        outputTokens = response.outputTokens,
                        totalTokens = response.totalTokens
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

    /**
     * Очищает историю чата
     */
    fun clearChat() {
        _messages.value = emptyList()
        chatRepository.clearHistory()
    }

    /**
     * Перезагружает профиль пользователя и обновляет system prompt
     * (Оставлено для совместимости, но теперь профиль обновляется автоматически)
     */
    @Deprecated("Profile updates automatically now", ReplaceWith(""))
    fun reloadUserProfile() {
        viewModelScope.launch {
            currentUserProfile = profileRepository.getUserProfileOnce()
            updateSystemPromptWithProfile()
        }
    }

    /**
     * Получает текущий профиль пользователя
     */
    fun getCurrentProfile(): UserProfile? = currentUserProfile

    /**
     * Обновляет состояние прослушивания речи
     */
    fun setListeningState(isListening: Boolean) {
        _isListening.value = isListening
    }

    /**
     * Обрабатывает результат распознавания речи
     */
    fun onSpeechRecognized(text: String) {
        _isListening.value = false
        _speechRecognitionError.value = null

        // Отправляем распознанный текст как сообщение
        sendMessage(text)
    }

    /**
     * Обрабатывает ошибку распознавания речи
     */
    fun onSpeechRecognitionError(errorMessage: String) {
        _isListening.value = false
        _speechRecognitionError.value = errorMessage
    }

    /**
     * Очищает ошибку распознавания речи
     */
    fun clearSpeechRecognitionError() {
        _speechRecognitionError.value = null
    }
}
