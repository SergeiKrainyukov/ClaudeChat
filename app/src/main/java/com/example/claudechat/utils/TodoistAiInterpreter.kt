package com.example.claudechat.utils

import android.content.Context
import com.example.claudechat.data.mcp.models.TodoistAction
import com.example.claudechat.repository.ChatRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Результат интерпретации сообщения пользователя
 */
data class InterpretationResult(
    val action: TodoistAction?,
    val responseText: String,
    val needsMoreInfo: Boolean = false
)

/**
 * JSON структура ответа Claude для интерпретации команд Todoist
 */
data class TodoistCommandResponse(
    @SerializedName("command_type")
    val commandType: String, // "create_task", "list_tasks", "list_projects", "complete_task", "delete_task", "none"

    @SerializedName("task_content")
    val taskContent: String? = null,

    @SerializedName("task_description")
    val taskDescription: String? = null,

    @SerializedName("task_id")
    val taskId: String? = null,

    @SerializedName("project_id")
    val projectId: String? = null,

    @SerializedName("due_string")
    val dueString: String? = null,

    @SerializedName("priority")
    val priority: Int? = null,

    @SerializedName("response_text")
    val responseText: String,

    @SerializedName("needs_more_info")
    val needsMoreInfo: Boolean = false
)

/**
 * Обертка для JSON ответа
 */
data class TodoistAiResponse(
    val text: String,
    val metadata: TodoistCommandResponse?
)

/**
 * Интерпретатор команд Todoist с помощью Claude AI
 */
class TodoistAiInterpreter(context: Context) {

    private val chatRepository = ChatRepository(context)
    private val gson = Gson()

    private val systemPrompt = """
        Ты AI-помощник для управления задачами Todoist. Твоя задача - анализировать сообщения пользователя
        на русском языке и определять, какую команду MCP нужно выполнить.

        ДОСТУПНЫЕ КОМАНДЫ:
        1. create_task - создать новую задачу
        2. list_tasks - показать список всех задач
        3. list_projects - показать список проектов
        4. complete_task - отметить задачу как выполненную
        5. delete_task - удалить задачу
        6. none - если сообщение не связано с управлением задачами

        ВАЖНО: Ты ДОЛЖЕН отвечать ТОЛЬКО в формате JSON:
        {
            "text": "твой дружелюбный ответ пользователю на русском",
            "metadata": {
                "command_type": "тип команды",
                "task_content": "название задачи (только для create_task)",
                "task_description": "описание задачи (опционально, только для create_task)",
                "task_id": "ID задачи (только для complete_task, delete_task)",
                "project_id": "ID проекта (опционально)",
                "due_string": "срок выполнения на английском (например: tomorrow, next monday)",
                "priority": приоритет от 1 до 4 (опционально),
                "response_text": "дружелюбный ответ пользователю",
                "needs_more_info": true если нужна дополнительная информация
            }
        }

        ПРИМЕРЫ:

        Пользователь: "Создай задачу купить молоко"
        {
            "text": "Хорошо, создаю задачу 'купить молоко'",
            "metadata": {
                "command_type": "create_task",
                "task_content": "купить молоко",
                "response_text": "Хорошо, создаю задачу 'купить молоко'",
                "needs_more_info": false
            }
        }

        Пользователь: "Покажи мои задачи"
        {
            "text": "Загружаю список ваших задач",
            "metadata": {
                "command_type": "list_tasks",
                "response_text": "Загружаю список ваших задач",
                "needs_more_info": false
            }
        }

        Пользователь: "Добавь задачу позвонить маме завтра с высоким приоритетом"
        {
            "text": "Создаю задачу 'позвонить маме' на завтра с высоким приоритетом",
            "metadata": {
                "command_type": "create_task",
                "task_content": "позвонить маме",
                "due_string": "tomorrow",
                "priority": 4,
                "response_text": "Создаю задачу 'позвонить маме' на завтра с высоким приоритетом",
                "needs_more_info": false
            }
        }

        Пользователь: "Удали задачу 12345"
        {
            "text": "Удаляю задачу #12345",
            "metadata": {
                "command_type": "delete_task",
                "task_id": "12345",
                "response_text": "Удаляю задачу #12345",
                "needs_more_info": false
            }
        }

        Пользователь: "Привет как дела?"
        {
            "text": "Привет! Я помогаю управлять задачами в Todoist. Могу создать задачу, показать список задач или проектов. Чем помочь?",
            "metadata": {
                "command_type": "none",
                "response_text": "Привет! Я помогаю управлять задачами в Todoist. Могу создать задачу, показать список задач или проектов. Чем помочь?",
                "needs_more_info": false
            }
        }

        ПРАВИЛА:
        - Всегда отвечай дружелюбно на русском языке
        - Если пользователь просит создать задачу, но не указал название - установи needs_more_info: true
        - Для complete_task и delete_task требуется task_id - если его нет, установи needs_more_info: true
        - due_string должен быть на английском (today, tomorrow, next monday, etc.)
        - priority: 1 (низкий), 2 (средний), 3 (высокий), 4 (очень высокий)
        - Распознавай варианты: "создай/добавь/новая задача", "покажи/список задач", "выполнена/готово", "удали/убери"
        - Не добавляй никакого текста кроме JSON
    """.trimIndent()

