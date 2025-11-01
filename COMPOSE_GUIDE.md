# Jetpack Compose в проекте Claude Chat

## Основные компоненты

### 1. ChatScreen (`ui/screens/ChatScreen.kt`)
Главный экран приложения, содержит:
- `Scaffold` - каркас экрана с топ-баром
- `TopAppBar` - верхняя панель с заголовком и кнопкой очистки
- `LazyColumn` - эффективный список сообщений
- `TextField` - поле ввода сообщения
- `FloatingActionButton` - кнопка отправки

### 2. MessageBubble (`ui/components/MessageBubble.kt`)
Компонент для отображения одного сообщения:
- Разный дизайн для пользователя и ассистента
- Автоматическое выравнивание (пользователь справа, ассистент слева)
- Закругленные углы с разными радиусами
- Ограничение максимальной ширины

### 3. Тема (`ui/theme/`)
- `Color.kt` - палитра цветов приложения
- `Theme.kt` - настройка светлой/темной темы
- `Type.kt` - типографика (размеры и стили текста)

## Особенности реализации

### Реактивность
```kotlin
val messages by viewModel.messages.observeAsState(emptyList())
```
UI автоматически обновляется при изменении данных во ViewModel.

### Автоскролл
```kotlin
LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
        listState.animateScrollToItem(messages.size - 1)
    }
}
```
При добавлении нового сообщения список автоматически прокручивается вниз.

### Состояние загрузки
```kotlin
if (isLoading) {
    item {
        CircularProgressIndicator()
    }
}
```
Индикатор загрузки отображается прямо в списке сообщений.

## Preview (Предпросмотр)

В файле `MessageBubblePreview.kt` находятся аннотации `@Preview`, которые позволяют:
- Просматривать компоненты в Android Studio без запуска приложения
- Тестировать разные варианты UI (длинные сообщения, разные роли)
- Ускорить разработку интерфейса

## Преимущества Compose в этом проекте

1. **Меньше кода**: Нет XML-файлов, все UI описано в Kotlin
2. **Читаемость**: Структура UI понятна из кода
3. **Реактивность**: UI обновляется автоматически
4. **Производительность**: LazyColumn оптимизирован для больших списков
5. **Гибкость**: Легко изменять и расширять компоненты

## Как добавить новые компоненты

### Пример: Кнопка копирования сообщения

```kotlin
@Composable
fun MessageBubble(message: Message, onCopy: (String) -> Unit) {
    Row {
        // Существующий код пузырька
        
        IconButton(onClick = { onCopy(message.text) }) {
            Icon(Icons.Default.ContentCopy, "Копировать")
        }
    }
}
```

### Пример: Индикатор печати

```kotlin
@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { 
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.Gray, CircleShape)
            )
        }
    }
}
```

## Ресурсы для изучения

- [Официальная документация Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Compose Samples](https://github.com/android/compose-samples)
- [Material 3 для Compose](https://m3.material.io/)
