package com.example.claudechat.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.claudechat.model.Message
import com.example.claudechat.ui.theme.ClaudeChatTheme

@Preview(showBackground = true, name = "User Message")
@Composable
fun PreviewUserMessage() {
    ClaudeChatTheme {
        MessageBubble(
            message = Message(
                text = "Привет! Как дела?",
                isUser = true
            ),
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true, name = "Assistant Message")
@Composable
fun PreviewAssistantMessage() {
    ClaudeChatTheme {
        MessageBubble(
            message = Message(
                text = "Здравствуйте! У меня всё отлично, спасибо за вопрос. Чем я могу вам помочь сегодня?",
                isUser = false
            ),
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true, name = "Long User Message")
@Composable
fun PreviewLongUserMessage() {
    ClaudeChatTheme {
        MessageBubble(
            message = Message(
                text = "Это очень длинное сообщение от пользователя, которое должно красиво переноситься на несколько строк и при этом сохранять читабельность и визуальную привлекательность.",
                isUser = true
            ),
            modifier = Modifier.padding(8.dp)
        )
    }
}
