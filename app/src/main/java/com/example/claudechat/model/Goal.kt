package com.example.claudechat.model

/**
 * Типы целей, которые может поставить пользователь
 */
enum class GoalType {
    GAME_DEVELOPMENT,
    MOBILE_APP,
    WEB_APP,
    LEARNING,
    BUSINESS,
    CREATIVE_PROJECT,
    OTHER
}

/**
 * Сообщение в чате достижения целей
 */
data class GoalChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Цель пользователя
 */
data class Goal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: GoalType,
    val title: String,
    val chatHistory: MutableList<GoalChatMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Шаг алгоритма достижения цели
 */
data class AlgorithmStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val estimatedTime: String,
    val resources: List<String> = emptyList(),
    val subSteps: List<String> = emptyList()
)

/**
 * Алгоритм достижения цели
 */
data class GoalAlgorithm(
    val goalId: String,
    val steps: List<AlgorithmStep>,
    val totalEstimatedTime: String,
    val difficulty: String,
    val prerequisites: List<String> = emptyList(),
    val tips: List<String> = emptyList()
)