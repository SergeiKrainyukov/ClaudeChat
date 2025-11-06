package com.example.claudechat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.claudechat.model.*
import com.example.claudechat.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для управления чатом достижения целей
 */
class GoalViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    // Состояние чата
    private val _uiState = MutableStateFlow<GoalUiState>(GoalUiState.Initial)
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    // Текущая цель
    private var currentGoal: Goal? = null

    // История сообщений для контекста
    private val conversationHistory = mutableListOf<GoalChatMessage>()

    // Счетчик заданных вопросов
    private var questionsAsked = 0
    private val maxQuestions = 8 // Максимум вопросов, которые AI задаст

    /**
     * Начать диалог с AI-ассистентом
     */
    fun startGoalConversation(goalType: GoalType, goalTitle: String) {
        currentGoal = Goal(
            type = goalType,
            title = goalTitle
        )

        conversationHistory.clear()
        questionsAsked = 0

        // Формируем системный промпт и первое сообщение от AI
        viewModelScope.launch {
            _uiState.value = GoalUiState.Loading

            val systemPrompt = buildSystemPrompt(goalType, goalTitle)

            try {
                // Получаем первый вопрос от AI
                val response = chatRepository.sendMessage(systemPrompt)

                response.onSuccess { result ->
                    val aiMessage = GoalChatMessage(
                        text = result.text,
                        isFromUser = false
                    )
                    conversationHistory.add(aiMessage)

                    _uiState.value = GoalUiState.Chatting(
                        messages = conversationHistory.toList(),
                        isLoading = false
                    )
                }

                response.onFailure { error ->
                    _uiState.value = GoalUiState.Error(
                        message = "Ошибка при начале диалога: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = GoalUiState.Error(
                    message = "Ошибка: ${e.message}"
                )
            }
        }
    }

    /**
     * Отправить сообщение пользователя
     */
    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        // Добавляем сообщение пользователя
        val userMsg = GoalChatMessage(
            text = userMessage,
            isFromUser = true
        )
        conversationHistory.add(userMsg)

        _uiState.value = GoalUiState.Chatting(
            messages = conversationHistory.toList(),
            isLoading = true
        )

        questionsAsked++

        viewModelScope.launch {
            try {
                // Формируем промпт с историей разговора
                val prompt = buildConversationPrompt(userMessage)

                val response = chatRepository.sendMessage(prompt)

                response.onSuccess { result ->
                    val aiMessage = GoalChatMessage(
                        text = result.text,
                        isFromUser = false
                    )
                    conversationHistory.add(aiMessage)

                    // Проверяем, достаточно ли информации собрано
                    if (questionsAsked >= maxQuestions || shouldGenerateAlgorithm(result.text)) {
                        // Генерируем алгоритм
                        generateAlgorithm()
                    } else {
                        _uiState.value = GoalUiState.Chatting(
                            messages = conversationHistory.toList(),
                            isLoading = false
                        )
                    }
                }

                response.onFailure { error ->
                    _uiState.value = GoalUiState.Error(
                        message = "Ошибка: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = GoalUiState.Error(
                    message = "Ошибка: ${e.message}"
                )
            }
        }
    }

    /**
     * Проверить, нужно ли генерировать алгоритм
     */
    private fun shouldGenerateAlgorithm(aiResponse: String): Boolean {
        // Ищем ключевые фразы, которые указывают на готовность к генерации плана
        val keywords = listOf(
            "создам план",
            "составлю алгоритм",
            "вот план",
            "приступим к плану",
            "достаточно информации"
        )
        return keywords.any { aiResponse.lowercase().contains(it) }
    }

    /**
     * Построить системный промпт для начала диалога
     */
    private fun buildSystemPrompt(goalType: GoalType, goalTitle: String): String {
        val goalTypeDesc = getGoalTypeDescription(goalType)

        return """
Ты - AI-ассистент, который помогает людям достигать их целей. Твоя задача - задать пользователю 2-3 уточняющих вопросов о его цели, чтобы понять все детали и затем создать подробный план достижения.

Цель пользователя: $goalTitle
Категория: $goalTypeDesc

ВАЖНО:
1. Задавай по одному вопросу за раз
2. Вопросы должны быть конкретными и помогать понять детали цели
3. Используй информацию из предыдущих ответов для формулирования следующих вопросов
4. После 2-3 вопросов скажи что-то вроде "Отлично! Теперь у меня достаточно информации. Создам план..." и я автоматически сгенерирую алгоритм
5. Будь дружелюбным и мотивирующим

Вопросы должны касаться:
${getQuestionTopics(goalType)}

Начни с первого вопроса прямо сейчас. Не нужно приветствий, сразу задай первый конкретный вопрос.
        """.trimIndent()
    }

    /**
     * Получить темы для вопросов в зависимости от типа цели
     */
    private fun getQuestionTopics(goalType: GoalType): String {
        return when (goalType) {
            GoalType.GAME_DEVELOPMENT -> """
- Жанр игры и основной геймплей
- Целевые платформы (PC, мобильные, консоли)
- Стиль графики (2D/3D)
- Опыт в разработке и навыки
- Масштаб проекта и сроки
- Бюджет и ресурсы
- Планы по монетизации
            """.trimIndent()

            GoalType.MOBILE_APP -> """
- Тип приложения и основной функционал
- Целевая платформа (Android, iOS, обе)
- Необходимость серверной части
- Опыт в разработке
- Сроки разработки
- Бюджет
- Монетизация
            """.trimIndent()

            GoalType.WEB_APP -> """
- Тип веб-приложения
- Основной функционал
- Ожидаемое количество пользователей
- Технологический стек (если знает)
- Опыт в разработке
- Сроки
- Хостинг и бюджет
            """.trimIndent()

            GoalType.LEARNING -> """
- Что конкретно хочет изучить
- Текущий уровень знаний
- Цель обучения (работа, проект, хобби)
- Доступное время для обучения
- Предпочитаемый стиль обучения
- Дедлайн
            """.trimIndent()

            GoalType.BUSINESS -> """
- Тип бизнеса
- Текущая стадия (идея, MVP, запущен)
- Целевая аудитория
- Бюджет
- Команда
- Сроки запуска
            """.trimIndent()

            GoalType.CREATIVE_PROJECT -> """
- Тип творческого проекта
- Цель проекта
- Опыт в этой области
- Масштаб и сроки
- Необходимые ресурсы
            """.trimIndent()

            GoalType.OTHER -> """
- Детали цели
- Причины важности этой цели
- Имеющийся опыт и навыки
- Доступные ресурсы
- Сроки
- Возможные препятствия
            """.trimIndent()
        }
    }

    /**
     * Построить промпт с историей разговора
     */
    private fun buildConversationPrompt(userMessage: String): String {
        val history = conversationHistory.takeLast(10).joinToString("\n") { msg ->
            if (msg.isFromUser) "Пользователь: ${msg.text}" else "Ассистент: ${msg.text}"
        }

        return """
$history
Пользователь: $userMessage

Продолжи диалог. Задай следующий уточняющий вопрос или, если уже задал достаточно вопросов (6-8), скажи что готов создать план и закончи фразой типа "Создам план действий для достижения твоей цели!".
        """.trimIndent()
    }

    /**
     * Генерировать алгоритм достижения цели
     */
    private fun generateAlgorithm() {
        viewModelScope.launch {
            _uiState.value = GoalUiState.GeneratingAlgorithm

            try {
                val goal = currentGoal ?: return@launch

                // Формируем промпт для генерации алгоритма на основе всего диалога
                val prompt = buildAlgorithmPrompt()

                val response = chatRepository.sendMessage(prompt)

                response.onSuccess { result ->
                    val algorithm = parseAlgorithmFromResponse(result.text, goal.id)

                    _uiState.value = GoalUiState.AlgorithmGenerated(
                        goal = goal,
                        algorithm = algorithm,
                        chatHistory = conversationHistory.toList()
                    )
                }

                response.onFailure { error ->
                    _uiState.value = GoalUiState.Error(
                        message = "Ошибка при генерации алгоритма: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = GoalUiState.Error(
                    message = "Ошибка: ${e.message}"
                )
            }
        }
    }

    /**
     * Построить промпт для генерации алгоритма
     */
    private fun buildAlgorithmPrompt(): String {
        val goal = currentGoal ?: return ""

        val dialogSummary = conversationHistory.joinToString("\n") { msg ->
            if (msg.isFromUser) "Пользователь: ${msg.text}" else "Ассистент: ${msg.text}"
        }

        return """
На основе следующего диалога создай подробный пошаговый алгоритм достижения цели.

Цель: ${goal.title}
Категория: ${getGoalTypeDescription(goal.type)}

Диалог:
$dialogSummary

Создай подробный план в следующем формате:

## Общая информация
- Оценка времени: [общее время]
- Сложность: [Начальный/Средний/Сложный/Экспертный]
- Предварительные требования: [список]

## Шаги

### Шаг 1: [Название]
**Описание:** [Подробное описание]
**Время:** [Оценка]
**Ресурсы:** [Список ресурсов]
**Подшаги:**
1. [Подшаг 1]
2. [Подшаг 2]

[Повтори для всех шагов]

## Советы
- [Совет 1]
- [Совет 2]

Будь максимально конкретным и практичным.
        """.trimIndent()
    }

    /**
     * Парсинг алгоритма из ответа Claude
     */
    private fun parseAlgorithmFromResponse(response: String, goalId: String): GoalAlgorithm {
        val lines = response.lines()
        val steps = mutableListOf<AlgorithmStep>()
        val prerequisites = mutableListOf<String>()
        val tips = mutableListOf<String>()
        var totalTime = "Не указано"
        var difficulty = "Средний"

        var currentStep: AlgorithmStep? = null
        var stepNumber = 0
        var parsingMode = ""

        for (line in lines) {
            when {
                line.startsWith("- Оценка времени:") -> {
                    totalTime = line.substringAfter(":").trim()
                }
                line.startsWith("- Сложность:") -> {
                    difficulty = line.substringAfter(":").trim()
                }
                line.startsWith("- Предварительные требования:") -> {
                    parsingMode = "prerequisites"
                }
                line.startsWith("### Шаг") -> {
                    currentStep?.let { steps.add(it) }

                    stepNumber++
                    val title = line.substringAfter(":").trim()
                    currentStep = AlgorithmStep(
                        stepNumber = stepNumber,
                        title = title,
                        description = "",
                        estimatedTime = "",
                        resources = emptyList(),
                        subSteps = emptyList()
                    )
                    parsingMode = "step"
                }
                line.startsWith("**Описание:**") && currentStep != null -> {
                    currentStep = currentStep.copy(
                        description = line.substringAfter(":").trim()
                    )
                }
                line.startsWith("**Время:**") && currentStep != null -> {
                    currentStep = currentStep.copy(
                        estimatedTime = line.substringAfter(":").trim()
                    )
                }
                line.startsWith("## Советы") -> {
                    currentStep?.let { steps.add(it) }
                    currentStep = null
                    parsingMode = "tips"
                }
                line.startsWith("- ") && parsingMode == "tips" -> {
                    tips.add(line.substring(2).trim())
                }
                line.startsWith("- ") && parsingMode == "prerequisites" -> {
                    prerequisites.add(line.substring(2).trim())
                }
            }
        }

        currentStep?.let { steps.add(it) }

        return GoalAlgorithm(
            goalId = goalId,
            steps = steps.ifEmpty {
                listOf(
                    AlgorithmStep(
                        stepNumber = 1,
                        title = "Алгоритм достижения цели",
                        description = response,
                        estimatedTime = totalTime
                    )
                )
            },
            totalEstimatedTime = totalTime,
            difficulty = difficulty,
            prerequisites = prerequisites,
            tips = tips
        )
    }

    /**
     * Получить описание типа цели
     */
    private fun getGoalTypeDescription(goalType: GoalType): String {
        return when (goalType) {
            GoalType.GAME_DEVELOPMENT -> "Разработка игры"
            GoalType.MOBILE_APP -> "Мобильное приложение"
            GoalType.WEB_APP -> "Веб-приложение"
            GoalType.LEARNING -> "Обучение"
            GoalType.BUSINESS -> "Бизнес"
            GoalType.CREATIVE_PROJECT -> "Творческий проект"
            GoalType.OTHER -> "Другое"
        }
    }

    /**
     * Принудительно сгенерировать алгоритм
     */
    fun forceGenerateAlgorithm() {
        generateAlgorithm()
    }

    /**
     * Сбросить состояние
     */
    fun reset() {
        _uiState.value = GoalUiState.Initial
        currentGoal = null
        conversationHistory.clear()
        questionsAsked = 0
    }
}

/**
 * Состояния UI для экрана достижения целей
 */
sealed class GoalUiState {
    object Initial : GoalUiState()
    object Loading : GoalUiState()

    data class Chatting(
        val messages: List<GoalChatMessage>,
        val isLoading: Boolean
    ) : GoalUiState()

    object GeneratingAlgorithm : GoalUiState()

    data class AlgorithmGenerated(
        val goal: Goal,
        val algorithm: GoalAlgorithm,
        val chatHistory: List<GoalChatMessage>
    ) : GoalUiState()

    data class Error(
        val message: String
    ) : GoalUiState()
}