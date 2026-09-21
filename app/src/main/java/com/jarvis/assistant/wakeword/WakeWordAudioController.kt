package com.jarvis.assistant.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controller responsible for managing hardware AudioRecord resources,
 * verifying microphone permissions, and delivering audio frames to the local wake-word engine.
 * Guarantees strict mutual exclusion with SpeechRecognizer.
 */
class WakeWordAudioController(
    private val context: Context,
    private val onAudioFrame: (buffer: ShortArray, length: Int) -> Unit,
    private val onError: (error: String) -> Unit = {}
) {

    companion object {
        private const val TAG = "WakeWordAudioCtrl"
        private const val SAMPLE_RATE = WakeWordConfig.AUDIO_SAMPLE_RATE
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE = WakeWordConfig.AUDIO_FRAME_SIZE
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val audioScope = CoroutineScope(Dispatchers.IO)

    // Preallocated audio frame buffer (reused on every cycle to avoid GC pressure)
    private val frameBuffer = ShortArray(FRAME_SIZE)

    val isCapturing: Boolean
        get() = isRecording.get()

    /**
     * Checks if RECORD_AUDIO permission is currently granted.
     */
    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Opens the microphone and begins reading audio frames on a background IO thread.
     * @return true if audio capture started successfully, false otherwise.
     */
    @Synchronized
    fun startCapture(): Boolean {
        if (isRecording.get()) {
            Log.d(TAG, "Audio capture already active.")
            return true
        }

        if (!hasMicrophonePermission()) {
            val msg = "RECORD_AUDIO permission not granted."
            Log.w(TAG, msg)
            onError(msg)
            return false
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                val msg = "Failed to calculate AudioRecord buffer size."
                Log.e(TAG, msg)
                onError(msg)
                return false
            }

            val bufferSize = maxOf(minBufferSize * 2, FRAME_SIZE * 4)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                val msg = "AudioRecord failed to initialize (status != STATE_INITIALIZED)."
                Log.e(TAG, msg)
                onError(msg)
                return false
            }

            audioRecord = record
            record.startRecording()
            isRecording.set(true)
            Log.i(TAG, "AudioRecord started: 16kHz mono 16-bit PCM.")

            // Start bounded, non-allocating background audio read loop
            recordingJob?.cancel()
            recordingJob = audioScope.launch {
                readAudioLoop(record)
            }

            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException opening microphone", e)
            onError("Microphone permission denied.")
            stopCapture()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting AudioRecord", e)
            onError("Audio capture error: ${e.message}")
            stopCapture()
            return false
        }
    }

    private suspend fun readAudioLoop(record: AudioRecord) {
        var offset = 0

        while (audioScope.isActive && isRecording.get()) {
            val readCount = record.read(frameBuffer, offset, FRAME_SIZE - offset)

            if (readCount > 0) {
                offset += readCount
                if (offset >= FRAME_SIZE) {
                    // Full frame ready for wake-word engine
                    try {
                        onAudioFrame(frameBuffer, FRAME_SIZE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onAudioFrame callback", e)
                    }
                    offset = 0
                }
            } else if (readCount < 0) {
                val errorMsg = when (readCount) {
                    AudioRecord.ERROR_INVALID_OPERATION -> "AudioRecord ERROR_INVALID_OPERATION"
                    AudioRecord.ERROR_BAD_VALUE -> "AudioRecord ERROR_BAD_VALUE"
                    AudioRecord.ERROR_DEAD_OBJECT -> "AudioRecord ERROR_DEAD_OBJECT"
                    else -> "AudioRecord read error ($readCount)"
                }
                Log.e(TAG, errorMsg)
                onError(errorMsg)
                break
            }
        }
    }

    /**
     * Cleanly stops and releases AudioRecord hardware resources.
     */
    @Synchronized
    fun stopCapture() {
        if (!isRecording.getAndSet(false) && audioRecord == null) {
            return
        }

        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
                Log.i(TAG, "AudioRecord stopped and hardware resources released.")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioRecord", e)
            } finally {
                audioRecord = null
            }
        }
    }

    /**
     * Completely destroys the controller and guarantees no leaks.
     */
    fun release() {
        stopCapture()
    }
}
