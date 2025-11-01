# Примеры расширения приложения

## 1. Добавление темной темы

### Шаг 1: Обновите Color.kt
```kotlin
// Светлая тема
val LightUserMessageBackground = Color(0xFF1976D2)
val LightAssistantMessageBackground = Color(0xFFFFFFFF)

// Темная тема  
val DarkUserMessageBackground = Color(0xFF64B5F6)
val DarkAssistantMessageBackground = Color(0xFF424242)
```

### Шаг 2: Используйте в Theme.kt
```kotlin
val colorScheme = when {
    darkTheme -> darkColorScheme(
        primary = Purple80,
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E)
    )
    else -> lightColorScheme(...)
}
```

## 2. Копирование сообщений

### Добавьте в MessageBubble.kt:
```kotlin
@Composable
fun MessageBubble(
    message: Message,
    onLongPress: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .combinedClickable(
                onLongClick = { onLongPress(message.text) }
            ) { }
    ) {
        // существующий код
    }
}
```

### В ChatScreen.kt:
```kotlin
val context = LocalContext.current

MessageBubble(
    message = message,
    onLongPress = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    }
)
```

## 3. Форматирование Markdown

### Добавьте зависимость:
```kotlin
implementation("io.noties.markwon:core:4.6.2")
```

### Создайте компонент MarkdownText:
```kotlin
@Composable
fun MarkdownText(text: String) {
    val markwon = remember { Markwon.create(LocalContext.current) }
    
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                markwon.setMarkdown(this, text)
            }
        }
    )
}
```

## 4. Индикатор печати

### Создайте TypingIndicator.kt:
```kotlin
@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 200)
                )
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .background(Color.Gray, CircleShape)
            )
        }
    }
}
```

### Используйте в ChatScreen:
```kotlin
if (isLoading) {
    item {
        TypingIndicator()
    }
}
```

## 5. Swipe-to-delete

### Добавьте в MessageBubble:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableMessage(
    message: Message,
    onDelete: () -> Unit
) {
    val dismissState = rememberDismissState(
        confirmValueChange = {
            if (it == DismissValue.DismissedToStart) {
                onDelete()
                true
            } else false
        }
    )
    
    SwipeToDismiss(
        state = dismissState,
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red)
                    .padding(16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, "Удалить", tint = Color.White)
            }
        },
        dismissContent = { MessageBubble(message) }
    )
}
```

## 6. Анимация появления сообщений

### В ChatScreen.kt:
```kotlin
items(messages, key = { it.timestamp }) { message ->
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        MessageBubble(message)
    }
}
```

## 7. Настройки модели

### Создайте SettingsScreen.kt:
```kotlin
@Composable
fun SettingsScreen(
    onModelChange: (String) -> Unit,
    onTemperatureChange: (Float) -> Unit
) {
    var temperature by remember { mutableStateOf(1f) }
    var selectedModel by remember { mutableStateOf("claude-sonnet-4-20250514") }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Модель", style = MaterialTheme.typography.titleMedium)
        
        RadioButton(
            selected = selectedModel == "claude-sonnet-4-20250514",
            onClick = { 
                selectedModel = "claude-sonnet-4-20250514"
                onModelChange(selectedModel)
            }
        )
        Text("Claude Sonnet 4")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Температура: $temperature")
        Slider(
            value = temperature,
            onValueChange = { 
                temperature = it
                onTemperatureChange(it)
            },
            valueRange = 0f..2f
        )
    }
}
```

## 8. Сохранение в Room Database

### Добавьте зависимости:
```kotlin
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")
```

### Создайте Entity:
```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long
)
```

### Создайте DAO:
```kotlin
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>
    
    @Insert
    suspend fun insertMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
```

## 9. Image Picker для отправки изображений

### Добавьте в ChatScreen:
```kotlin
val imagePickerLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
) { uri ->
    uri?.let { viewModel.sendImage(it) }
}

IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
    Icon(Icons.Default.Image, "Прикрепить изображение")
}
```

## 10. Pull-to-Refresh

### Оберните LazyColumn:
```kotlin
val refreshState = rememberPullRefreshState(
    refreshing = isRefreshing,
    onRefresh = { viewModel.refresh() }
)

Box(modifier = Modifier.pullRefresh(refreshState)) {
    LazyColumn { /* messages */ }
    
    PullRefreshIndicator(
        refreshing = isRefreshing,
        state = refreshState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
}
```

## Полезные ссылки

- [Compose Animation](https://developer.android.com/jetpack/compose/animation)
- [Compose Gestures](https://developer.android.com/jetpack/compose/gestures)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Material 3 Components](https://m3.material.io/components)
