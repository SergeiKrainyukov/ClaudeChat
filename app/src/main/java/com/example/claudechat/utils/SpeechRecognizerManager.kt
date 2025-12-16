package com.example.claudechat.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Менеджер для управления распознаванием речи
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onReadyForSpeechCallback: () -> Unit = {},
    private val onEndOfSpeechCallback: () -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        private const val TAG = "SpeechRecognizer"

        /**
         * Проверяет, доступно ли распознавание речи на устройстве
         */
        fun isRecognitionAvailable(context: Context): Boolean {
            return SpeechRecognizer.isRecognitionAvailable(context)
        }
    }

    /**
     * Инициализирует распознаватель речи
     */
    private fun initializeSpeechRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech")
                        isListening = true
                        onReadyForSpeechCallback()
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Beginning of speech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Уровень громкости - можно использовать для визуализации
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        // Получены данные аудио буфера
                    }

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "End of speech")
                        isListening = false
                        onEndOfSpeechCallback()
                    }

                    override fun onError(error: Int) {
                        Log.e(TAG, "Error: $error")
                        isListening = false
                        val errorMessage = getErrorMessage(error)
                        onError(errorMessage)
                    }

                    override fun onResults(results: Bundle?) {
                        Log.d(TAG, "Results received")
                        isListening = false

                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            Log.d(TAG, "Recognized: $recognizedText")
                            onResult(recognizedText)
                        } else {
                            onError("Не удалось распознать речь")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        // Частичные результаты - можно использовать для отображения промежуточного текста
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            Log.d(TAG, "Partial: ${matches[0]}")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // Дополнительные события
                    }
                })
            }
        }
    }

    /**
     * Начинает прослушивание речи
     */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Распознавание речи недоступно на этом устройстве")
            return
        }

        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }

        initializeSpeechRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            isListening = false
            onError("Ошибка запуска распознавания: ${e.message}")
        }
    }

    /**
     * Останавливает прослушивание речи
     */
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            Log.d(TAG, "Stopped listening")
        }
    }

    /**
     * Освобождает ресурсы
     */
    fun destroy() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d(TAG, "Destroyed")
    }

    /**
     * Проверяет, идет ли сейчас прослушивание
     */
    fun isListening(): Boolean = isListening

    /**
     * Получает понятное сообщение об ошибке
     */
    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи аудио"
            SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Недостаточно разрешений для записи аудио"
            SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Превышено время ожидания сети"
            SpeechRecognizer.ERROR_NO_MATCH -> "Не удалось распознать речь"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
            SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не обнаружена"
            else -> "Неизвестная ошибка: $error"
        }
    }
}
