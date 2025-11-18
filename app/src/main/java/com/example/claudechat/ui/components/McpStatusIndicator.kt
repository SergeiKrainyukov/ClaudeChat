package com.example.claudechat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.claudechat.data.mcp.models.McpConnectionState

/**
 * Индикатор статуса подключения MCP
 */
@Composable
fun McpStatusIndicator(
    connectionState: McpConnectionState,
    onReconnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (icon, color, text) = when (connectionState) {
        is McpConnectionState.Connected -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            "Todoist подключен"
        )
        is McpConnectionState.Connecting -> Triple(
            Icons.Default.Info,
            MaterialTheme.colorScheme.tertiary,
            "Подключение к Todoist..."
        )
        is McpConnectionState.Disconnected -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            "Todoist отключен"
        )
        is McpConnectionState.Error -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
            "Ошибка: ${connectionState.message}"
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Анимированная иконка для состояния "Connecting"
                if (connectionState is McpConnectionState.Connecting) {
                    PulsingIcon(icon = icon, tint = color)
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            // Кнопка переподключения для отключенного состояния
            if (connectionState is McpConnectionState.Disconnected ||
                connectionState is McpConnectionState.Error) {
                TextButton(
                    onClick = onReconnect,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Переподключить",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/**
 * Пульсирующая иконка для состояния загрузки
 */
@Composable
private fun PulsingIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(20.dp)
            .scale(scale)
    )
}

/**
 * Компактная версия индикатора (только иконка)
 */
@Composable
fun McpStatusBadge(
    connectionState: McpConnectionState,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (connectionState) {
        is McpConnectionState.Connected -> Pair(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary
        )
        is McpConnectionState.Connecting -> Pair(
            Icons.Default.Info,
            MaterialTheme.colorScheme.tertiary
        )
        is McpConnectionState.Disconnected -> Pair(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        )
        is McpConnectionState.Error -> Pair(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error
        )
    }

    if (connectionState is McpConnectionState.Connecting) {
        PulsingIcon(icon = icon, tint = color, modifier = modifier)
    } else {
        Icon(
            imageVector = icon,
            contentDescription = when (connectionState) {
                is McpConnectionState.Connected -> "Todoist подключен"
                is McpConnectionState.Connecting -> "Подключение..."
                is McpConnectionState.Disconnected -> "Todoist отключен"
                is McpConnectionState.Error -> "Ошибка подключения"
            },
            tint = color,
            modifier = modifier.size(20.dp)
        )
    }
}