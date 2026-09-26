package com.jarvis.assistant.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import kotlin.math.max

interface TTSListener {
    fun onInitSuccess() {}
    fun onInitError(message: String) {}
    fun onSpeechStarted(utteranceId: String) {}
    fun onSpeechCompleted(utteranceId: String) {}
    fun onSpeechError(utteranceId: String, errorMessage: String) {}
}

/**
 * Text-to-Speech manager for JARVIS with watchdog timeout protection and masculine voice tuning.
 * Guarantees that playback always returns an onSpeechCompleted or onSpeechError callback.
 */
class JarvisTTSManager(
    context: Context,
    private val listener: TTSListener
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JarvisTTSManager"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    val isReady: Boolean get() = isInitialized

    private var activeUtteranceId: String? = null
    private var watchdogRunnable: Runnable? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = false
                mainHandler.post {
                    listener.onInitError("English language is not supported for Text-to-Speech on this device.")
                }
            } else {
                isInitialized = true
                configureMaleVoice()

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val id = utteranceId.orEmpty()
                        mainHandler.post {
                            listener.onSpeechStarted(id)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        val id = utteranceId.orEmpty()
                        cancelWatchdog()
                        mainHandler.post {
                            listener.onSpeechCompleted(id)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        val id = utteranceId.orEmpty()
                        cancelWatchdog()
                        mainHandler.post {
                            listener.onSpeechError(id, "TTS playback error.")
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        val id = utteranceId.orEmpty()
                        cancelWatchdog()
                        mainHandler.post {
                            listener.onSpeechError(id, "TTS error code: $errorCode")
                        }
                    }
                })

                mainHandler.post {
                    listener.onInitSuccess()
                }
            }
        } else {
            isInitialized = false
            mainHandler.post {
                listener.onInitError("Text-to-Speech initialization failed with status $status.")
            }
        }
    }

    private fun configureMaleVoice() {
        val tts = textToSpeech ?: return
        tts.setPitch(0.85f)
        tts.setSpeechRate(1.02f)

        try {
            val availableVoices = tts.voices
            if (!availableVoices.isNullOrEmpty()) {
                val englishVoices = availableVoices.filter { voice ->
                    val lang = voice.locale.language
                    (lang.equals("en", ignoreCase = true) || voice.locale == Locale.US || voice.locale == Locale.UK) &&
                        !voice.name.contains("female", ignoreCase = true)
                }

                val maleCandidate = englishVoices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase(Locale.ROOT)
                    val features = voice.features?.map { it.lowercase(Locale.ROOT) } ?: emptyList()
                    nameLower.contains("male") ||
                        features.any { it.contains("male") } ||
                        nameLower.contains("#male") ||
                        nameLower.contains("-male-")
                } ?: englishVoices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase(Locale.ROOT)
                    nameLower.contains("rjs") || nameLower.contains("iom") || nameLower.contains("iol") || nameLower.contains("fis")
                } ?: englishVoices.firstOrNull { voice ->
                    voice.locale.country.equals("GB", ignoreCase = true) || voice.locale.country.equals("US", ignoreCase = true)
                }

                if (maleCandidate != null) {
                    tts.voice = maleCandidate
                }
            }
        } catch (_: Throwable) {
            // Retain pitch-adjusted male timbre fallback
        }
    }

    fun speak(text: String, utteranceId: String = UUID.randomUUID().toString()): Boolean {
        cancelWatchdog()

        if (!isInitialized || textToSpeech == null) {
            mainHandler.post {
                listener.onSpeechError(utteranceId, "Text-to-Speech is not initialized.")
            }
            return false
        }

        if (text.isBlank()) {
            mainHandler.post {
                listener.onSpeechCompleted(utteranceId)
            }
            return true
        }

        activeUtteranceId = utteranceId

        // Schedule watchdog timeout based on length of speech
        // ~10 chars per second + 4s buffer, capped between 4s and 20s
        val timeoutMs = max(4_000L, (text.length * 90L) + 4_000L).coerceAtMost(20_000L)
        val watchdog = Runnable {
            if (activeUtteranceId == utteranceId) {
                Log.w(TAG, "TTS internal watchdog timeout reached for utterance $utteranceId ($timeoutMs ms). Forcing completion.")
                activeUtteranceId = null
                listener.onSpeechCompleted(utteranceId)
            }
        }
        watchdogRunnable = watchdog
        mainHandler.postDelayed(watchdog, timeoutMs)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val result = textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        ) ?: TextToSpeech.ERROR

        if (result != TextToSpeech.SUCCESS) {
            cancelWatchdog()
            mainHandler.post {
                listener.onSpeechError(utteranceId, "TextToSpeech speak failed with code $result")
            }
            return false
        }
        return true
    }

    private fun cancelWatchdog() {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null
        activeUtteranceId = null
    }

    fun stop() {
        cancelWatchdog()
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {}
        mainHandler.post {
            listener.onSpeechCompleted("stopped")
        }
    }

    fun shutdown() {
        cancelWatchdog()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
        textToSpeech = null
        isInitialized = false
    }
}
