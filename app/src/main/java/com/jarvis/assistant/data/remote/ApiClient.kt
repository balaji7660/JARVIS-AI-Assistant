package com.jarvis.assistant.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton factory and provider for Retrofit network client instances.
 */
object ApiClient {

    // Default Android emulator host loopback address
    const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/"

    const val PREFS_NAME = "jarvis_network_prefs"
    const val KEY_BACKEND_URL = "backend_base_url"

    @Volatile
    private var customBaseUrl: String = DEFAULT_BASE_URL

    @Volatile
    private var apiServiceInstance: ChatApiService? = null

    fun init(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BACKEND_URL, null)
        if (!saved.isNullOrBlank()) {
            setBaseUrl(saved)
        }
    }

    fun setBaseUrl(url: String, context: android.content.Context? = null) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        if (customBaseUrl != normalized) {
            customBaseUrl = normalized
            apiServiceInstance = null
        }
        context?.let {
            it.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BACKEND_URL, normalized)
                .apply()
        }
    }

    fun getBaseUrl(): String = customBaseUrl

    fun getChatApiService(): ChatApiService {
        return apiServiceInstance ?: synchronized(this) {
            apiServiceInstance ?: createChatApiService(customBaseUrl).also {
                apiServiceInstance = it
            }
        }
    }

    fun createChatApiService(baseUrl: String): ChatApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ChatApiService::class.java)
    }
}
