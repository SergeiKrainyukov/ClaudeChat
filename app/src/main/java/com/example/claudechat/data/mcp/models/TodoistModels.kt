package com.example.claudechat.data.mcp.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Модель задачи Todoist
 */
@Serializable
data class TodoistTask(
    val id: String,
    val content: String,
    val description: String = "",
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    val priority: Int = 1,
    val labels: List<String> = emptyList(),
    val due: TodoistDue? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val url: String? = null
)

/**
 * Модель срока выполнения задачи
 */
@Serializable
data class TodoistDue(
    val date: String,
    val string: String? = null,
    val timezone: String? = null,
    val recurring: Boolean = false
)

/**
 * Модель проекта Todoist
 */
@Serializable
data class TodoistProject(
    val id: String,
    val name: String,
    val color: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val order: Int = 0,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("is_shared") val isShared: Boolean = false,
    val url: String? = null
)

/**
 * Состояние подключения MCP
 */
sealed class McpConnectionState {
    object Disconnected : McpConnectionState()
    object Connecting : McpConnectionState()
    object Connected : McpConnectionState()
    data class Error(val message: String) : McpConnectionState()
}

/**
 * Действие Todoist, которое может быть выполнено
 */
sealed class TodoistAction {
    data class CreateTask(
        val content: String,
        val description: String? = null,
        val projectId: String? = null,
        val dueString: String? = null,
        val priority: Int? = null
    ) : TodoistAction()

    data class CompleteTask(val taskId: String) : TodoistAction()

    object ListTasks : TodoistAction()

    data class ListTasksForProject(val projectId: String) : TodoistAction()

    object ListProjects : TodoistAction()

    data class UpdateTask(
        val taskId: String,
        val content: String? = null,
        val description: String? = null,
        val dueString: String? = null,
        val priority: Int? = null
    ) : TodoistAction()

    data class DeleteTask(val taskId: String) : TodoistAction()
}

/**
 * Результат выполнения MCP операции
 */
sealed class McpResult<out T> {
    data class Success<T>(val data: T) : McpResult<T>()
    data class Error(val message: String, val code: Int? = null) : McpResult<Nothing>()
    object Loading : McpResult<Nothing>()
}

/**
 * Приоритеты задач Todoist
 */
enum class TodoistPriority(val value: Int, val displayName: String) {
    NONE(1, "Нет"),
    LOW(2, "Низкий"),
    MEDIUM(3, "Средний"),
    HIGH(4, "Высокий");

    companion object {
        fun fromValue(value: Int): TodoistPriority {
            return values().find { it.value == value } ?: NONE
        }
    }
}