package com.example.claudechat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.claudechat.data.mcp.models.TodoistAction
import com.example.claudechat.utils.ParsedAction

/**
 * Кнопка действия для Todoist с иконкой
 */
@Composable
fun McpActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * Карточка с предложением выполнить Todoist действие
 */
@Composable
fun TodoistActionCard(
    parsedAction: ParsedAction,
    onExecute: (TodoistAction) -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getActionTitle(parsedAction.action),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                // Confidence badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${(parsedAction.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Описание действия
            Text(
                text = getActionDescription(parsedAction.action),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(onClick = { onExecute(parsedAction.action) }) {
                    Text("Выполнить")
                }
            }
        }
    }
}

/**
 * Панель быстрых действий Todoist
 */
@Composable
fun TodoistQuickActions(
    onCreateTask: () -> Unit,
    onListTasks: () -> Unit,
    onListProjects: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        item {
            McpActionButton(
                icon = Icons.Default.Add,
                label = "Новая задача",
                onClick = onCreateTask,
                enabled = enabled
            )
        }
        item {
            McpActionButton(
                icon = Icons.Default.List,
                label = "Мои задачи",
                onClick = onListTasks,
                enabled = enabled
            )
        }
        item {
            McpActionButton(
                icon = Icons.Default.Create,
                label = "Проекты",
                onClick = onListProjects,
                enabled = enabled
            )
        }
    }
}

/**
 * Список предложенных действий
 */
@Composable
fun TodoistActionsList(
    actions: List<ParsedAction>,
    onExecute: (TodoistAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { parsedAction ->
            TodoistActionCard(
                parsedAction = parsedAction,
                onExecute = onExecute
            )
        }
    }
}

// Helper functions

private fun getActionTitle(action: TodoistAction): String {
    return when (action) {
        is TodoistAction.CreateTask -> "Создать задачу"
        is TodoistAction.CompleteTask -> "Выполнить задачу"
        is TodoistAction.ListTasks -> "Показать задачи"
        is TodoistAction.ListTasksForProject -> "Задачи проекта"
        is TodoistAction.ListProjects -> "Показать проекты"
        is TodoistAction.UpdateTask -> "Обновить задачу"
        is TodoistAction.DeleteTask -> "Удалить задачу"
    }
}

private fun getActionDescription(action: TodoistAction): String {
    return when (action) {
        is TodoistAction.CreateTask -> {
            buildString {
                append("Содержание: ${action.content}")
                action.description?.let { append("\nОписание: $it") }
                action.dueString?.let { append("\nСрок: $it") }
                action.priority?.let { append("\nПриоритет: $it") }
            }
        }
        is TodoistAction.CompleteTask -> "Задача ID: ${action.taskId}"
        is TodoistAction.ListTasks -> "Все активные задачи"
        is TodoistAction.ListTasksForProject -> "Проект ID: ${action.projectId}"
        is TodoistAction.ListProjects -> "Все проекты"
        is TodoistAction.UpdateTask -> {
            buildString {
                append("Задача ID: ${action.taskId}")
                action.content?.let { append("\nНовое содержание: $it") }
            }
        }
        is TodoistAction.DeleteTask -> "Задача ID: ${action.taskId}"
    }
}