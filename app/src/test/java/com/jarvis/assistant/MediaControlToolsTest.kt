package com.jarvis.assistant

import com.jarvis.assistant.tools.impl.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControlToolsTest {

    @Test
    fun playMedia_executesAction() = runBlocking {
        var played = false
        val tool = PlayMediaTool(actionOverride = {
            played = true
            true
        })

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(played)
    }

    @Test
    fun pauseMedia_executesAction() = runBlocking {
        var paused = false
        val tool = PauseMediaTool(actionOverride = {
            paused = true
            true
        })

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(paused)
    }

    @Test
    fun nextTrack_executesAction() = runBlocking {
        var skipped = false
        val tool = NextTrackTool(actionOverride = {
            skipped = true
            true
        })

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(skipped)
    }

    @Test
    fun previousTrack_executesAction() = runBlocking {
        var prevExecuted = false
        val tool = PreviousTrackTool(actionOverride = {
            prevExecuted = true
            true
        })

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(prevExecuted)
    }

    @Test
    fun getMediaState_reportsPlayingState() = runBlocking {
        val toolActive = GetMediaStateTool(mockIsActive = true)
        val resultActive = toolActive.execute(emptyMap())
        assertTrue(resultActive.success)
        assertTrue(resultActive.message.contains("playing"))

        val toolPaused = GetMediaStateTool(mockIsActive = false)
        val resultPaused = toolPaused.execute(emptyMap())
        assertTrue(resultPaused.success)
        assertFalse(resultPaused.data["isPlaying"] as Boolean)
    }
}
