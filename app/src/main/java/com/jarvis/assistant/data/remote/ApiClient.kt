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

    // Production Render HTTPS backend deployment
    const val PRODUCTION_BASE_URL = "https://jarvis-ai-assistant-qvzy.onrender.com/"
    // Local development loopback for Android emulator / local dev
    const val LOCAL_DEV_BASE_URL = "http://10.0.2.2:3000/"

    // Default production endpoint (always ends with /)
    const val DEFAULT_BASE_URL = PRODUCTION_BASE_URL
    const val CLOUD_BASE_URL = PRODUCTION_BASE_URL

    const val PREFS_NAME = "jarvis_network_prefs"
    const val KEY_BACKEND_URL = "backend_base_url"

    @Volatile
    private var customBaseUrl: String = DEFAULT_BASE_URL

    @Volatile
    private var apiServiceInstance: ChatApiService? = null

    fun init(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_BACKEND_URL, null)
        // Automatically migrate legacy localhost/127.0.0.1 URLs to the production HTTPS cloud backend
        if (!saved.isNullOrBlank() && !saved.contains("127.0.0.1") && !saved.contains("localhost")) {
            setBaseUrl(saved)
        } else {
            setBaseUrl(DEFAULT_BASE_URL, context)
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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(okhttp3.ConnectionPool(5, 30L, TimeUnit.SECONDS))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ChatApiService::class.java)
    }
}
