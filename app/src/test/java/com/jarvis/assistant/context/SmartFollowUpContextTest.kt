package com.jarvis.assistant.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFollowUpContextTest {

    private val resolver = ReferenceResolver()

    @Test
    fun ordinalReferences_resolveCorrectly() {
        val elements = listOf(
            ScreenElementContext(label = "Java Full Course", type = "result"),
            ScreenElementContext(label = "Spring Boot Tutorial", type = "result"),
            ScreenElementContext(label = "Kotlin Crash Course", type = "result")
        )
        val context = ConversationContext(detectedElements = elements)

        // First result
        val firstRes = resolver.resolve("open the first result", context)
        assertTrue(firstRes is ReferenceResolutionResult.Resolved)
        assertEquals("Java Full Course", (firstRes as ReferenceResolutionResult.Resolved).target.label)

        // Second one
        val secondRes = resolver.resolve("click the second one", context)
        assertTrue(secondRes is ReferenceResolutionResult.Resolved)
        assertEquals("Spring Boot Tutorial", (secondRes as ReferenceResolutionResult.Resolved).target.label)

        // Last result
        val lastRes = resolver.resolve("open the last result", context)
        assertTrue(lastRes is ReferenceResolutionResult.Resolved)
        assertEquals("Kotlin Crash Course", (lastRes as ReferenceResolutionResult.Resolved).target.label)
    }

    @Test
    fun pageReference_resolvesToCurrentScreenOrApp() {
        val context = ConversationContext(
            currentApp = "Chrome",
            currentPackage = "com.android.chrome",
            currentScreenSummary = "Google Search Results for Java Jobs"
        )

        val res = resolver.resolve("read this page", context)
        assertTrue(res is ReferenceResolutionResult.Resolved)
        assertEquals("Google Search Results for Java Jobs", (res as ReferenceResolutionResult.Resolved).target.label)
    }

    @Test
    fun cancellationFollowUp_detectsNaturalCommands() {
        assertTrue(resolver.isCancellationFollowUp("Actually, don't call"))
        assertTrue(resolver.isCancellationFollowUp("don't call Daddy"))
        assertTrue(resolver.isCancellationFollowUp("cancel call"))
        assertTrue(resolver.isCancellationFollowUp("never mind"))
        assertTrue(resolver.isCancellationFollowUp("forget it"))
        assertTrue(resolver.isCancellationFollowUp("stop"))

        assertFalse(resolver.isCancellationFollowUp("open chrome"))
        assertFalse(resolver.isCancellationFollowUp("what is the weather"))
    }
}
