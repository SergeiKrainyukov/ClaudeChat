package com.example.claudechat.api

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface HuggingFaceApiService {

    // Новый endpoint Hugging Face Router API
    // Формат: https://router.huggingface.co/v1/chat/completions
    @POST("v1/chat/completions")
    suspend fun generateText(
        @Body request: HuggingFaceRequest
    ): HuggingFaceResponse
}