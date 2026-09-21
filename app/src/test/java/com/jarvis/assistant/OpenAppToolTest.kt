package com.jarvis.assistant

import com.jarvis.assistant.tools.impl.OpenAppTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAppToolTest {

    private lateinit var tool: OpenAppTool
    private val installedApps = setOf("YouTube", "Chrome", "Settings")

    @Before
    fun setUp() {
        tool = OpenAppTool(
            customLauncher = { appName ->
                installedApps.any { it.equals(appName, ignoreCase = true) }
            }
        )
    }

    @Test
    fun installedApp_launchesSuccessfully() = runTest {
        val result = tool.execute(mapOf("appName" to "YouTube"))

        assertTrue(result.success)
        assertEquals("Opening YouTube, boss.", result.message)
    }

    @Test
    fun missingApp_returnsFailureWithExplanation() = runTest {
        val result = tool.execute(mapOf("appName" to "NonExistentApp123"))

        assertFalse(result.success)
        assertEquals("I couldn't find NonExistentApp123 on this device, boss.", result.message)
    }

    @Test
    fun missingArgument_returnsFailure() = runTest {
        val result = tool.execute(emptyMap())

        assertFalse(result.success)
        assertTrue(result.message.contains("missing or empty"))
    }
}
