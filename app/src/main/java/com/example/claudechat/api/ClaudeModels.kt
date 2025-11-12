package com.example.claudechat.api

import com.google.gson.annotations.SerializedName

data class ClaudeRequest(
    val model: String = "claude-sonnet-4-20250514",
    val max_tokens: Int = 4096,
    val messages: List<ClaudeMessage>,
    val system: String? = null,
    val temperature: Double? = null
)

data class ClaudeMessage(
    val role: String,
    val content: String
)

data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @SerializedName("stop_reason")
    val stopReason: String?,
    val usage: UsageInfo?
)

data class UsageInfo(
    @SerializedName("input_tokens")
    val inputTokens: Int,
    @SerializedName("output_tokens")
    val outputTokens: Int,
    @SerializedName("cache_creation_input_tokens")
    val cacheCreationInputTokens: Int? = null,
    @SerializedName("cache_read_input_tokens")
    val cacheReadInputTokens: Int? = null
)

data class ContentBlock(
    val type: String,
    val text: String
)

// Структурированный ответ от Claude в JSON формате
data class ClaudeJsonResponse(
    val text: String,
    val metadata: ResponseMetadata? = null
)

data class ResponseMetadata(
    val confidence: Double? = null,
    val tokens_used: Int? = null
)
