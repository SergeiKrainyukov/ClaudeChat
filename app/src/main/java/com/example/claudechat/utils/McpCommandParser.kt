package com.example.claudechat.utils

import com.example.claudechat.data.mcp.models.TodoistAction
import com.example.claudechat.data.mcp.models.TodoistPriority
import java.util.regex.Pattern

/**
 * Парсер команд для автоматического извлечения Todoist действий
 * из текстовых сообщений пользователя и ответов Claude
 */
object McpCommandParser {

    /**
     * Извлекает действия Todoist из текста
     */
    fun parseActions(text: String): List<ParsedAction> {
        val actions = mutableListOf<ParsedAction>()

        // Парсим различные типы команд
        actions.addAll(parseCreateTaskCommands(text))
        actions.addAll(parseListTasksCommands(text))
        actions.addAll(parseCompleteTaskCommands(text))
        actions.addAll(parseListProjectsCommands(text))

        return actions
    }

    /**
     * Проверяет, содержит ли текст намерение создать задачу
     */
    fun hasCreateTaskIntent(text: String): Boolean {
        val createPatterns = listOf(
            "созда.* задач",
            "добав.* задач",
            "напомн.* мне",
            "не забыть",
            "нужно",
            "надо",
            "сделать",
            "записать задач",
            "внес.* в список",
            "в todo",
            "в todoist"
        )

        val lowerText = text.lowercase()
        return createPatterns.any { pattern ->
            lowerText.contains(Regex(pattern))
        }
    }

    /**
     * Проверяет, содержит ли текст намерение показать задачи
     */
    fun hasListTasksIntent(text: String): Boolean {
        val listPatterns = listOf(
            "пока[жз].* задач",
            "спис[оа]к задач",
            "мои задач",
            "что.*мне.*сделать",
            "что.*на.*сегодня",
            "что.*запланировано",
            "задачи на",
            "дела на",
            "что.*дел"
        )

        val lowerText = text.lowercase()
        return listPatterns.any { pattern ->
            lowerText.contains(Regex(pattern))
        }
    }

