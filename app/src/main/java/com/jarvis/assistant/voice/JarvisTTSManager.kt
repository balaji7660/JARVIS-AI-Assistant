package com.jarvis.assistant.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

interface TTSListener {
    fun onInitSuccess() {}
    fun onInitError(message: String) {}
    fun onSpeechStarted(utteranceId: String) {}
    fun onSpeechCompleted(utteranceId: String) {}
    fun onSpeechError(utteranceId: String, errorMessage: String) {}
}

class JarvisTTSManager(
    context: Context,
    private val listener: TTSListener
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    val isReady: Boolean get() = isInitialized

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
                        mainHandler.post {
                            listener.onSpeechStarted(utteranceId.orEmpty())
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            listener.onSpeechCompleted(utteranceId.orEmpty())
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            listener.onSpeechError(utteranceId.orEmpty(), "TTS playback error.")
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.post {
                            listener.onSpeechError(utteranceId.orEmpty(), "TTS error code: $errorCode")
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

    /**
     * Configures a calm, articulate male voice characteristic of JARVIS.
     * Selects an available male voice from the TTS engine and sets masculine pitch/cadence.
     */
    private fun configureMaleVoice() {
        val tts = textToSpeech ?: return

        // Deep, articulate, calm masculine pitch and cadence
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

                // 1. Explicitly tagged male voices
                val maleCandidate = englishVoices.firstOrNull { voice ->
                    val nameLower = voice.name.lowercase(Locale.ROOT)
                    val features = voice.features?.map { it.lowercase(Locale.ROOT) } ?: emptyList()
                    nameLower.contains("male") ||
                        features.any { it.contains("male") } ||
                        nameLower.contains("#male") ||
                        nameLower.contains("-male-")
                } ?: englishVoices.firstOrNull { voice ->
                    // 2. Well-known male voice identifiers in Google TTS engine
                    val nameLower = voice.name.lowercase(Locale.ROOT)
                    nameLower.contains("rjs") || nameLower.contains("iom") || nameLower.contains("iol") || nameLower.contains("fis")
                } ?: englishVoices.firstOrNull { voice ->
                    // 3. Fallback to British English or US voice
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
            mainHandler.post {
                listener.onSpeechError(utteranceId, "TextToSpeech speak failed with code $result")
            }
            return false
        }
        return true
    }

    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {}
        mainHandler.post {
            listener.onSpeechCompleted("stopped")
        }
    }

    fun shutdown() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
        textToSpeech = null
        isInitialized = false
    }
}
