package com.jarvis.assistant.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.jarvis.assistant.wakeword.WakeWordPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

enum class MicSessionState {
    IDLE,
    WAKE_DETECTING,
    COMMAND_LISTENING,
    TTS_SPEAKING,
    RELEASING,
    ERROR
}

enum class MicOwner {
    FREE,
    WAKE,
    COMMAND,
    TTS
}

enum class RecognizerStatus {
    IDLE,
    LISTENING,
    ERROR
}

enum class TtsStatus {
    IDLE,
    SPEAKING,
    ERROR
}

/**
 * Authoritative lifecycle manager for microphone ownership and audio resource arbitration.
 * Guarantees that AudioRecord (wake detector), SpeechRecognizer, and TTS never conflict.
 */
class MicrophoneSessionManager(
    private val context: Context? = null,
    private val audioFocusManager: JarvisAudioFocusManager = JarvisAudioFocusManager(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val settlingDelayMs: Long = AUDIO_RELEASE_WAIT_MS
) {

    companion object {
        private const val TAG = "MicSessionManager"
        const val AUDIO_RELEASE_WAIT_MS = 300L // 200-500ms audio resource settling delay
        const val MAX_RECOVERY_ATTEMPTS = 2
        const val TTS_WATCHDOG_TIMEOUT_MS = 12_000L // 12 seconds max watchdog for TTS
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _sessionState = MutableStateFlow(MicSessionState.IDLE)
    val sessionState: StateFlow<MicSessionState> = _sessionState.asStateFlow()

    private val _micOwner = MutableStateFlow(MicOwner.FREE)
    val micOwner: StateFlow<MicOwner> = _micOwner.asStateFlow()

    private val _recognizerStatus = MutableStateFlow(RecognizerStatus.IDLE)
    val recognizerStatus: StateFlow<RecognizerStatus> = _recognizerStatus.asStateFlow()

    private val _ttsStatus = MutableStateFlow(TtsStatus.IDLE)
    val ttsStatus: StateFlow<TtsStatus> = _ttsStatus.asStateFlow()

    private val recoveryAttempts = AtomicInteger(0)
    private var ttsWatchdogJob: Job? = null
    private var settlingJob: Job? = null

    // Hooks registered by controller / service
    var onStartWakeDetection: (() -> Unit)? = null
    var onStopWakeDetection: (() -> Unit)? = null
    var onStartCommandListening: (() -> Unit)? = null
    var onStopCommandListening: (() -> Unit)? = null
    var onStopTtsPlayback: (() -> Unit)? = null

    val audioFocusState: AudioFocusState
        get() = audioFocusManager.focusState

    @Synchronized
    fun requestWakeDetection(caller: String = "unknown"): Boolean {
        Log.d(TAG, "requestWakeDetection from $caller. Current state: ${_sessionState.value}")

        // TTS or active command listening must finish or be explicitly aborted
        if (_sessionState.value == MicSessionState.TTS_SPEAKING) {
            Log.w(TAG, "Cannot start wake detection while TTS is speaking.")
            return false
        }

        settlingJob?.cancel()
        settlingJob = null

        // Suspend any command listening
        if (_sessionState.value == MicSessionState.COMMAND_LISTENING) {
            onStopCommandListening?.invoke()
            _recognizerStatus.value = RecognizerStatus.IDLE
        }

        audioFocusManager.abandonFocus()

        _sessionState.value = MicSessionState.WAKE_DETECTING
        _micOwner.value = MicOwner.WAKE
        recoveryAttempts.set(0)

        JarvisLogger.log(JarvisLogger.Event.MIC_ACQUIRED_WAKE, "Started wake detection")
        onStartWakeDetection?.invoke()
        return true
    }

    @Synchronized
    fun requestCommandListening(caller: String = "unknown", onGranted: () -> Unit) {
        Log.d(TAG, "requestCommandListening from $caller. Current state: ${_sessionState.value}")

        // 1. Immediately transition to RELEASING to stop wake detector
        _sessionState.value = MicSessionState.RELEASING
        _micOwner.value = MicOwner.FREE

        JarvisLogger.log(JarvisLogger.Event.MIC_RELEASED_WAKE, "Releasing mic for command recognition")
        onStopWakeDetection?.invoke()

        // 2. Stop any TTS
        cancelTtsWatchdog()
        onStopTtsPlayback?.invoke()
        _ttsStatus.value = TtsStatus.IDLE

        // 3. Settling delay to ensure hardware AudioRecord is completely freed by Android audio server
        fun activateListening() {
            audioFocusManager.requestFocus(AudioFocusMode.COMMAND_LISTENING)
            _sessionState.value = MicSessionState.COMMAND_LISTENING
            _micOwner.value = MicOwner.COMMAND
            _recognizerStatus.value = RecognizerStatus.LISTENING

            JarvisLogger.log(JarvisLogger.Event.SPEECH_STARTED, "Command listening active")
            onStartCommandListening?.invoke()
            onGranted()
        }

        if (settlingDelayMs > 0L) {
            settlingJob?.cancel()
            settlingJob = coroutineScope.launch {
                delay(settlingDelayMs)
                activateListening()
            }
        } else {
            activateListening()
        }
    }

    @Synchronized
    fun requestTtsSpeaking(caller: String = "unknown", onGranted: () -> Unit) {
        Log.d(TAG, "requestTtsSpeaking from $caller. Current state: ${_sessionState.value}")

        settlingJob?.cancel()
        settlingJob = null

        // Stop wake detector
        onStopWakeDetection?.invoke()

        // Stop speech recognition
        onStopCommandListening?.invoke()
        _recognizerStatus.value = RecognizerStatus.IDLE

        // Request AudioFocus for TTS
        audioFocusManager.requestFocus(AudioFocusMode.TTS_PLAYBACK)

        _sessionState.value = MicSessionState.TTS_SPEAKING
        _micOwner.value = MicOwner.TTS
        _ttsStatus.value = TtsStatus.SPEAKING

        JarvisLogger.log(JarvisLogger.Event.TTS_STARTED, "TTS speaking initiated")

        // Start TTS Watchdog to prevent permanently getting stuck in SPEAKING
        startTtsWatchdog()

        onGranted()
    }

    @Synchronized
    fun onTtsCompleted(
        utteranceId: String,
        isContinuousSession: Boolean,
        onListenAgain: () -> Unit,
        onResumeWake: () -> Unit
    ) {
        Log.d(TAG, "onTtsCompleted for $utteranceId. isContinuous: $isContinuousSession")
        cancelTtsWatchdog()

        JarvisLogger.log(JarvisLogger.Event.TTS_DONE, "Utterance: $utteranceId")
        _ttsStatus.value = TtsStatus.IDLE

        // Release TTS audio focus
        audioFocusManager.abandonFocus()

        // Transition to RELEASING state and wait settling delay before returning to mic
        _sessionState.value = MicSessionState.RELEASING
        _micOwner.value = MicOwner.FREE

        fun completeTransition() {
            if (isContinuousSession) {
                // In continuous conversation: listen again for user command
                requestCommandListening("continuous_conversation", onListenAgain)
            } else {
                // Otherwise resume wake-word detection
                requestWakeDetection("tts_completed")
                onResumeWake()
            }
        }

        if (settlingDelayMs > 0L) {
            settlingJob?.cancel()
            settlingJob = coroutineScope.launch {
                delay(settlingDelayMs)
                completeTransition()
            }
        } else {
            completeTransition()
        }
    }

    @Synchronized
    fun onTtsError(
        utteranceId: String,
        error: String,
        isContinuousSession: Boolean,
        onListenAgain: () -> Unit,
        onResumeWake: () -> Unit
    ) {
        Log.e(TAG, "onTtsError for $utteranceId: $error")
        cancelTtsWatchdog()

        JarvisLogger.logError(JarvisLogger.Event.TTS_ERROR, "Utterance: $utteranceId, error: $error")
        _ttsStatus.value = TtsStatus.ERROR

        audioFocusManager.abandonFocus()
        _sessionState.value = MicSessionState.RELEASING
        _micOwner.value = MicOwner.FREE

        settlingJob?.cancel()
        settlingJob = coroutineScope.launch {
            delay(AUDIO_RELEASE_WAIT_MS)
            if (isContinuousSession) {
                requestCommandListening("continuous_after_tts_error", onListenAgain)
            } else {
                requestWakeDetection("tts_error")
                onResumeWake()
            }
        }
    }

    private fun startTtsWatchdog() {
        cancelTtsWatchdog()
        ttsWatchdogJob = coroutineScope.launch {
            delay(TTS_WATCHDOG_TIMEOUT_MS)
            if (_sessionState.value == MicSessionState.TTS_SPEAKING) {
                Log.w(TAG, "⚠️ TTS Watchdog triggered! Text-To-Speech did not complete in ${TTS_WATCHDOG_TIMEOUT_MS}ms. Forcing recovery.")
                onStopTtsPlayback?.invoke()
                _ttsStatus.value = TtsStatus.ERROR
                audioFocusManager.abandonFocus()
                _sessionState.value = MicSessionState.RELEASING
                _micOwner.value = MicOwner.FREE

                delay(AUDIO_RELEASE_WAIT_MS)
                requestWakeDetection("tts_watchdog_timeout")
            }
        }
    }

    private fun cancelTtsWatchdog() {
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = null
    }

    @Synchronized
    fun onSpeechRecognized(text: String) {
        _recognizerStatus.value = RecognizerStatus.IDLE
        audioFocusManager.abandonFocus()
        JarvisLogger.log(JarvisLogger.Event.SPEECH_RESULT, "Length: ${text.length}")
        WakeWordPreferences.recordSpeechResult(context)
    }

    @Synchronized
    fun onSpeechError(errorCode: Int, errorMessage: String, onRecover: () -> Unit) {
        _recognizerStatus.value = RecognizerStatus.ERROR
        audioFocusManager.abandonFocus()
        JarvisLogger.logError(JarvisLogger.Event.SPEECH_ERROR, "Code: $errorCode, $errorMessage")
        WakeWordPreferences.recordSpeechError(context, "$errorMessage (code: $errorCode)")

        val attempts = recoveryAttempts.incrementAndGet()
        WakeWordPreferences.recordRecoveryAttempt(context)
        JarvisLogger.log(JarvisLogger.Event.MIC_RECOVERY, "Attempt $attempts of $MAX_RECOVERY_ATTEMPTS")

        _sessionState.value = MicSessionState.ERROR
        _micOwner.value = MicOwner.FREE

        // Stop speech recognizer and AudioRecord
        onStopCommandListening?.invoke()
        onStopWakeDetection?.invoke()

        fun performRecovery() {
            if (attempts <= MAX_RECOVERY_ATTEMPTS) {
                Log.i(TAG, "Recovering voice pipeline (attempt $attempts)...")
                requestWakeDetection("speech_error_recovery")
                JarvisLogger.log(JarvisLogger.Event.WAKE_DETECTOR_RESTARTED, "Recovered after speech error")
                onRecover()
            } else {
                Log.w(TAG, "Voice input needs microphone recovery. Maximum recovery attempts reached.")
                _sessionState.value = MicSessionState.ERROR
                recoveryAttempts.set(0)
                requestWakeDetection("final_recovery_fallback")
                onRecover()
            }
        }

        if (settlingDelayMs > 0L) {
            settlingJob?.cancel()
            settlingJob = coroutineScope.launch {
                delay(settlingDelayMs)
                performRecovery()
            }
        } else {
            performRecovery()
        }
    }

    @Synchronized
    fun releaseAll() {
        Log.i(TAG, "releaseAll: Halting all microphone and audio activities.")
        cancelTtsWatchdog()
        settlingJob?.cancel()
        settlingJob = null

        onStopWakeDetection?.invoke()
        onStopCommandListening?.invoke()
        onStopTtsPlayback?.invoke()

        audioFocusManager.abandonFocus()

        _sessionState.value = MicSessionState.IDLE
        _micOwner.value = MicOwner.FREE
        _recognizerStatus.value = RecognizerStatus.IDLE
        _ttsStatus.value = TtsStatus.IDLE
        recoveryAttempts.set(0)
    }
}
