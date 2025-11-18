package com.example.claudechat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.claudechat.data.mcp.models.TodoistPriority
import com.example.claudechat.data.mcp.models.TodoistTask

/**
 * Карточка для отображения задачи Todoist
 */
@Composable
fun TodoistCard(
    task: TodoistTask,
    onComplete: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Заголовок с приоритетом
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Контент задачи
                Text(
                    text = task.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Приоритет
                if (task.priority > 1) {
                    PriorityIndicator(
                        priority = TodoistPriority.fromValue(task.priority),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Описание (если есть)
            if (task.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Срок (если есть)
            task.due?.let { due ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📅 ${due.string ?: due.date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Лейблы (если есть)
            if (task.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    task.labels.take(3).forEach { label ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    if (task.labels.size > 3) {
                        Text(
                            text = "+${task.labels.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }

            // Кнопки действий
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!task.isCompleted) {
                    // Кнопка "Выполнено"
                    FilledTonalButton(
                        onClick = { onComplete(task.id) },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Выполнить",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Выполнить", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Кнопка "Удалить"
                OutlinedButton(
                    onClick = { onDelete(task.id) },
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Индикатор приоритета задачи
 */
@Composable
fun PriorityIndicator(
    priority: TodoistPriority,
    modifier: Modifier = Modifier
) {
    val color = when (priority) {
        TodoistPriority.HIGH -> MaterialTheme.colorScheme.error
        TodoistPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        TodoistPriority.LOW -> MaterialTheme.colorScheme.primary
        TodoistPriority.NONE -> MaterialTheme.colorScheme.surfaceVariant
    }

    val text = when (priority) {
        TodoistPriority.HIGH -> "!!!"
        TodoistPriority.MEDIUM -> "!!"
        TodoistPriority.LOW -> "!"
        TodoistPriority.NONE -> ""
    }

    if (text.isNotEmpty()) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = color.copy(alpha = 0.2f)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}