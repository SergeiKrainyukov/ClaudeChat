package com.example.claudechat.api

import com.google.gson.annotations.SerializedName

// Новый формат запроса для Hugging Face Router API (v1/chat/completions)
data class HuggingFaceRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    @SerializedName("top_p")
    val topP: Double? = null
)

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)

// Ответ от Hugging Face Router API
data class HuggingFaceResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
    val error: ErrorDetails? = null
)

data class Choice(
    val index: Int? = null,
    val message: ChatMessage? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerializedName("completion_tokens")
    val completionTokens: Int? = null,
    @SerializedName("total_tokens")
    val totalTokens: Int? = null
)

data class ErrorDetails(
    val message: String? = null,
    val type: String? = null
)

// Для совместимости (если понадобится)
typealias HuggingFaceArrayResponse = HuggingFaceResponse