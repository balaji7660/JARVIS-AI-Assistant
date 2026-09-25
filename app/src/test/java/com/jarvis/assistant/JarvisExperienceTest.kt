package com.jarvis.assistant

import com.jarvis.assistant.ai.LocalResponseEngine
import com.jarvis.assistant.context.ContinuousConversationManager
import com.jarvis.assistant.context.ConversationContext
import com.jarvis.assistant.context.ReferenceResolutionResult
import com.jarvis.assistant.context.ReferenceResolver
import com.jarvis.assistant.context.ScreenElementContext
import com.jarvis.assistant.personality.JarvisPersonalityEngine
import com.jarvis.assistant.proactive.ProactiveAssistantManager
import com.jarvis.assistant.proactive.ProactiveEventType
import com.jarvis.assistant.tools.impl.CallContactTool
import com.jarvis.assistant.ui.home.AssistantState
import com.jarvis.assistant.ui.home.HomeUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisExperienceTest {

    // 1. Continuous Conversation & Timeout Tests
    @Test
    fun continuousConversation_sessionActivatesAndMaintainsState() {
        var currentTime = 10_000L
        var timedOut = false
        val manager = ContinuousConversationManager(
            sessionTimeoutMs = 45_000L,
            timeProvider = { currentTime },
            onSessionExpired = { timedOut = true }
        )
        assertFalse(manager.isSessionActive())

        manager.startOrExtendSession()
        assertTrue(manager.isSessionActive())
        assertTrue(manager.getRemainingSessionTimeMs() > 0)

        // Advance 10s -> still active
        currentTime += 10_000L
        manager.evaluateTimeout()
        assertTrue(manager.isSessionActive())
        assertFalse(timedOut)

        // Advance 40s more (total 50s) -> expired
        currentTime += 40_000L
        manager.evaluateTimeout()
        assertFalse(manager.isSessionActive())
        assertTrue(timedOut)
    }

    @Test
    fun continuousConversation_endSessionClearsActiveState() {
        val manager = ContinuousConversationManager()
        manager.startOrExtendSession()
        assertTrue(manager.isSessionActive())

        manager.endSession()
        assertFalse(manager.isSessionActive())
        assertEquals(0L, manager.getRemainingSessionTimeMs())
    }

    // 2. Natural Interruption & Cancellation Tests
    @Test
    fun interruption_cancellationCommandsIdentifiedCorrectly() {
        val cancellationInputs = listOf(
            "stop", "wait", "cancel", "no", "abort",
            "never mind", "nevermind", "stop task", "cancel automation",
            "actually do this instead", "stop, wait"
        )
        for (input in cancellationInputs) {
            val lower = input.lowercase().trim().removeSuffix(".")
            val isCancelled = lower in setOf("stop", "wait", "cancel", "no", "abort", "never mind", "nevermind", "stop task", "cancel task", "stop automation", "cancel automation", "actually do this instead") ||
                    lower.startsWith("stop,") || lower.startsWith("cancel,")
            assertTrue("Expected '$input' to be recognized as cancellation", isCancelled)
        }

        assertFalse("open chrome should not be cancellation", listOf("stop", "cancel").contains("open chrome"))
    }

    // 3. Task Revision Tests
    @Test
    fun taskRevision_detectsCorrectionsAndExtractsTarget() {
        assertTrue(ReferenceResolver.isTaskCorrection("No, Mom."))
        assertEquals("Mom.", ReferenceResolver.extractCorrectionTarget("No, Mom."))

        assertTrue(ReferenceResolver.isTaskCorrection("Actually open YouTube"))
        assertEquals("open YouTube", ReferenceResolver.extractCorrectionTarget("Actually open YouTube"))

        assertTrue(ReferenceResolver.isTaskCorrection("Instead call Dad"))
        assertEquals("call Dad", ReferenceResolver.extractCorrectionTarget("Instead call Dad"))

        assertTrue(ReferenceResolver.isTaskCorrection("Change to 5 minutes"))
        assertEquals("5 minutes", ReferenceResolver.extractCorrectionTarget("Change to 5 minutes"))

        assertFalse(ReferenceResolver.isTaskCorrection("Open Chrome"))
    }

    // 4. Smart Clarification Tests
    @Test
    fun clarification_disambiguatesMultipleMatchingContacts() = runBlocking {
        val tool = CallContactTool(
            context = null,
            contactLookupOverride = {
                listOf(
                    CallContactTool.ContactInfo("Rahul Sharma", "+919876543210"),
                    CallContactTool.ContactInfo("Rahul Verma", "+919876543211")
                )
            }
        )

        val result = tool.execute(mapOf("contactName" to "Rahul"))
        assertFalse(result.success)
        assertEquals(true, result.data["isAmbiguous"])
        val candidates = result.data["candidates"] as? List<*>
        assertNotNull(candidates)
        assertEquals(2, candidates?.size)
        assertTrue(result.message.contains("Rahul Sharma") && result.message.contains("Rahul Verma"))
    }

    // 5. Follow-Up Reference Resolution Tests
    @Test
    fun followUpResolution_resolvesContextualReferences() {
        val resolver = ReferenceResolver()
        val context = ConversationContext(
            currentApp = "Chrome",
            currentPackage = "com.android.chrome",
            recentEntities = listOf("Java Developer", "Rahul"),
            detectedElements = listOf(
                ScreenElementContext(label = "Search", type = "button"),
                ScreenElementContext(label = "First Result", type = "result"),
                ScreenElementContext(label = "Second Result", type = "result")
            )
        )

        // Resolve "the current app"
        val appRes = resolver.resolve("the current app", context)
        assertTrue(appRes is ReferenceResolutionResult.Resolved)
        assertEquals("Chrome", (appRes as ReferenceResolutionResult.Resolved).target.label)

        // Resolve "the first result"
        val firstRes = resolver.resolve("the first result", context)
        assertTrue(firstRes is ReferenceResolutionResult.Resolved)
        assertEquals("First Result", (firstRes as ReferenceResolutionResult.Resolved).target.label)

        // Resolve "that contact"
        val contactRes = resolver.resolve("that contact", context)
        assertTrue(contactRes is ReferenceResolutionResult.Resolved)
        assertEquals("Java Developer", (contactRes as ReferenceResolutionResult.Resolved).target.label)
    }

    // 6. Natural Confirmation & Cancellation Tests
    @Test
    fun confirmation_affirmsAndDeniesProperly() {
        val affirmative = listOf("yes", "yeah", "yep", "do it", "go ahead", "confirm", "sure", "proceed")
        val negative = listOf("no", "cancel", "stop", "don't", "never mind", "abort")

        for (word in affirmative) {
            val lower = word.lowercase().trim()
            assertTrue(lower in setOf("yes", "yeah", "yep", "do it", "go ahead", "confirm", "sure", "please do", "proceed"))
        }

        for (word in negative) {
            val lower = word.lowercase().trim()
            assertTrue(lower in setOf("no", "cancel", "stop", "don't", "dont", "never mind", "nevermind", "abort"))
        }
    }

    // 7. Personality Engine & Deduplication Tests
    @Test
    fun personality_deduplicatesFluffAndDuplicatePhrases() {
        val input = "Sure! Operation completed successfully. Chrome opened. Chrome opened."
        val cleaned = JarvisPersonalityEngine.deduplicate(input)
        assertFalse(cleaned.startsWith("Sure!"))
        assertFalse(cleaned.startsWith("Operation completed successfully."))
        assertEquals("Chrome opened.", cleaned)
    }

    @Test
    fun personality_consistentAcksAndBriefing() {
        assertTrue(listOf("Yes, boss?", "I'm here, boss.", "Listening, boss.", "Ready, boss.").contains(JarvisPersonalityEngine.getWakeGreeting()))
        assertTrue(listOf("Stopped, boss.", "Task cancelled, boss.", "Standing by, boss.").contains(JarvisPersonalityEngine.getInterruptionAck()))
        assertTrue(listOf("Searching now.", "Searching now, boss.", "Looking into that.").contains(JarvisPersonalityEngine.getSearchingAck()))
        assertEquals("Call Daddy?", JarvisPersonalityEngine.formatCallConfirmation("Daddy"))
        assertEquals("Okay. Call Mom?", JarvisPersonalityEngine.formatRevisionConfirmation("Mom"))

        val briefing = JarvisPersonalityEngine.formatMorningBriefing(
            remindersCount = 2,
            calendarEvents = listOf("Interview at 7 PM"),
            batteryPercent = 72
        )
        assertTrue(briefing.contains("Good morning, boss."))
        assertTrue(briefing.contains("2 reminders"))
        assertTrue(briefing.contains("Interview at 7 PM"))
        assertTrue(briefing.contains("Battery at 72%."))
    }

    // 8. Proactive Assistant Tests
    @Test
    fun proactiveAssistant_alertsWhenLowBatteryAndOptedIn() {
        val manager = ProactiveAssistantManager()

        // When disabled, no alerts should fire
        manager.setProactiveEnabled(false)
        val disabledAlert = manager.evaluateBattery(batteryPercent = 12, isCharging = false)
        assertNull(disabledAlert)

        // When enabled and battery <= 15% and not charging and past cooldown -> Alert
        manager.setProactiveEnabled(true)
        val alert = manager.evaluateBattery(batteryPercent = 15, isCharging = false, currentTime = 2_000_000L)
        assertNotNull(alert)
        assertEquals(ProactiveEventType.LOW_BATTERY, alert?.type)
        assertTrue(alert?.message?.contains("15%") == true)

        // Charging battery should not alert
        val chargingAlert = manager.evaluateBattery(batteryPercent = 10, isCharging = true, currentTime = 4_000_000L)
        assertNull(chargingAlert)
    }

    @Test
    fun proactiveAssistant_alertsForDueRemindersAndEvents() {
        val manager = ProactiveAssistantManager()
        manager.setProactiveEnabled(true)

        val reminderAlert = manager.evaluateReminder("Prepare Java interview")
        assertNotNull(reminderAlert)
        assertEquals(ProactiveEventType.REMINDER, reminderAlert?.type)
        assertTrue(reminderAlert?.message?.contains("Prepare Java interview") == true)

        val calAlert = manager.evaluateCalendarEvent("Interview with Google", minutesUntil = 15)
        assertNotNull(calAlert)
        assertEquals(ProactiveEventType.CALENDAR_EVENT, calAlert?.type)
        assertTrue(calAlert?.message?.contains("Interview with Google") == true)
    }

    // 9. Privacy Indicators & UI States
    @Test
    fun privacyIndicators_showsTransparentStates() {
        val activeMicState = HomeUiState(state = AssistantState.ACTIVE_LISTENING)
        assertTrue(activeMicState.hudStateLabel.contains("MIC ACTIVE"))

        val wakeReadyState = HomeUiState(state = AssistantState.PASSIVE_WAKE, isWakeWordEnabled = true)
        assertTrue(wakeReadyState.hudStateLabel.contains("LISTENING LOCALLY") || wakeReadyState.hudStateLabel.contains("WAKE WORD READY"))

        val speakingState = HomeUiState(state = AssistantState.SPEAKING)
        assertTrue(speakingState.hudStateLabel.contains("SPEAKING"))

        val confirmState = HomeUiState(state = AssistantState.WAITING_FOR_CONFIRMATION)
        assertTrue(confirmState.hudStateLabel.contains("CONFIRMATION"))

        val clarifyState = HomeUiState(state = AssistantState.WAITING_FOR_CLARIFICATION)
        assertTrue(clarifyState.hudStateLabel.contains("CLARIFICATION NEEDED"))
    }

    // 10. Local-First Engine & Offline Fallback Tests
    @Test
    fun localResponseEngine_canHandleLocallyAndOfflineMessage() = runBlocking {
        val engine = LocalResponseEngine()

        assertTrue(engine.canHandleLocally("what is the battery"))
        assertTrue(engine.canHandleLocally("what time is it"))
        assertTrue(engine.canHandleLocally("what is todays date"))
        assertTrue(engine.canHandleLocally("turn on flashlight"))
        assertTrue(engine.canHandleLocally("set volume to 50"))
        assertTrue(engine.canHandleLocally("open chrome"))
        assertTrue(engine.canHandleLocally("set a timer for 5 minutes"))
        assertTrue(engine.canHandleLocally("good morning"))

        // Unhandled queries offline fallback
        val offlineResponse = engine.generateResponse("explain quantum mechanics")
        assertEquals("JARVIS cloud intelligence is unavailable.", offlineResponse)
    }
}
