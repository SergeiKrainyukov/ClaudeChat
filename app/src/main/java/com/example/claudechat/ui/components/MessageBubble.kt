package com.example.claudechat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.claudechat.model.Message
import com.example.claudechat.ui.theme.AssistantMessageBackground
import com.example.claudechat.ui.theme.MessageTextAssistant
import com.example.claudechat.ui.theme.MessageTextUser
import com.example.claudechat.ui.theme.UserMessageBackground
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (message.useMarkdown) 360.dp else 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (message.isUser) UserMessageBackground else AssistantMessageBackground
                )
                .padding(12.dp)
        ) {
            // Используем Markdown для сообщений с флагом useMarkdown
            if (message.useMarkdown && !message.isUser) {
                MarkdownText(
                    markdown = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MessageTextAssistant
                )
            } else {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isUser) MessageTextUser else MessageTextAssistant
                )
            }

            // Показываем процент уверенности только для сообщений ассистента
            if (!message.isUser && message.confidence != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Уверенность: ${(message.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MessageTextAssistant.copy(alpha = 0.7f)
                )
            }

            // Показываем информацию о токенах только для сообщений ассистента
            if (!message.isUser && message.totalTokens > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Токены: ${message.inputTokens} вход / ${message.outputTokens} выход (всего: ${message.totalTokens})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MessageTextAssistant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
