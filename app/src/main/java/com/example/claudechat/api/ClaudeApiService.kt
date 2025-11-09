package com.example.claudechat.api

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ClaudeApiService {

    @POST("v1/messages")
    @Headers("anthropic-version: 2023-06-01")
    suspend fun sendMessage(@Body request: ClaudeRequest): ClaudeResponse
}