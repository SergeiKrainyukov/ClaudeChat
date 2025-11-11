# Hugging Face Router API - Новый формат

## Что изменилось

Hugging Face перешел на новый Router API endpoint:
- **Старый (устарел):** `https://api-inference.huggingface.co/models/{model_id}`
- **Новый:** `https://router.huggingface.co/v1/chat/completions`

## Формат нового API

### Endpoint
```
POST https://router.huggingface.co/v1/chat/completions
```

### Заголовки
```
Authorization: Bearer YOUR_HF_TOKEN
Content-Type: application/json
```

### Формат запроса (JSON)
```json
{
    "model": "meta-llama/Llama-3.2-3B-Instruct",
    "messages": [
        {
            "role": "user",
            "content": "What is artificial intelligence?"
        }
    ],
    "max_tokens": 512,
    "temperature": 0.7,
    "top_p": 0.9,
    "stream": false
}
```

### Формат ответа
```json
{
    "id": "chatcmpl-123",
    "model": "meta-llama/Llama-3.2-3B-Instruct",
    "choices": [
        {
            "index": 0,
            "message": {
                "role": "assistant",
                "content": "Artificial intelligence is..."
            },
            "finish_reason": "stop"
        }
    ],
    "usage": {
        "prompt_tokens": 10,
        "completion_tokens": 50,
        "total_tokens": 60
    }
}
```

## Реализация в приложении

### 1. Модели данных (HuggingFaceModels.kt)

```kotlin
data class HuggingFaceRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val max_tokens: Int? = null,
    val temperature: Double? = null,
    val top_p: Double? = null
)

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)

data class HuggingFaceResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
    val error: ErrorDetails? = null
)
```

### 2. API Service (HuggingFaceApiService.kt)

```kotlin
interface HuggingFaceApiService {
    @POST("v1/chat/completions")
    suspend fun generateText(
        @Body request: HuggingFaceRequest
    ): HuggingFaceResponse
}
```

### 3. API Client (HuggingFaceApiClient.kt)

```kotlin
object HuggingFaceApiClient {
    private const val BASE_URL = "https://router.huggingface.co/"

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $API_KEY")
            .header("Content-Type", "application/json")
            .build()
        chain.proceed(request)
    }
}
```

## Рекомендуемые модели для Router API

### Топ модели (январь 2025)

1. **meta-llama/Llama-3.2-3B-Instruct**
   - Современная модель от Meta
   - Хорошее качество для размера
   - Быстрая генерация

2. **microsoft/Phi-3.5-mini-instruct**
   - Компактная модель от Microsoft
   - Отличное качество/скорость
   - Хороша для инструкций

3. **Qwen/Qwen2.5-7B-Instruct**
   - Модель от Alibaba
   - Отличное качество текста
   - Поддержка многих языков

4. **google/gemma-2-2b-it**
   - Модель от Google
   - Быстрая и качественная
   - Instruction-tuned версия

### Специализированные модели

1. **deepseek-ai/DeepSeek-OCR:novita**
   - Распознавание текста на изображениях
   - Поддержка multimodal запросов

2. **mistralai/Mistral-7B-Instruct-v0.3**
   - Качественная модель от Mistral AI
   - Хорошо следует инструкциям

## Провайдеры моделей

Некоторые модели доступны через разных провайдеров. Формат:
```
organization/model-name:provider
```

Например:
- `deepseek-ai/DeepSeek-OCR:novita`
- `meta-llama/Llama-3.2-3B-Instruct:together`

## Параметры генерации

### max_tokens
- Диапазон: 1 - 4096 (зависит от модели)
- Рекомендуется: 256-512 для обычных ответов
- Больше = длиннее ответ, но медленнее

### temperature
- Диапазон: 0.0 - 2.0
- **0.0-0.3:** Детерминированные, предсказуемые ответы
- **0.7:** Сбалансированная креативность (по умолчанию)
- **1.0-2.0:** Более креативные, разнообразные ответы

### top_p (nucleus sampling)
- Диапазон: 0.0 - 1.0
- **0.9:** Хороший баланс (рекомендуется)
- **0.95:** Больше разнообразия
- **0.5:** Более консервативный выбор

### stream
- `false`: Получить полный ответ сразу
- `true`: Получать ответ частями (для UI с печатающимся текстом)

## Роли сообщений

### "user"
Сообщение от пользователя
```json
{"role": "user", "content": "Explain quantum computing"}
```

### "assistant"
Ответ модели (для контекста в multi-turn диалогах)
```json
{"role": "assistant", "content": "Quantum computing is..."}
```

### "system"
Системный промпт (инструкции для модели)
```json
{"role": "system", "content": "You are a helpful assistant"}
```

## Пример multi-turn диалога

```json
{
    "model": "meta-llama/Llama-3.2-3B-Instruct",
    "messages": [
        {
            "role": "system",
            "content": "You are a physics teacher"
        },
        {
            "role": "user",
            "content": "What is gravity?"
        },
        {
            "role": "assistant",
            "content": "Gravity is a force that attracts..."
        },
        {
            "role": "user",
            "content": "Can you give an example?"
        }
    ]
}
```

## Обработка ошибок

### Формат ошибки
```json
{
    "error": {
        "message": "Model not found",
        "type": "invalid_request_error"
    }
}
```

### Типичные ошибки

1. **Model not found**
   - Модель не существует или неправильный ID
   - Проверьте написание

2. **Unauthorized**
   - Неверный API ключ
   - Проверьте токен в ApiKeys.kt

3. **Rate limit exceeded**
   - Превышен лимит запросов
   - Подождите или обновите план

4. **Model is loading**
   - Модель "прогревается"
   - Подождите 10-30 секунд

## Лимиты бесплатного tier

- **Запросы:** ~1000 запросов/день
- **Rate limit:** 10-30 запросов/минуту (зависит от модели)
- **Токены:** Ограничение на длину ответа

## Миграция со старого API

### Было (старый API):
```kotlin
val request = HuggingFaceRequest(
    inputs = "Hello world",
    parameters = HuggingFaceParameters(
        max_new_tokens = 512,
        temperature = 0.7
    )
)
val response = apiService.generateText("gpt2", request)
val text = response[0].generatedText
```

### Стало (новый Router API):
```kotlin
val request = HuggingFaceRequest(
    model = "meta-llama/Llama-3.2-3B-Instruct",
    messages = listOf(
        ChatMessage(role = "user", content = "Hello world")
    ),
    max_tokens = 512,
    temperature = 0.7
)
val response = apiService.generateText(request)
val text = response.choices[0].message.content
```

## Преимущества нового API

1. **Стандартизация** - совместим с OpenAI API
2. **Multi-turn диалоги** - поддержка контекста
3. **Больше моделей** - доступ к новым моделям
4. **Лучшая производительность** - оптимизированный роутинг
5. **Streaming** - поддержка потокового вывода

## Проверка доступности модели

Перед использованием проверьте:
1. Откройте https://huggingface.co/models
2. Найдите модель
3. Проверьте наличие "Inference API" badge
4. Попробуйте в веб-интерфейсе

## Полезные ссылки

- [Hugging Face Router API Docs](https://huggingface.co/docs/api-inference/index)
- [Список моделей](https://huggingface.co/models?inference=warm&pipeline_tag=text-generation)
- [OpenAI-compatible API](https://huggingface.co/docs/api-inference/tasks/chat-completion)

## Обновление: январь 2025

Этот документ актуален на январь 2025. Router API является текущим стандартом Hugging Face.