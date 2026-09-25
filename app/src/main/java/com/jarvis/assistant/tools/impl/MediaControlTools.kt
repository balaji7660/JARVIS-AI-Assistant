package com.jarvis.assistant.tools.impl

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult

/**
 * Base helper for dispatching media key events via AudioManager.
 */
object MediaEventDispatcher {
    fun sendKeyEvent(context: Context?, keyCode: Int): Boolean {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
        return true
    }
}

/**
 * Tool: play_media
 */
class PlayMediaTool(
    private val context: Context? = null,
    private val actionOverride: (() -> Boolean)? = null
) : JarvisTool {
    override val name: String = "play_media"
    override val description: String = "Starts or resumes playback of media/music."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val success = actionOverride?.invoke() ?: MediaEventDispatcher.sendKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY)
        return if (success) {
            ToolResult(true, "Playing media, boss.", mapOf("directResponse" to true))
        } else {
            ToolResult(false, "Could not control media playback, boss.", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: pause_media
 */
class PauseMediaTool(
    private val context: Context? = null,
    private val actionOverride: (() -> Boolean)? = null
) : JarvisTool {
    override val name: String = "pause_media"
    override val description: String = "Pauses active media/music playback."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val success = actionOverride?.invoke() ?: MediaEventDispatcher.sendKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
        return if (success) {
            ToolResult(true, "Paused media, boss.", mapOf("directResponse" to true))
        } else {
            ToolResult(false, "Could not pause media, boss.", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: resume_media
 */
class ResumeMediaTool(
    private val context: Context? = null,
    private val actionOverride: (() -> Boolean)? = null
) : JarvisTool {
    override val name: String = "resume_media"
    override val description: String = "Resumes media/music playback."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val success = actionOverride?.invoke() ?: MediaEventDispatcher.sendKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY)
        return if (success) {
            ToolResult(true, "Resumed playback, boss.", mapOf("directResponse" to true))
        } else {
            ToolResult(false, "Could not resume media, boss.", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: next_track
 */
class NextTrackTool(
    private val context: Context? = null,
    private val actionOverride: (() -> Boolean)? = null
) : JarvisTool {
    override val name: String = "next_track"
    override val description: String = "Skips to the next music track or media item."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val success = actionOverride?.invoke() ?: MediaEventDispatcher.sendKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        return if (success) {
            ToolResult(true, "Playing next track, boss.", mapOf("directResponse" to true))
        } else {
            ToolResult(false, "Could not skip to next track, boss.", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: previous_track
 */
class PreviousTrackTool(
    private val context: Context? = null,
    private val actionOverride: (() -> Boolean)? = null
) : JarvisTool {
    override val name: String = "previous_track"
    override val description: String = "Skips back to the previous music track or media item."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val success = actionOverride?.invoke() ?: MediaEventDispatcher.sendKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        return if (success) {
            ToolResult(true, "Playing previous track, boss.", mapOf("directResponse" to true))
        } else {
            ToolResult(false, "Could not skip to previous track, boss.", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: get_media_state
 */
class GetMediaStateTool(
    private val context: Context? = null,
    private val mockIsActive: Boolean? = null
) : JarvisTool {
    override val name: String = "get_media_state"
    override val description: String = "Checks whether audio/music is actively playing on the device."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (mockIsActive != null) {
            val stateText = if (mockIsActive) "playing" else "paused"
            return ToolResult(true, "Media playback is currently $stateText, boss.", mapOf("isPlaying" to mockIsActive, "directResponse" to true))
        }

        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult(false, "Audio service unavailable, boss.", mapOf("directResponse" to true))

        val isMusicActive = audioManager.isMusicActive
        val message = if (isMusicActive) "Media is currently playing, boss." else "No media is currently playing, boss."
        return ToolResult(
            success = true,
            message = message,
            data = mapOf("isPlaying" to isMusicActive, "directResponse" to true)
        )
    }
}
