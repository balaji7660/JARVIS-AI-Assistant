package com.jarvis.assistant.wakeword

/**
 * Result data class produced when processing an audio frame.
 */
data class WakeWordResult(
    val isDetected: Boolean,
    val confidence: Float = 0f,
    val phrase: String = WakeWordConfig.WAKE_PHRASE,
    val message: String? = null
)

/**
 * Modular engine abstraction decoupling audio processing and wake-word scoring
 * from higher-level controllers and UI viewmodels.
 */
interface WakeWordEngine {
    /** Human-readable engine identifier. */
    val name: String

    /** True if the engine and its local acoustic model are ready to process audio. */
    val isInitialized: Boolean

    /**
     * Initializes the engine, loading local model assets if necessary.
     * @return true if initialization succeeded, false otherwise.
     */
    fun initialize(): Boolean

    /**
     * Ingests a frame of 16-bit PCM mono audio samples and computes wake-word likelihood.
     * @param audioBuffer Buffer containing PCM 16-bit audio samples.
     * @param length Number of valid samples in the buffer.
     * @return WakeWordResult indicating whether the phrase was recognized and confidence.
     */
    fun processFrame(audioBuffer: ShortArray, length: Int): WakeWordResult

    /**
     * Clears internal state, ring buffers, and acoustic tracking.
     */
    fun reset()

    /**
     * Releases memory, native handles, or model references.
     */
    fun release()
}
