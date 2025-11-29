package com.example.claudechat.api

import retrofit2.Response
import retrofit2.http.*

/**
 * API интерфейс для работы с Embedding Server
 */
interface EmbeddingApiService {

    // Работа с документами
    @POST("api/documents/upload")
    suspend fun uploadDocument(@Body request: UploadDocumentRequest): Response<UploadDocumentResponse>

    @POST("api/documents/ask")
    suspend fun askQuestion(@Body request: AskQuestionRequest): Response<AskQuestionResponse>

    @GET("api/documents")
    suspend fun getDocuments(): Response<List<DocumentInfo>>

    @GET("api/documents/{id}")
    suspend fun getDocument(@Path("id") id: Int): Response<DocumentInfo>

    @DELETE("api/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: Int): Response<Unit>

    @GET("api/documents/stats")
    suspend fun getStats(): Response<DocumentStatsResponse>

    @GET("api/documents/{id}/chunks/{chunkIndex}")
    suspend fun getChunk(
        @Path("id") documentId: Int,
        @Path("chunkIndex") chunkIndex: Int
    ): Response<ChunkResponse>

    // Здоровье сервера
    @GET("api/health")
    suspend fun health(): Response<HealthResponse>
}
