package com.jarvis.assistant.wakeword

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production implementation of WakeWordDetector.
 * Coordinates the local WakeWordAudioController and the local WakeWordEngine.
 * Enforces the WakeWordState machine, debouncing, and false-trigger cooldowns.
 */
class LocalWakeWordDetector(
    private val context: Context? = null,
    val engine: WakeWordEngine = LocalAcousticWakeWordEngine(context),
    private val audioControllerFactory: ((
        onFrame: (ShortArray, Int) -> Unit,
        onError: (String) -> Unit
    ) -> WakeWordAudioController)? = null
) : WakeWordDetector {

    companion object {
        private const val TAG = "LocalWakeWordDetector"
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<WakeWordListener>()
    private val _state = MutableStateFlow(WakeWordState.DISABLED)
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    override val isListening: Boolean
        get() = _state.value == WakeWordState.LISTENING

    private var lastDetectionTimeMs: Long = 0L
    private var isCommandActive: Boolean = false
    @Volatile
    private var isSpeaking: Boolean = false

    private var audioController: WakeWordAudioController? = null

    init {
        initializeAudioController()
    }

    private fun initializeAudioController() {
        if (context == null && audioControllerFactory == null) {
            // Context is null (e.g. unit tests); controller can be injected or omitted
            return
        }

        val isHeadless = try {
            context != null && context.packageName == null
        } catch (_: Throwable) {
            true
        }
        if (isHeadless && audioControllerFactory == null) {
            return
        }

        audioController = audioControllerFactory?.invoke(
            { buffer, length -> processAudioFrame(buffer, length) },
            { error -> handleAudioError(error) }
        ) ?: context?.let { ctx ->
            WakeWordAudioController(
                context = ctx,
                onAudioFrame = { buffer, length -> processAudioFrame(buffer, length) },
                onError = { error -> handleAudioError(error) }
            )
        }
    }

    private var primaryListener: WakeWordListener? = null

    override fun setListener(listener: WakeWordListener?) {
        primaryListener = listener
    }

    fun addListener(listener: WakeWordListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: WakeWordListener) {
        listeners.remove(listener)
        if (primaryListener === listener) {
            primaryListener = null
        }
    }

    /**
     * Suppresses wake-word detection while Text-To-Speech is speaking to prevent false activations.
     */
    @Synchronized
    fun suppressDuringSpeech(speaking: Boolean) {
        isSpeaking = speaking
        if (speaking) {
            audioController?.stopCapture()
        }
    }

    /**
     * Starts the wake-word detector, opening the microphone and listening locally for "Hey JARVIS".
     */
    @Synchronized
    override fun start() {
        if (isSpeaking) {
            Log.d(TAG, "Cannot start wake-word detector while TTS is speaking.")
            return
        }

        if (_state.value == WakeWordState.LISTENING || _state.value == WakeWordState.STARTING) {
            Log.d(TAG, "Wake-word detector already active or starting.")
            return
        }

        // Validate state transition from DISABLED, ERROR, or COMMAND_LISTENING
        transitionTo(WakeWordState.STARTING)
        isCommandActive = false
        engine.reset()

        val controller = audioController
        if (controller != null) {
            val started = controller.startCapture()
            if (started) {
                transitionTo(WakeWordState.LISTENING)
                Log.i(TAG, "Wake-word detector is now LISTENING locally for \"${WakeWordConfig.WAKE_PHRASE}\".")
            } else {
                transitionTo(WakeWordState.ERROR)
                val errorMsg = "Failed to start microphone audio capture for wake-word detection."
                Log.e(TAG, errorMsg)
                for (l in listeners) {
                    try { l.onWakeWordError(errorMsg) } catch (t: Throwable) { Log.e(TAG, "Listener error", t) }
                }
            }
        } else {
            // Fallback for tests/environments without AudioRecord
            transitionTo(WakeWordState.LISTENING)
            Log.i(TAG, "Wake-word detector set to LISTENING (headless/test mode).")
        }
    }

    /**
     * Stops the wake-word detector and releases the microphone cleanly.
     */
    @Synchronized
    override fun stop() {
        if (_state.value == WakeWordState.DISABLED) return

        audioController?.stopCapture()
        engine.reset()
        transitionTo(WakeWordState.DISABLED)
        Log.i(TAG, "Wake-word detector stopped. Microphone released.")
    }

    /**
     * Called when the assistant transitions to active command listening via SpeechRecognizer.
     */
    @Synchronized
    fun notifyCommandListeningStarted() {
        isCommandActive = true
        audioController?.stopCapture()
        if (_state.value != WakeWordState.DISABLED) {
            transitionTo(WakeWordState.COMMAND_LISTENING)
        }
    }

    /**
     * Called when command processing finishes and the assistant returns to wake-word listening.
     */
    @Synchronized
    fun notifyCommandFinished() {
        isCommandActive = false
        if (_state.value == WakeWordState.COMMAND_LISTENING && !isSpeaking) {
            start()
        }
    }

    /**
     * Ingests an audio frame from the audio controller and scores it with the local engine.
     */
    fun processAudioFrame(buffer: ShortArray, length: Int) {
        if (_state.value != WakeWordState.LISTENING || isCommandActive || isSpeaking) {
            return
        }

        // False Trigger Protection: Cooldown period
        val now = System.currentTimeMillis()
        if (now - lastDetectionTimeMs < WakeWordConfig.WAKE_WORD_COOLDOWN_MS) {
            return
        }

        val result = engine.processFrame(buffer, length)
        if (result.isDetected) {
            handleDetectionSuccess(result.confidence, now)
        }
    }

    private fun handleDetectionSuccess(confidence: Float, timestamp: Long) {
        lastDetectionTimeMs = timestamp
        Log.i(TAG, "⚡ Wake word \"${WakeWordConfig.WAKE_PHRASE}\" detected locally! (Confidence: $confidence)")

        // 1. Immediately transition state
        transitionTo(WakeWordState.WAKE_DETECTED)

        // 2. Stop wake-word audio capture before SpeechRecognizer activates
        audioController?.stopCapture()

        // 3. Transition to TRANSITIONING state
        transitionTo(WakeWordState.TRANSITIONING)

        // 4. Notify listeners
        primaryListener?.let {
            try { it.onWakeWordDetected() } catch (t: Throwable) { Log.e(TAG, "Primary listener error", t) }
        }
        for (l in listeners) {
            try { l.onWakeWordDetected() } catch (t: Throwable) { Log.e(TAG, "Listener error", t) }
        }
    }

    private fun handleAudioError(error: String) {
        Log.e(TAG, "Audio error in wake-word detector: $error")
        transitionTo(WakeWordState.ERROR)
        primaryListener?.let {
            try { it.onWakeWordError(error) } catch (t: Throwable) { Log.e(TAG, "Primary listener error", t) }
        }
        for (l in listeners) {
            try { l.onWakeWordError(error) } catch (t: Throwable) { Log.e(TAG, "Listener error", t) }
        }
    }

    private fun transitionTo(newState: WakeWordState) {
        val current = _state.value
        if (current == newState) return

        // Validate state transitions to prevent invalid flows
        val isValid = when (newState) {
            WakeWordState.STARTING -> current == WakeWordState.DISABLED || current == WakeWordState.ERROR || current == WakeWordState.COMMAND_LISTENING
            WakeWordState.LISTENING -> current == WakeWordState.STARTING
            WakeWordState.WAKE_DETECTED -> current == WakeWordState.LISTENING
            WakeWordState.TRANSITIONING -> current == WakeWordState.WAKE_DETECTED
            WakeWordState.COMMAND_LISTENING -> current == WakeWordState.TRANSITIONING || current == WakeWordState.WAKE_DETECTED || current == WakeWordState.LISTENING
            WakeWordState.DISABLED -> true
            WakeWordState.ERROR -> true
        }

        if (isValid) {
            _state.value = newState
            Log.d(TAG, "WakeWordState: $current -> $newState")
        } else {
            Log.w(TAG, "Blocked invalid WakeWordState transition: $current -> $newState")
        }
    }

    /**
     * Programmatic trigger for testing and validation.
     */
    fun simulateDetection() {
        if (_state.value == WakeWordState.LISTENING && !isCommandActive) {
            val now = System.currentTimeMillis()
            if (now - lastDetectionTimeMs >= WakeWordConfig.WAKE_WORD_COOLDOWN_MS) {
                handleDetectionSuccess(0.95f, now)
            }
        }
    }

    fun simulateError(error: String) {
        handleAudioError(error)
    }

    fun release() {
        stop()
        audioController?.release()
        audioController = null
        engine.release()
        listeners.clear()
    }
}
