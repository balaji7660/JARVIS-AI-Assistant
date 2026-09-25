package com.jarvis.assistant.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class BackendHealthState {
    AVAILABLE,
    CHECKING,
    DEGRADED,
    UNAVAILABLE,
    RECOVERING
}

enum class NetworkFailureType(val code: String, val userMessage: String, val isRetryable: Boolean) {
    NETWORK_UNAVAILABLE("NETWORK_UNAVAILABLE", "No internet connection detected, boss.", false),
    DNS_FAILURE("DNS_FAILURE", "Cannot resolve JARVIS server address.", true),
    CONNECT_TIMEOUT("CONNECT_TIMEOUT", "Connection to JARVIS server timed out.", true),
    READ_TIMEOUT("READ_TIMEOUT", "Server took too long to respond.", true),
    HTTP_401("HTTP_401", "Authentication with AI platform failed.", false),
    HTTP_403("HTTP_403", "Access to AI service was forbidden.", false),
    HTTP_429("HTTP_429", "AI service rate limit reached. Please wait a moment.", true),
    HTTP_500("HTTP_500", "JARVIS cloud server encountered an internal error.", true),
    HTTP_502("HTTP_502", "JARVIS cloud server is booting or bad gateway.", true),
    HTTP_503("HTTP_503", "JARVIS cloud intelligence is temporarily unavailable.", true),
    PUTER_TIMEOUT("PUTER_TIMEOUT", "Puter AI platform timed out. Retrying.", true),
    PUTER_ERROR("PUTER_ERROR", "Puter AI platform reported an error.", true),
    INVALID_REQUEST("INVALID_REQUEST", "I didn't catch that request, boss.", false),
    UNKNOWN_ERROR("UNKNOWN_ERROR", "JARVIS cloud intelligence is unavailable.", true)
}

data class BackendDiagnostics(
    val state: BackendHealthState = BackendHealthState.AVAILABLE,
    val lastSuccessfulRequestTimeMs: Long = 0L,
    val lastFailedRequestTimeMs: Long = 0L,
    val lastFailureType: NetworkFailureType? = null,
    val lastFailureDetail: String? = null,
    val consecutiveFailures: Int = 0,
    val retryCount: Int = 0,
    val lastHealthCheckTimeMs: Long = 0L,
    val activeProvider: String = "puter",
    val isPuterConfigured: Boolean = true,
    val isPuterAvailable: Boolean = true
)

/**
 * Manages backend connection health, failure classification, circuit breaker,
 * and automatic background recovery.
 */
object BackendHealthManager {

    private const val TAG = "BackendHealthManager"
    private const val HEALTH_CACHE_TTL_MS = 45_000L // 45 seconds cache
    private const val RECOVERY_POLL_INTERVAL_MS = 15_000L // 15 seconds poll when unavailable
    private const val MAX_CONSECUTIVE_FAILURES_BEFORE_TRIP = 3

    private val _healthState = MutableStateFlow(BackendHealthState.AVAILABLE)
    val healthState: StateFlow<BackendHealthState> = _healthState.asStateFlow()

    private val _diagnostics = MutableStateFlow(BackendDiagnostics())
    val diagnostics: StateFlow<BackendDiagnostics> = _diagnostics.asStateFlow()

    private var recoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    fun recordSuccess() {
        _diagnostics.value = _diagnostics.value.copy(
            state = BackendHealthState.AVAILABLE,
            lastSuccessfulRequestTimeMs = System.currentTimeMillis(),
            consecutiveFailures = 0,
            retryCount = 0,
            isPuterAvailable = true
        )
        _healthState.value = BackendHealthState.AVAILABLE
        stopRecoveryJob()
    }

