package com.jarvis.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

enum class AudioFocusState {
    OWNED,
    RELEASED
}

enum class AudioFocusMode {
    COMMAND_LISTENING,
    TTS_PLAYBACK,
    NONE
}

/**
 * Authoritative manager for Android AudioFocus.
 * Guarantees that audio focus is acquired cleanly and abandoned promptly without stale references.
 */
class JarvisAudioFocusManager(
    private val context: Context? = null,
    private val onAudioFocusChanged: ((Boolean) -> Unit)? = null
) {

    companion object {
        private const val TAG = "JarvisAudioFocus"
    }

    private val audioManager: AudioManager? = try {
        val ctx = context?.applicationContext ?: context
        ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } catch (_: Throwable) {
        null
    }

    private var activeFocusRequest: AudioFocusRequest? = null
    private var currentMode: AudioFocusMode = AudioFocusMode.NONE

    val focusState: AudioFocusState
        get() = if (currentMode != AudioFocusMode.NONE) AudioFocusState.OWNED else AudioFocusState.RELEASED

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "AUDIOFOCUS_GAIN received")
                onAudioFocusChanged?.invoke(true)
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "AudioFocus lost ($focusChange)")
                onAudioFocusChanged?.invoke(false)
            }
        }
    }

    @Synchronized
    fun requestFocus(mode: AudioFocusMode): Boolean {
        if (currentMode == mode) return true

        // Abandon any existing focus first
        abandonFocus()

        if (mode == AudioFocusMode.NONE) {
            return true
        }

        if (audioManager == null) {
            // Null in test environments; track state safely
            currentMode = mode
            return true
        }

        val focusGain = when (mode) {
            AudioFocusMode.COMMAND_LISTENING -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            AudioFocusMode.TTS_PLAYBACK -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            AudioFocusMode.NONE -> return true
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

            activeFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                focusGain
            )
        }

        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) {
            currentMode = mode
            JarvisLogger.log(JarvisLogger.Event.AUDIO_FOCUS_GAINED, "Mode: $mode")
        } else {
            currentMode = AudioFocusMode.NONE
            Log.w(TAG, "Audio focus request rejected for mode $mode (code: $result)")
        }
        return granted
    }

    @Synchronized
    fun abandonFocus() {
        if (currentMode == AudioFocusMode.NONE) {
            return
        }

        try {
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activeFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                    activeFocusRequest = null
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(focusChangeListener)
                }
            }
            JarvisLogger.log(JarvisLogger.Event.AUDIO_FOCUS_RELEASED, "Previous mode: $currentMode")
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus: ${e.message}")
        } finally {
            currentMode = AudioFocusMode.NONE
        }
    }
}
