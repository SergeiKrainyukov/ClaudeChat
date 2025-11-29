package com.example.claudechat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.claudechat.model.RagMessage
import com.example.claudechat.api.Source
import java.text.SimpleDateFormat
import java.util.*

/**
 * Компонент для отображения сообщения в RAG чате
 */
@Composable
fun RagMessageBubble(
    message: RagMessage,
    onShowSources: (List<Source>) -> Unit,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        // Основное сообщение
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp),
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Время отправки
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(message.timestamp),
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Источники (если есть)
        if (!message.sources.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onShowSources(message.sources) },
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text("Показать источники (${message.sources.size})")
            }
        }
    }
}

/**
 * Форматирование timestamp
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