    @Synchronized
    fun recordFailure(throwable: Throwable): NetworkFailureType {
        val failureType = classifyFailure(throwable)
        val now = System.currentTimeMillis()
        val nextConsecutive = _diagnostics.value.consecutiveFailures + 1

        val nextState = when {
            nextConsecutive >= MAX_CONSECUTIVE_FAILURES_BEFORE_TRIP -> BackendHealthState.UNAVAILABLE
            nextConsecutive >= 1 -> BackendHealthState.DEGRADED
            else -> BackendHealthState.AVAILABLE
        }

        _diagnostics.value = _diagnostics.value.copy(
            state = nextState,
            lastFailedRequestTimeMs = now,
            lastFailureType = failureType,
            lastFailureDetail = throwable.message ?: failureType.code,
            consecutiveFailures = nextConsecutive
        )
        _healthState.value = nextState

        Log.w(TAG, "Backend failure recorded: $failureType (consecutive=$nextConsecutive, state=$nextState)")

        if (nextState == BackendHealthState.UNAVAILABLE) {
            startRecoveryJob()
        }

        return failureType
    }

    fun recordRetry() {
        _diagnostics.value = _diagnostics.value.copy(
            retryCount = _diagnostics.value.retryCount + 1
        )
    }

    fun isCloudAvailable(): Boolean {
        return _healthState.value != BackendHealthState.UNAVAILABLE
    }

    fun classifyFailure(throwable: Throwable): NetworkFailureType {
        return when (throwable) {
            is UnknownHostException -> NetworkFailureType.DNS_FAILURE
            is ConnectException -> NetworkFailureType.CONNECT_TIMEOUT
            is SocketTimeoutException -> NetworkFailureType.READ_TIMEOUT
            is HttpException -> {
                when (throwable.code()) {
                    400 -> NetworkFailureType.INVALID_REQUEST
                    401 -> NetworkFailureType.HTTP_401
                    403 -> NetworkFailureType.HTTP_403
                    429 -> NetworkFailureType.HTTP_429
                    500 -> NetworkFailureType.HTTP_500
                    502 -> NetworkFailureType.HTTP_502
                    503 -> NetworkFailureType.HTTP_503
                    504 -> NetworkFailureType.PUTER_TIMEOUT
                    else -> NetworkFailureType.UNKNOWN_ERROR
                }
            }
            is IOException -> {
                val msg = throwable.message?.lowercase() ?: ""
                when {
                    msg.contains("timeout") -> NetworkFailureType.READ_TIMEOUT
                    msg.contains("connect") -> NetworkFailureType.CONNECT_TIMEOUT
                    msg.contains("puter") -> NetworkFailureType.PUTER_ERROR
                    else -> NetworkFailureType.NETWORK_UNAVAILABLE
                }
            }
            else -> NetworkFailureType.UNKNOWN_ERROR
        }
    }

    suspend fun checkHealth(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && (now - _diagnostics.value.lastHealthCheckTimeMs < HEALTH_CACHE_TTL_MS)) {
            return@withContext _healthState.value != BackendHealthState.UNAVAILABLE
        }

        _healthState.value = BackendHealthState.CHECKING
        _diagnostics.value = _diagnostics.value.copy(
            state = BackendHealthState.CHECKING,
            lastHealthCheckTimeMs = now
        )

        return@withContext try {
            val api = ApiClient.getChatApiService()
            val healthRes = api.checkHealth()
            if (healthRes.isSuccessful && healthRes.body()?.status == "ok") {
                recordSuccess()
                Log.i(TAG, "Backend health check PASSED: ${_diagnostics.value.activeProvider}")
                true
            } else {
                recordFailure(HttpException(healthRes))
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend health check failed: ${e.message}")
            recordFailure(e)
            false
        }
    }

    private fun startRecoveryJob() {
        if (recoveryJob?.isActive == true) return
        recoveryJob = scope.launch {
            Log.i(TAG, "Starting automatic backend recovery polling...")
            while (_healthState.value == BackendHealthState.UNAVAILABLE || _healthState.value == BackendHealthState.RECOVERING) {
                delay(RECOVERY_POLL_INTERVAL_MS)
                _healthState.value = BackendHealthState.RECOVERING
                val isHealthy = checkHealth(force = true)
                if (isHealthy) {
                    Log.i(TAG, "Backend automatically recovered! State is now AVAILABLE.")
                    break
                }
            }
        }
    }

    private fun stopRecoveryJob() {
        recoveryJob?.cancel()
        recoveryJob = null
    }

    fun reset() {
        stopRecoveryJob()
        _healthState.value = BackendHealthState.AVAILABLE
        _diagnostics.value = BackendDiagnostics()
    }
}
