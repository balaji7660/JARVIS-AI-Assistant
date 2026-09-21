package com.jarvis.assistant

import com.jarvis.assistant.tools.impl.OpenUrlTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenUrlToolTest {

    private var launchedUrls = mutableListOf<String>()
    private lateinit var tool: OpenUrlTool

    @Before
    fun setUp() {
        launchedUrls.clear()
        tool = OpenUrlTool(
            urlLauncher = { url ->
                launchedUrls.add(url)
                true
            }
        )
    }

    @Test
    fun httpsUrl_isAcceptedAndLaunched() = runTest {
        val result = tool.execute(mapOf("url" to "https://example.com/test"))

        assertTrue(result.success)
        assertEquals(1, launchedUrls.size)
        assertEquals("https://example.com/test", launchedUrls[0])
    }

    @Test
    fun httpUrl_isAcceptedAndLaunched() = runTest {
        val result = tool.execute(mapOf("url" to "http://example.org"))

        assertTrue(result.success)
        assertEquals(1, launchedUrls.size)
        assertEquals("http://example.org", launchedUrls[0])
    }

    @Test
    fun ftpScheme_isRejected() = runTest {
        val result = tool.execute(mapOf("url" to "ftp://files.example.com"))

        assertFalse(result.success)
        assertTrue(result.message.contains("Only HTTP and HTTPS"))
        assertEquals(0, launchedUrls.size)
    }

    @Test
    fun fileScheme_isRejected() = runTest {
        val result = tool.execute(mapOf("url" to "file:///etc/passwd"))

        assertFalse(result.success)
        assertTrue(result.message.contains("Only HTTP and HTTPS"))
        assertEquals(0, launchedUrls.size)
    }

    @Test
    fun javascriptScheme_isRejected() = runTest {
        val result = tool.execute(mapOf("url" to "javascript:alert(1)"))

        assertFalse(result.success)
        assertTrue(result.message.contains("Only HTTP and HTTPS"))
        assertEquals(0, launchedUrls.size)
    }

    @Test
    fun malformedUrl_isRejected() = runTest {
        val result = tool.execute(mapOf("url" to "https://"))

        assertFalse(result.success)
        assertTrue(result.message.contains("Malformed URL"))
        assertEquals(0, launchedUrls.size)
    }

    @Test
    fun missingUrl_isRejected() = runTest {
        val result = tool.execute(emptyMap())

        assertFalse(result.success)
        assertTrue(result.message.contains("missing or empty"))
        assertEquals(0, launchedUrls.size)
    }
}
