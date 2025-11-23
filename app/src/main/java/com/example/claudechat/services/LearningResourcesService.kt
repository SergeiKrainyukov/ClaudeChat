package com.example.claudechat.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Модель данных для ответа MCP сервера с ресурсами для обучения
 */
data class LearningResourcesResponse(
    val detectedTasks: List<DetectedTask>,
    val taskResources: Map<String, TaskResources>,
    val generalRecommendations: List<String>,
    val summary: String
)

data class DetectedTask(
    val id: String,
    val description: String,
    val category: String
)

data class TaskResources(
    val courses: List<Course>,
    val articles: List<Article>,
    val books: List<Book>,
    val practiceIdeas: List<String>
)

data class Course(
    val title: String,
    val platform: String,
    val url: String,
    val duration: String?,
    val level: String?
)

data class Article(
    val title: String,
    val url: String,
    val source: String?
)

data class Book(
    val title: String,
    val author: String?,
    val description: String?
)

/**
 * Сервис для получения рекомендаций по обучению через MCP сервер
 */
class LearningResourcesService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://10.0.2.2:3001" // Для эмулятора Android

    /**
     * Получает рекомендации по обучению на основе текста плана задач
     * @param planText Текст плана реализации задач
     * @return Форматированный текст с рекомендациями для добавления в PDF
     */
    suspend fun getLearningRecommendations(planText: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Подготавливаем запрос к MCP серверу
                val jsonBody = JSONObject().apply {
                    put("toolId", "find_learning_resources")
                    put("input", planText)
                }

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/api/tools/call")
                    .post(requestBody)
                    .build()

                // Выполняем запрос
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Ошибка MCP сервера: ${response.code}")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Пустой ответ от сервера"))

                // Парсим ответ
                val learningResponse = parseResponse(responseBody)

                // Форматируем рекомендации для PDF
                val formattedRecommendations = formatRecommendations(learningResponse)

                Result.success(formattedRecommendations)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Парсит JSON ответ от MCP сервера
     */
    private fun parseResponse(jsonString: String): LearningResourcesResponse {
        val json = JSONObject(jsonString)

        // Парсим обнаруженные задачи
        val detectedTasks = mutableListOf<DetectedTask>()
        val tasksArray = json.optJSONArray("detectedTasks")
        if (tasksArray != null) {
            for (i in 0 until tasksArray.length()) {
                val taskJson = tasksArray.getJSONObject(i)
                detectedTasks.add(
                    DetectedTask(
                        id = taskJson.optString("id", ""),
                        description = taskJson.optString("description", ""),
                        category = taskJson.optString("category", "")
                    )
                )
            }
        }

        // Парсим ресурсы для задач
        val taskResources = mutableMapOf<String, TaskResources>()
        val resourcesJson = json.optJSONObject("taskResources")
        if (resourcesJson != null) {
            resourcesJson.keys().forEach { taskId ->
                val taskResourceJson = resourcesJson.getJSONObject(taskId)
                taskResources[taskId] = parseTaskResources(taskResourceJson)
            }
        }

        // Парсим общие рекомендации
        val generalRecommendations = mutableListOf<String>()
        val recommendationsArray = json.optJSONArray("generalRecommendations")
        if (recommendationsArray != null) {
            for (i in 0 until recommendationsArray.length()) {
                generalRecommendations.add(recommendationsArray.getString(i))
            }
        }

        // Парсим резюме
        val summary = json.optString("summary", "")

        return LearningResourcesResponse(
            detectedTasks = detectedTasks,
            taskResources = taskResources,
            generalRecommendations = generalRecommendations,
            summary = summary
        )
    }

    /**
     * Парсит ресурсы для конкретной задачи
     */
    private fun parseTaskResources(json: JSONObject): TaskResources {
        // Парсим курсы
        val courses = mutableListOf<Course>()
        val coursesArray = json.optJSONArray("courses")
        if (coursesArray != null) {
            for (i in 0 until coursesArray.length()) {
                val courseJson = coursesArray.getJSONObject(i)
                courses.add(
                    Course(
                        title = courseJson.optString("title", ""),
                        platform = courseJson.optString("platform", ""),
                        url = courseJson.optString("url", ""),
                        duration = courseJson.optString("duration"),
                        level = courseJson.optString("level")
                    )
                )
            }
        }

        // Парсим статьи
        val articles = mutableListOf<Article>()
        val articlesArray = json.optJSONArray("articles")
        if (articlesArray != null) {
            for (i in 0 until articlesArray.length()) {
                val articleJson = articlesArray.getJSONObject(i)
                articles.add(
                    Article(
                        title = articleJson.optString("title", ""),
                        url = articleJson.optString("url", ""),
                        source = articleJson.optString("source")
                    )
                )
            }
        }

        // Парсим книги
        val books = mutableListOf<Book>()
        val booksArray = json.optJSONArray("books")
        if (booksArray != null) {
            for (i in 0 until booksArray.length()) {
                val bookJson = booksArray.getJSONObject(i)
                books.add(
                    Book(
                        title = bookJson.optString("title", ""),
                        author = bookJson.optString("author"),
                        description = bookJson.optString("description")
                    )
                )
            }
        }

        // Парсим идеи для практики
        val practiceIdeas = mutableListOf<String>()
        val practiceArray = json.optJSONArray("practiceIdeas")
        if (practiceArray != null) {
            for (i in 0 until practiceArray.length()) {
                practiceIdeas.add(practiceArray.getString(i))
            }
        }

        return TaskResources(
            courses = courses,
            articles = articles,
            books = books,
            practiceIdeas = practiceIdeas
        )
    }

    /**
     * Форматирует рекомендации для красивого отображения в PDF
     */
    private fun formatRecommendations(response: LearningResourcesResponse): String {
        val builder = StringBuilder()

        builder.append("\n\n")
        builder.append("# РЕКОМЕНДАЦИИ ПО ОБУЧЕНИЮ\n\n")

        // Резюме
        if (response.summary.isNotBlank()) {
            builder.append("## Резюме анализа\n")
            builder.append("${response.summary}\n\n")
        }

        // Рекомендации по каждой задаче
        if (response.detectedTasks.isNotEmpty()) {
            builder.append("## Обучающие ресурсы по задачам\n\n")

            response.detectedTasks.forEach { task ->
                val resources = response.taskResources[task.id]
                if (resources != null) {
                    builder.append("### ${task.description}\n")
                    builder.append("Категория: ${task.category}\n\n")

                    // Курсы
                    if (resources.courses.isNotEmpty()) {
                        builder.append("#### Рекомендуемые курсы:\n")
                        resources.courses.forEach { course ->
                            builder.append("• ${course.title}")
                            if (course.platform.isNotBlank()) {
                                builder.append(" (${course.platform})")
                            }
                            if (course.level != null && course.level.isNotBlank()) {
                                builder.append(" - Уровень: ${course.level}")
                            }
                            if (course.duration != null && course.duration.isNotBlank()) {
                                builder.append(", ${course.duration}")
                            }
                            builder.append("\n")
                            if (course.url.isNotBlank()) {
                                builder.append("  ${course.url}\n")
                            }
                        }
                        builder.append("\n")
                    }

                    // Статьи
                    if (resources.articles.isNotEmpty()) {
                        builder.append("#### Полезные статьи:\n")
                        resources.articles.forEach { article ->
                            builder.append("• ${article.title}")
                            if (article.source != null && article.source.isNotBlank()) {
                                builder.append(" (${article.source})")
                            }
                            builder.append("\n")
                            if (article.url.isNotBlank()) {
                                builder.append("  ${article.url}\n")
                            }
                        }
                        builder.append("\n")
                    }

                    // Книги
                    if (resources.books.isNotEmpty()) {
                        builder.append("#### Рекомендуемые книги:\n")
                        resources.books.forEach { book ->
                            builder.append("• ${book.title}")
                            if (book.author != null && book.author.isNotBlank()) {
                                builder.append(" - ${book.author}")
                            }
                            builder.append("\n")
                            if (book.description != null && book.description.isNotBlank()) {
                                builder.append("  ${book.description}\n")
                            }
                        }
                        builder.append("\n")
                    }

                    // Идеи для практики
                    if (resources.practiceIdeas.isNotEmpty()) {
                        builder.append("#### Идеи для практики:\n")
                        resources.practiceIdeas.forEach { idea ->
                            builder.append("• $idea\n")
                        }
                        builder.append("\n")
                    }
                }
            }
        }

        // Общие рекомендации
        if (response.generalRecommendations.isNotEmpty()) {
            builder.append("## Общие рекомендации\n\n")
            response.generalRecommendations.forEach { recommendation ->
                builder.append("• $recommendation\n")
            }
            builder.append("\n")
        }

        return builder.toString()
    }
}