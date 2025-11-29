package com.example.claudechat.api

import com.google.gson.annotations.SerializedName

/**
 * Модели для работы с Embedding Server
 */

// Запрос на загрузку документа
data class UploadDocumentRequest(
    @SerializedName("fileName") val fileName: String,
    @SerializedName("content") val content: String
)

// Ответ на загрузку документа
data class UploadDocumentResponse(
    @SerializedName("documentId") val documentId: Int,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("fileSize") val fileSize: Int,
    @SerializedName("chunksCreated") val chunksCreated: Int,
    @SerializedName("createdAt") val createdAt: String
)

// Запрос на вопрос с RAG
data class AskQuestionRequest(
    @SerializedName("question") val question: String,
    @SerializedName("topK") val topK: Int = 3
)

// Источник для ответа
data class Source(
    @SerializedName("documentId") val documentId: Int,
    @SerializedName("documentName") val documentName: String,
    @SerializedName("chunkIndex") val chunkIndex: Int,
    @SerializedName("text") val text: String,
    @SerializedName("similarity") val similarity: Double,
    @SerializedName("similarityPercent") val similarityPercent: String,
    @SerializedName("link") val link: String
)

// Ответ на вопрос с RAG
data class AskQuestionResponse(
    @SerializedName("question") val question: String,
    @SerializedName("answer") val answer: String,
    @SerializedName("sources") val sources: List<Source>
)

// Информация о документе
data class DocumentInfo(
    @SerializedName("id") val id: Int,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("fileSize") val fileSize: Int,
    @SerializedName("chunksCount") val chunksCount: Int,
    @SerializedName("createdAt") val createdAt: String
)

// Статистика документов
data class DocumentStatsResponse(
    @SerializedName("totalDocuments") val totalDocuments: Int,
    @SerializedName("totalChunks") val totalChunks: Int,
    @SerializedName("totalSize") val totalSize: Long
)

// Чанк документа
data class ChunkResponse(
    @SerializedName("documentId") val documentId: Int,
    @SerializedName("documentName") val documentName: String,
    @SerializedName("chunkIndex") val chunkIndex: Int,
    @SerializedName("text") val text: String,
    @SerializedName("embedding") val embedding: List<Double>?
)

// Здоровье сервера
data class HealthResponse(
    @SerializedName("status") val status: String,
    @SerializedName("ollama") val ollama: Boolean,
    @SerializedName("database") val database: Boolean
)
