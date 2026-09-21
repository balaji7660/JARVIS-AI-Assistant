package com.jarvis.assistant.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
                // Subtle sleek futuristic pitch and smooth pace
                textToSpeech?.setPitch(0.92f)
                textToSpeech?.setSpeechRate(1.05f)

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

    fun speak(text: String, utteranceId: String = UUID.randomUUID().toString()) {
        if (!isInitialized || textToSpeech == null) {
            listener.onSpeechError(utteranceId, "Text-to-Speech is not initialized.")
            return
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        )
    }

    fun stop() {
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {}
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
