package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

interface SpeechRecognitionListener {
    fun onReady() {}
    fun onBeginningOfSpeech() {}
    fun onRmsChanged(rmsdB: Float) {}
    fun onPartialResult(text: String) {}
    fun onFinalResult(text: String) {}
    fun onError(errorCode: Int, errorMessage: String) {}
}

class SpeechRecognizerManager(
    private val context: Context,
    private val listener: SpeechRecognitionListener
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening() {
        mainHandler.post {
            if (isListening) {
                stopListening()
            }

            if (!isRecognitionAvailable()) {
                listener.onError(
                    -1,
                    "Speech recognition service is not available on this device."
                )
                return@post
            }

            try {
                // Always destroy stale recognizer instance to prevent Android ERROR_CLIENT binder corruption
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                isListening = false
                listener.onError(-1, e.message ?: "Failed to start speech recognition.")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            isListening = false
        }
    }

    fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
            isListening = false
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
            isListening = false
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listener.onReady()
            }

            override fun onBeginningOfSpeech() {
                listener.onBeginningOfSpeech()
            }

            override fun onRmsChanged(rmsdB: Float) {
                listener.onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_CLIENT -> "Google Speech service unavailable or busy. Please check Google app microphone permissions."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK -> "Network connection required for speech recognition."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timed out."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Please try again."
                    SpeechRecognizer.ERROR_SERVER -> "Recognition server error. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    else -> "Speech recognition error ($error)."
                }
                listener.onError(error, message)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalTranscript = matches?.firstOrNull()?.trim().orEmpty()
                if (finalTranscript.isNotEmpty()) {
                    listener.onFinalResult(finalTranscript)
                } else {
                    listener.onError(
                        SpeechRecognizer.ERROR_NO_MATCH,
                        "No speech detected. Please try again."
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialTranscript = matches?.firstOrNull()?.trim().orEmpty()
                if (partialTranscript.isNotEmpty()) {
                    listener.onPartialResult(partialTranscript)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}
