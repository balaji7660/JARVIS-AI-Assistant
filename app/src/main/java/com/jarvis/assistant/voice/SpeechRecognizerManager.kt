package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

interface SpeechRecognitionListener {
    fun onReady() {}
    fun onBeginningOfSpeech() {}
    fun onRmsChanged(rmsdB: Float) {}
    fun onPartialResult(text: String) {}
    fun onFinalResult(text: String) {}
    fun onError(errorCode: Int, errorMessage: String) {}
}

/**
 * Robust SpeechRecognizer lifecycle manager.
 * Guarantees that speech recognition sessions are created and destroyed cleanly,
 * and errors (such as ERROR_RECOGNIZER_BUSY, ERROR_CLIENT, ERROR_NO_MATCH) are safely recovered.
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val listener: SpeechRecognitionListener
) {

    companion object {
        private const val TAG = "SpeechRecognizerMgr"
    }

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
                val errorMsg = "Speech recognition service is not available on this device."
                Log.w(TAG, errorMsg)
                listener.onError(-1, errorMsg)
                return@post
            }

            try {
                // Destroy any stale instance first to avoid binder corruption
                releaseRecognizerInternal()

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                recognizer.setRecognitionListener(createListener())
                speechRecognizer = recognizer

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

                recognizer.startListening(intent)
                isListening = true
                Log.i(TAG, "SpeechRecognizer started successfully.")
            } catch (e: Exception) {
                isListening = false
                releaseRecognizerInternal()
                val msg = e.message ?: "Failed to start speech recognition."
                Log.e(TAG, "Exception starting SpeechRecognizer: $msg", e)
                listener.onError(-1, msg)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error in stopListening: ${e.message}")
            }
            isListening = false
        }
    }

    fun cancel() {
        mainHandler.post {
            releaseRecognizerInternal()
        }
    }

    fun destroy() {
        mainHandler.post {
            releaseRecognizerInternal()
        }
    }

    private fun releaseRecognizerInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            isListening = false
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech")
                listener.onReady()
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech")
                listener.onBeginningOfSpeech()
            }

            override fun onRmsChanged(rmsdB: Float) {
                listener.onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                releaseRecognizerInternal()

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error. Retrying voice pipeline."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK -> "Network connection required for speech recognition."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timed out."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Retrying."
                    SpeechRecognizer.ERROR_SERVER -> "Recognition server error. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    else -> "Speech recognition error ($error)."
                }
                Log.w(TAG, "SpeechRecognizer error: $message (code: $error)")
                listener.onError(error, message)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                releaseRecognizerInternal()

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalTranscript = matches?.firstOrNull()?.trim().orEmpty()
                Log.i(TAG, "Speech recognition results received. Transcript: \"$finalTranscript\"")

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