    /**
     * Парсит команды создания задачи
     */
    private fun parseCreateTaskCommands(text: String): List<ParsedAction> {
        val actions = mutableListOf<ParsedAction>()

        // Паттерн 1: "Создай задачу: <content>"
        val pattern1 = Pattern.compile(
            "(?:созда[йт][ьи]?|добав[ьи]?[тьи]?) задач[уи]?:?\\s+(.+?)(?:[.!]|$)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher1 = pattern1.matcher(text)
        while (matcher1.find()) {
            val content = matcher1.group(1)?.trim() ?: continue
            actions.add(
                ParsedAction(
                    action = TodoistAction.CreateTask(content = content),
                    confidence = 0.9,
                    sourceText = matcher1.group(0) ?: ""
                )
            )
        }

        // Паттерн 2: "Напомни мне <content>"
        val pattern2 = Pattern.compile(
            "напомн[иь]\\s+мне\\s+(.+?)(?:[.!]|$)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher2 = pattern2.matcher(text)
        while (matcher2.find()) {
            val content = matcher2.group(1)?.trim() ?: continue
            actions.add(
                ParsedAction(
                    action = TodoistAction.CreateTask(content = content),
                    confidence = 0.85,
                    sourceText = matcher2.group(0) ?: ""
                )
            )
        }

        // Паттерн 3: "Не забыть <content>"
        val pattern3 = Pattern.compile(
            "не забыть\\s+(.+?)(?:[.!]|$)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher3 = pattern3.matcher(text)
        while (matcher3.find()) {
            val content = matcher3.group(1)?.trim() ?: continue
            actions.add(
                ParsedAction(
                    action = TodoistAction.CreateTask(content = content),
                    confidence = 0.8,
                    sourceText = matcher3.group(0) ?: ""
                )
            )
        }

        // Паттерн 4: Извлечение деталей задачи из предложений Claude
        val pattern4 = Pattern.compile(
            "(?:задач[ау]|todo)\\s+['\"](.+?)['\"]\\s*(?:с\\s+приоритетом\\s+(высокий|средний|низкий|high|medium|low))?",
            Pattern.CASE_INSENSITIVE
        )
        val matcher4 = pattern4.matcher(text)
        while (matcher4.find()) {
            val content = matcher4.group(1)?.trim() ?: continue
            val priorityStr = matcher4.group(2)?.lowercase()
            val priority = when (priorityStr) {
                "высокий", "high" -> TodoistPriority.HIGH.value
                "средний", "medium" -> TodoistPriority.MEDIUM.value
                "низкий", "low" -> TodoistPriority.LOW.value
                else -> null
            }

            actions.add(
                ParsedAction(
                    action = TodoistAction.CreateTask(
                        content = content,
                        priority = priority
                    ),
                    confidence = 0.75,
                    sourceText = matcher4.group(0) ?: ""
                )
            )
        }

        return actions
    }

    /**
     * Парсит команды получения списка задач
     */
    private fun parseListTasksCommands(text: String): List<ParsedAction> {
        val actions = mutableListOf<ParsedAction>()

        val listPatterns = listOf(
            "пока[жз].*задач",
            "спис[оа]к\\s+задач",
            "мои\\s+задач",
            "что.*мне.*сделать",
            "что.*на.*сегодня",
            "задачи\\s+на",
            "дела\\s+на"
        )

        val lowerText = text.lowercase()
        for (pattern in listPatterns) {
            if (lowerText.contains(Regex(pattern))) {
                actions.add(
                    ParsedAction(
                        action = TodoistAction.ListTasks,
                        confidence = 0.85,
                        sourceText = text
                    )
                )
                break // Добавляем только одну команду listTasks
            }
        }

        return actions
    }

    /**
     * Парсит команды выполнения задачи
     */
    private fun parseCompleteTaskCommands(text: String): List<ParsedAction> {
        val actions = mutableListOf<ParsedAction>()

        // Паттерн: "Отметь задачу <id> как выполненную"
        val pattern1 = Pattern.compile(
            "(?:отмет[ьи]|выполн[иь]|закр[ойы]|завер[шь]и).*задач[уи]?\\s+([a-zA-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher1 = pattern1.matcher(text)
        while (matcher1.find()) {
            val taskId = matcher1.group(1)?.trim() ?: continue
            actions.add(
                ParsedAction(
                    action = TodoistAction.CompleteTask(taskId = taskId),
                    confidence = 0.9,
                    sourceText = matcher1.group(0) ?: ""
                )
            )
        }

        return actions
    }

    /**
     * Парсит команды получения списка проектов
     */
    private fun parseListProjectsCommands(text: String): List<ParsedAction> {
        val actions = mutableListOf<ParsedAction>()

        val projectPatterns = listOf(
            "пока[жз].*проект",
            "спис[оа]к\\s+проект",
            "мои\\s+проект",
            "какие.*проект"
        )

        val lowerText = text.lowercase()
        for (pattern in projectPatterns) {
            if (lowerText.contains(Regex(pattern))) {
                actions.add(
                    ParsedAction(
                        action = TodoistAction.ListProjects,
                        confidence = 0.85,
                        sourceText = text
                    )
                )
                break
            }
        }

        return actions
    }

    /**
     * Извлекает дату/время из текста
     */
    fun extractDueDate(text: String): String? {
        val datePatterns = listOf(
            "сегодня" to "today",
            "завтра" to "tomorrow",
            "послезавтра" to "in 2 days",
            "через неделю" to "in 1 week",
            "через (\\d+) дн[ейя]" to null, // будет обработано отдельно
            "в понедельник" to "monday",
            "во вторник" to "tuesday",
            "в среду" to "wednesday",
            "в четверг" to "thursday",
            "в пятницу" to "friday",
            "в субботу" to "saturday",
            "в воскресенье" to "sunday"
        )

        val lowerText = text.lowercase()

        for ((pattern, result) in datePatterns) {
            if (result != null && lowerText.contains(pattern)) {
                return result
            } else if (pattern.contains("\\d")) {
                val regex = Regex(pattern)
                val match = regex.find(lowerText)
                if (match != null) {
                    val days = match.groupValues.getOrNull(1)?.toIntOrNull()
                    if (days != null) {
                        return "in $days days"
                    }
                }
            }
        }

        return null
    }

    /**
     * Извлекает приоритет из текста
     */
    fun extractPriority(text: String): TodoistPriority? {
        val lowerText = text.lowercase()

        return when {
            lowerText.contains("высокий приоритет") ||
            lowerText.contains("срочно") ||
            lowerText.contains("важно") -> TodoistPriority.HIGH

            lowerText.contains("средний приоритет") -> TodoistPriority.MEDIUM

            lowerText.contains("низкий приоритет") ||
            lowerText.contains("не срочно") -> TodoistPriority.LOW

            else -> null
        }
    }

    /**
     * Создает предложение действия на основе текста пользователя
     */
    fun suggestAction(userMessage: String): ParsedAction? {
        // Сначала пытаемся распарсить явные команды
        val actions = parseActions(userMessage)
        if (actions.isNotEmpty()) {
            return actions.maxByOrNull { it.confidence }
        }

        // Если явных команд нет, анализируем намерение
        when {
            hasCreateTaskIntent(userMessage) -> {
                // Пытаемся извлечь содержание задачи из всего сообщения
                val content = extractTaskContent(userMessage)
                if (content.isNotBlank()) {
                    val dueDate = extractDueDate(userMessage)
                    val priority = extractPriority(userMessage)

                    return ParsedAction(
                        action = TodoistAction.CreateTask(
                            content = content,
                            dueString = dueDate,
                            priority = priority?.value
                        ),
                        confidence = 0.7,
                        sourceText = userMessage
                    )
                }
            }

            hasListTasksIntent(userMessage) -> {
                return ParsedAction(
                    action = TodoistAction.ListTasks,
                    confidence = 0.75,
                    sourceText = userMessage
                )
            }
        }

        return null
    }

    /**
     * Извлекает содержание задачи из текста
     */
    private fun extractTaskContent(text: String): String {
        // Удаляем фразы-триггеры команд
        var content = text
        val triggersToRemove = listOf(
            "созда[йт][ьи]?\\s+задач[уи]?:?",
            "добав[ьи]?[тьи]?\\s+задач[уи]?:?",
            "напомн[иь]\\s+мне",
            "не забыть",
            "нужно",
            "надо"
        )

        for (trigger in triggersToRemove) {
            content = content.replace(Regex(trigger, RegexOption.IGNORE_CASE), "")
        }

        return content.trim()
    }
}

/**
 * Распарсенное действие с confidence score
 */
data class ParsedAction(
    val action: TodoistAction,
    val confidence: Double, // 0.0 - 1.0
    val sourceText: String
)