package com.jarvis.assistant.wakeword

/**
 * Centralized configuration parameters and thresholds for local wake-word detection.
 * Designed to prevent false activations, optimize CPU/battery usage, and ensure responsive detection.
 */
object WakeWordConfig {
    /** Target wake phrase recognized locally. */
    const val WAKE_PHRASE: String = "Hey JARVIS"

    /** Standard audio sample rate for speech processing (16 kHz). */
    const val AUDIO_SAMPLE_RATE: Int = 16000

    /** Frame size in samples per chunk (512 samples = 32ms at 16kHz). */
    const val AUDIO_FRAME_SIZE: Int = 512

    /** Overlap step (hop size) for spectral analysis (256 samples = 16ms). */
    const val AUDIO_HOP_SIZE: Int = 256

    /** Number of Mel frequency filter banks. */
    const val MEL_FILTER_COUNT: Int = 26

    /** Lower frequency bound for speech feature extraction (Hz). */
    const val LOWER_FREQ_HZ: Float = 100.0f

    /** Upper frequency bound for speech feature extraction (Hz). */
    const val UPPER_FREQ_HZ: Float = 7500.0f

    /**
     * Minimum likelihood / confidence threshold (0.0 to 1.0) required to trigger detection.
     * Prevents false triggers in noisy environments.
     */
    const val WAKE_WORD_THRESHOLD: Float = 0.65f

    /**
     * Cooldown period in milliseconds following a successful trigger or command session.
     * Prevents duplicate/repeating triggers while the assistant processes speech or replies.
     */
    const val WAKE_WORD_COOLDOWN_MS: Long = 2500L

    /**
     * Root Mean Square (RMS) energy threshold to filter out room silence and conserve battery.
     */
    const val SILENCE_ENERGY_THRESHOLD: Float = 120.0f

    /**
     * Maximum ring buffer capacity in frames (~1.25 seconds of historical audio).
     */
    const val RING_BUFFER_FRAME_CAPACITY: Int = 40
}
