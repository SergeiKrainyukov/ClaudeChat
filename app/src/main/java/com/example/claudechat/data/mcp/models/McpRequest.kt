package com.example.claudechat.data.mcp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSON-RPC 2.0 Request модель для MCP сервера
 */
@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonObject? = null
)

/**
 * Вспомогательные классы для создания параметров запросов
 */
object McpParams {

    /**
     * Параметры для создания задачи в Todoist
     */
    @Serializable
    data class CreateTask(
        val content: String,
        val description: String? = null,
        val project_id: String? = null,
        val due_string: String? = null,
        val priority: Int? = null,
        val labels: List<String>? = null
    )

    /**
     * Параметры для получения списка задач
     */
    @Serializable
    data class ListTasks(
        val project_id: String? = null,
        val filter: String? = null
    )

    /**
     * Параметры для выполнения задачи
     */
    @Serializable
    data class CompleteTask(
        val task_id: String
    )

    /**
     * Параметры для получения списка проектов
     */
    @Serializable
    data class ListProjects(
        val limit: Int? = null
    )

    /**
     * Параметры для обновления задачи
     */
    @Serializable
    data class UpdateTask(
        val task_id: String,
        val content: String? = null,
        val description: String? = null,
        val due_string: String? = null,
        val priority: Int? = null,
        val labels: List<String>? = null
    )

    /**
     * Параметры для удаления задачи
     */
    @Serializable
    data class DeleteTask(
        val task_id: String
    )
}