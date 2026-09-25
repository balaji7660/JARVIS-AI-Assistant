package com.jarvis.assistant.data.remote

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Retrofit definition for the JARVIS Backend REST API.
 */
interface ChatApiService {

    @Headers("Content-Type: application/json")
    @POST("api/chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse

    @Headers("Content-Type: application/json")
    @POST("api/screen/analyze")
    suspend fun analyzeScreen(@Body request: ScreenAnalyzeRequest): ScreenAnalyzeResponse

    @Headers("Content-Type: application/json")
    @POST("api/plan")
    suspend fun createTaskPlan(@Body request: PlanRequest): PlanResponseDto

    @retrofit2.http.GET("health")
    suspend fun checkHealth(): retrofit2.Response<HealthResponse>
}

data class HealthResponse(
    val status: String? = null,
    val service: String? = null,
    val provider: String? = null
)
