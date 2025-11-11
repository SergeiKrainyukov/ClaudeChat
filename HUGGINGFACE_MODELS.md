# Рекомендуемые модели Hugging Face для сравнения

## Обновление 2024-2025

Hugging Face обновил свой Inference API. Теперь некоторые старые короткие ID моделей (типа `gpt2`, `distilgpt2`) могут не работать. Используйте полные ID моделей с указанием организации.

## Модели по умолчанию в приложении

- **Модель 1:** `google/flan-t5-small` - легкая модель T5 от Google
- **Модель 2:** `facebook/opt-125m` - OPT модель от Meta (125M параметров)

## Проверенные рабочие модели (2024-2025)

### Легкие и быстрые модели (рекомендуется для начала)

1. **google/flan-t5-small** (60M параметров)
   - Отлично подходит для вопросов и ответов
   - Быстрая генерация
   - Хорошо работает с инструкциями

2. **facebook/opt-125m** (125M параметров)
   - Базовая генерация текста
   - Быстрая
   - Хорошее качество для своего размера

3. **microsoft/DialoGPT-small** (117M параметров)
   - Специализирована на диалогах
   - Хорошо отвечает на простые вопросы

4. **distilbert/distilgpt2** (82M параметров)
   - Облегченная GPT-2
   - Очень быстрая
   - Подходит для тестирования

### Средние модели (лучшее качество, но медленнее)

1. **facebook/opt-350m** (350M параметров)
   - Лучшее качество от OPT
   - Разумная скорость

2. **google/flan-t5-base** (250M параметров)
   - Более качественные ответы чем small
   - Хорошо понимает инструкции

3. **microsoft/DialoGPT-medium** (345M параметров)
   - Лучше для диалогов
   - Более естественные ответы

### Специализированные модели

1. **facebook/bart-large-cnn**
   - Суммаризация текста
   - Отлично для сжатия длинных текстов

2. **t5-small / t5-base**
   - Универсальные модели
   - Хороши для перефразирования

## Как изменить модели

### Вариант 1: В коде (постоянно)

Откройте `ModelComparisonViewModel.kt` и измените:

```kotlin
private var model1Id = "google/flan-t5-small"
private var model2Id = "facebook/opt-125m"
```

На например:

```kotlin
private var model1Id = "microsoft/DialoGPT-medium"
private var model2Id = "facebook/opt-350m"
```

### Вариант 2: Добавить выбор в UI (будущая функция)

Можно добавить dropdown меню в экран сравнения для динамического выбора.

## Типы запросов для разных моделей

### Для FLAN-T5 (google/flan-t5-*)
Работает лучше с инструкциями:
```
Translate to Russian: Hello world
Summarize: [long text]
Answer the question: What is AI?
```

### Для OPT (facebook/opt-*)
Продолжение текста:
```
Once upon a time
The weather today is
```

### Для DialoGPT (microsoft/DialoGPT-*)
Вопросы и диалог:
```
Hello, how are you?
What's your favorite color?
Tell me a joke
```

## Частые проблемы и решения

### Ошибка 410 Gone

Если вы видите HTML с ошибкой 410 и сообщением о том, что `api-inference.huggingface.co` больше не поддерживается:

1. **Проверьте ID модели** - используйте полный ID с организацией (`org/model-name`)
2. **Убедитесь что модель существует** - проверьте на https://huggingface.co/models
3. **Проверьте API ключ** - должен быть валидным токеном

### Модель загружается очень долго

Некоторые модели "засыпают" и требуют 20-30 секунд для первой загрузки. Это нормально. Повторные запросы будут быстрее.

### Ошибка "Model is currently loading"

Подождите 10-30 секунд и повторите запрос.

### Некорректный ответ или только эхо промпта

Некоторые модели возвращают промпт вместе с ответом. Код уже содержит логику очистки:

```kotlin
val cleanText = if (generatedText.startsWith(prompt)) {
    generatedText.substring(prompt.length).trim()
} else {
    generatedText.trim()
}
```

## Проверка доступности модели

Перед использованием проверьте модель на сайте:
1. Перейдите на https://huggingface.co/models
2. Найдите нужную модель
3. Убедитесь что есть "Hosted inference API" виджет
4. Попробуйте ввести текст в виджете
5. Если работает - можно использовать в приложении

## Рекомендуемые пары для сравнения

### Скорость vs Качество
```
Модель 1: distilbert/distilgpt2 (быстрая)
Модель 2: facebook/opt-350m (качественная)
```

### Разные архитектуры
```
Модель 1: google/flan-t5-small (T5, encoder-decoder)
Модель 2: facebook/opt-125m (GPT-style, decoder-only)
```

### Диалоговые модели
```
Модель 1: microsoft/DialoGPT-small
Модель 2: microsoft/DialoGPT-medium
```

## Лимиты и ограничения

### Бесплатный tier Hugging Face:
- Примерно 30,000 символов входа в месяц
- Rate limit: обычно 1-2 запроса в секунду
- Модели могут "засыпать" если не используются

### Платные планы:
Для production использования рекомендуется:
- Hugging Face Pro ($9/месяц)
- Inference Endpoints (от $0.06/час)
- Dedicated endpoints для критичных приложений

## Альтернативные endpoint'ы

Если `api-inference.huggingface.co` не работает, можно попробовать:
- `https://api-inference.huggingface.co/models/` (старый формат)
- Dedicated Inference Endpoints (платные)

## Поиск моделей

Найти подходящие модели можно на:
- https://huggingface.co/models?pipeline_tag=text-generation
- https://huggingface.co/models?pipeline_tag=text2text-generation
- Фильтр: "Inference API"

## Обновление списка моделей

Этот файл актуален на январь 2025. Список доступных моделей постоянно обновляется.
Проверяйте актуальность на официальном сайте Hugging Face.