    init {
        chatRepository.setSystemPrompt(systemPrompt)
        chatRepository.setTemperature(0.3) // Низкая температура для более предсказуемых ответов
    }

    /**
     * Интерпретирует сообщение пользователя и возвращает команду для выполнения
     */
    suspend fun interpretMessage(userMessage: String): Result<InterpretationResult> {
        return try {
            // Отправляем сообщение в Claude с требованием JSON ответа
            val result = chatRepository.sendMessage(userMessage, useJsonFormat = false)

            result.fold(
                onSuccess = { response ->
                    // Парсим JSON ответ
                    val aiResponse = parseAiResponse(response.text)

                    // Преобразуем в TodoistAction
                    val action = convertToAction(aiResponse.metadata)

                    Result.success(
                        InterpretationResult(
                            action = action,
                            responseText = aiResponse.metadata?.responseText ?: aiResponse.text,
                            needsMoreInfo = aiResponse.metadata?.needsMoreInfo ?: false
                        )
                    )
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Парсит JSON ответ от Claude
     */
    private fun parseAiResponse(rawResponse: String): TodoistAiResponse {
        // Очищаем от markdown code blocks
        val cleanedResponse = rawResponse
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            gson.fromJson(cleanedResponse, TodoistAiResponse::class.java)
        } catch (e: Exception) {
            // Если не удалось распарсить, возвращаем ответ без метаданных
            TodoistAiResponse(
                text = rawResponse,
                metadata = TodoistCommandResponse(
                    commandType = "none",
                    responseText = rawResponse
                )
            )
        }
    }

    /**
     * Преобразует метаданные из ответа Claude в TodoistAction
     */
    private fun convertToAction(metadata: TodoistCommandResponse?): TodoistAction? {
        if (metadata == null) return null

        return when (metadata.commandType) {
            "create_task" -> {
                if (metadata.taskContent.isNullOrBlank()) {
                    null // Недостаточно информации
                } else {
                    TodoistAction.CreateTask(
                        content = metadata.taskContent,
                        description = metadata.taskDescription,
                        projectId = metadata.projectId,
                        dueString = metadata.dueString,
                        priority = metadata.priority
                    )
                }
            }
            "list_tasks" -> TodoistAction.ListTasks
            "list_projects" -> TodoistAction.ListProjects
            "complete_task" -> {
                if (metadata.taskId.isNullOrBlank()) {
                    null
                } else {
                    TodoistAction.CompleteTask(taskId = metadata.taskId)
                }
            }
            "delete_task" -> {
                if (metadata.taskId.isNullOrBlank()) {
                    null
                } else {
                    TodoistAction.DeleteTask(taskId = metadata.taskId)
                }
            }
            else -> null // "none" или неизвестная команда
        }
    }

    /**
     * Очищает историю диалога
     */
    fun clearHistory() {
        chatRepository.clearHistory()
    }
}