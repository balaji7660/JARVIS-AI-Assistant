package com.jarvis.assistant.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReferenceResolverTest {

    private lateinit var resolver: ReferenceResolver

    @Before
    fun setUp() {
        resolver = ReferenceResolver()
    }

    @Test
    fun resolve_theFirstResult_resolvesFirstVisibleResult() {
        val context = ConversationContext(
            detectedElements = listOf(
                ScreenElementContext(label = "Spring Boot in 100 Seconds", type = "result"),
                ScreenElementContext(label = "Spring Boot Full Course 2024", type = "result"),
                ScreenElementContext(label = "Spring Security Tutorial", type = "result")
            )
        )

        val result = resolver.resolve("open the first result", context)

        assertTrue(result is ReferenceResolutionResult.Resolved)
        val resolved = result as ReferenceResolutionResult.Resolved
        assertEquals("Spring Boot in 100 Seconds", resolved.target.label)
    }

    @Test
    fun resolve_theSecondResult_resolvesSecondVisibleResult() {
        val context = ConversationContext(
            detectedElements = listOf(
                ScreenElementContext(label = "Spring Boot in 100 Seconds", type = "result"),
                ScreenElementContext(label = "Spring Boot Full Course 2024", type = "result")
            )
        )

        val result = resolver.resolve("open the second result", context)

        assertTrue(result is ReferenceResolutionResult.Resolved)
        val resolved = result as ReferenceResolutionResult.Resolved
        assertEquals("Spring Boot Full Course 2024", resolved.target.label)
    }

    @Test
    fun resolve_singleButton_resolvesUnambiguously() {
        val context = ConversationContext(
            detectedElements = listOf(
                ScreenElementContext(label = "Subscribe", type = "button"),
                ScreenElementContext(label = "Description text here", type = "text")
            )
        )

        val result = resolver.resolve("click the button", context)

        assertTrue(result is ReferenceResolutionResult.Resolved)
        assertEquals("Subscribe", (result as ReferenceResolutionResult.Resolved).target.label)
    }

    @Test
    fun resolve_multipleButtons_returnsAmbiguousAndNeverGuesses() {
        val context = ConversationContext(
            detectedElements = listOf(
                ScreenElementContext(label = "Subscribe", type = "button"),
                ScreenElementContext(label = "Like", type = "button"),
                ScreenElementContext(label = "Share", type = "button")
            )
        )

        val result = resolver.resolve("click that button", context)

        assertTrue(result is ReferenceResolutionResult.Ambiguous)
        val ambiguous = result as ReferenceResolutionResult.Ambiguous
        assertEquals(3, ambiguous.candidates.size)
        assertEquals("click that button", ambiguous.query)
    }

    @Test
    fun resolve_pronounWithEstablishedTarget_resolvesTarget() {
        val context = ConversationContext(
            currentTarget = "Kotlin Tutorial",
            detectedElements = listOf(
                ScreenElementContext(label = "Kotlin Tutorial", type = "result"),
                ScreenElementContext(label = "Java Tutorial", type = "result")
            )
        )

        val result = resolver.resolve("open it", context)

        assertTrue(result is ReferenceResolutionResult.Resolved)
        assertEquals("Kotlin Tutorial", (result as ReferenceResolutionResult.Resolved).target.label)
    }

    @Test
    fun resolve_theApp_resolvesForegroundAppContext() {
        val context = ConversationContext(
            currentApp = "YouTube",
            currentPackage = "com.google.android.youtube"
        )

        val result = resolver.resolve("open the app", context)

        assertTrue(result is ReferenceResolutionResult.Resolved)
        assertEquals("YouTube", (result as ReferenceResolutionResult.Resolved).target.label)
    }

    @Test
    fun resolve_noMatch_returnsNotFound() {
        val context = ConversationContext(
            detectedElements = emptyList()
        )

        val result = resolver.resolve("open the first result", context)

        assertTrue(result is ReferenceResolutionResult.NotFound)
    }
}
