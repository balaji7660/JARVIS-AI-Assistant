package com.jarvis.assistant

import com.jarvis.assistant.automation.AppLaunchSynchronizer
import com.jarvis.assistant.context.ConversationContextManager
import com.jarvis.assistant.context.ReferenceResolver
import com.jarvis.assistant.data.remote.BackendHealthManager
import com.jarvis.assistant.data.remote.BackendHealthState
import com.jarvis.assistant.data.remote.NetworkFailureType
import com.jarvis.assistant.memory.InMemoryConversationRepository
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.tools.impl.CallContactTool
import com.jarvis.assistant.tools.impl.SearchYouTubeTool
import com.jarvis.assistant.tools.impl.SendWhatsAppMessageTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * End-to-end unit test suite validating:
 * 1. Permanent conversation storage & restoration into context
 * 2. Contact resolution & disambiguation
 * 3. Controlled WhatsApp UI automation state machine & safe verification
 * 4. YouTube search & screen synchronizer
 * 5. Backend health classification, circuit-breaker tripping, and automatic recovery
 */
class ProductionReliabilityTest {

    @Before
    fun setUp() {
        BackendHealthManager.reset()
    }

    // =========================================================================
    // PART 1 & 2: PERMANENT CONVERSATION STORAGE & CONTEXT RESTORATION
    // =========================================================================

    @Test
    fun conversationPersistence_savesAndRestoresAcrossSessions() = runBlocking {
        val repo = InMemoryConversationRepository()

        // 1. User says "Search YouTube for Java tutorials"
        val userMsg = repo.saveUserMessage("Search YouTube for Java tutorials.")
        assertNotNull(userMsg.conversationId)
        assertEquals("user", userMsg.role)
        assertEquals("Search YouTube for Java tutorials.", userMsg.content)

        // 2. Assistant replies "Searching YouTube for Java tutorials."
        val assistantMsg = repo.saveAssistantMessage("Searching YouTube for Java tutorials.")
        assertEquals("assistant", assistantMsg.role)
        assertEquals(userMsg.conversationId, assistantMsg.conversationId)

        // 3. Retrieve recent messages
        val messages = repo.getRecentMessages(limit = 10)
        assertEquals(2, messages.size)
        assertEquals("Search YouTube for Java tutorials.", messages[0].content)
        assertEquals("Searching YouTube for Java tutorials.", messages[1].content)

        // 4. Simulate App Restart: Restore context into ConversationContextManager
        val contextManager = ConversationContextManager()
        val restored = repo.restoreLastSessionContext()
        restored.forEach { msg ->
            if (msg.role == "user") {
                contextManager.recordUserTurn(msg.content)
            } else if (msg.role == "assistant") {
                contextManager.recordAssistantTurn(msg.content)
            }
        }

        val restoredContext = contextManager.getConversationContext()
        assertEquals(2, restoredContext.recentTurns.size)
        assertEquals("Search YouTube for Java tutorials.", restoredContext.recentTurns[0].text)
        assertEquals("Searching YouTube for Java tutorials.", restoredContext.recentTurns[1].text)

        // 5. Verify follow-up reference resolution works on restored context
        val contextWithElements = restoredContext.copy(
            detectedElements = listOf(
                com.jarvis.assistant.context.ScreenElementContext(label = "Java Spring Boot Tutorial #1", type = "result"),
                com.jarvis.assistant.context.ScreenElementContext(label = "Java Spring Boot Tutorial #2", type = "result")
            )
        )
        val resolver = ReferenceResolver()
        val resolution = resolver.resolve("Open the first result", contextWithElements)
        assertTrue(resolution is com.jarvis.assistant.context.ReferenceResolutionResult.Resolved)
        val resolved = resolution as com.jarvis.assistant.context.ReferenceResolutionResult.Resolved
        assertEquals("Java Spring Boot Tutorial #1", resolved.target.label)
    }

    // =========================================================================
    // PART 4: CALLING & CONTACT RESOLUTION
    // =========================================================================

    @Test
    fun callContact_uniqueContact_initiatesCallSuccessfully() = runBlocking {
        var placedCallNumber: String? = null
        val tool = CallContactTool(
            context = null,
            contactLookupOverride = { listOf(CallContactTool.ContactInfo("Rahul", "+919876543210")) },
            callActionOverride = { number ->
                placedCallNumber = number
                true
            }
        )

        val result = tool.execute(mapOf("contactName" to "Rahul"))
        assertTrue(result.success)
        assertEquals("+919876543210", placedCallNumber)
        assertTrue(result.message.contains("Calling Rahul"))
    }

    @Test
    fun callContact_multipleContacts_disambiguatesWithoutGuessing() = runBlocking {
        val tool = CallContactTool(
            context = null,
            contactLookupOverride = {
                listOf(
                    CallContactTool.ContactInfo("Rahul Office", "+919876543210"),
                    CallContactTool.ContactInfo("Rahul Home", "+919876543211")
                )
            }
        )

        val result = tool.execute(mapOf("contactName" to "Rahul"))
        assertFalse(result.success)
        assertEquals(true, result.data["isAmbiguous"])
        assertTrue(result.message.contains("Rahul Office"))
        assertTrue(result.message.contains("Rahul Home"))
        assertTrue(result.message.contains("Which Rahul should I call?"))
    }

    @Test
    fun callContact_missingPermission_reportsExplicitPermissionNotice() = runBlocking {
        val tool = CallContactTool(context = null)
        val result = tool.execute(mapOf("contactName" to "Rahul"))
        assertFalse(result.success)
        assertEquals("READ_CONTACTS", result.data["needsPermission"])
        assertTrue(result.message.contains("JARVIS needs contact access permission to find Rahul."))
    }

    // =========================================================================
    // PART 5: CONTROLLED WHATSAPP MESSAGING
    // =========================================================================

    @Test
    fun whatsappTool_disambiguatesMultipleContactsWithoutGuessing() = runBlocking {
        val tool = SendWhatsAppMessageTool(
            context = null,
            contactLookupOverride = {
                listOf(
                    SendWhatsAppMessageTool.ContactInfo("Rahul Sharma", "+919876543210"),
                    SendWhatsAppMessageTool.ContactInfo("Rahul Verma", "+919876543211")
                )
            }
        )

        val result = tool.execute(mapOf("contactName" to "Rahul", "message" to "I'll reach in 10 minutes"))
        assertFalse(result.success)
        assertEquals(true, result.data["isAmbiguous"])
        assertTrue(result.message.contains("multiple contacts named Rahul"))
        assertTrue(result.message.contains("Which Rahul should I message"))
    }

    @Test
    fun whatsappTool_asksConfirmationBeforeSending() = runBlocking {
        val tool = SendWhatsAppMessageTool(
            context = null,
            contactLookupOverride = {
                listOf(SendWhatsAppMessageTool.ContactInfo("Rahul", "+919876543210"))
            }
        )

        val result = tool.execute(
            mapOf(
                "contactName" to "Rahul",
                "message" to "I'll reach in 10 minutes",
                "isConfirmed" to false
            )
        )

        assertFalse(result.success)
        assertEquals(true, result.data["requiresConfirmation"])
        assertTrue(result.message.contains("Send WhatsApp message to Rahul saying: \"I'll reach in 10 minutes\"?"))
    }

    @Test
    fun whatsappTool_reportsVerificationFailureWhenSendUnconfirmed() = runBlocking {
        val mockAutomation = object : AndroidAutomationProvider {
            override suspend fun execute(action: AutomationAction): ToolResult {
                return when (action) {
                    is AutomationAction.ReadVisibleScreen -> ToolResult(
                        success = true,
                        message = "Screen ready",
                        data = mapOf("packageName" to "com.whatsapp", "summary" to "WhatsApp Chat Screen")
                    )
                    else -> ToolResult(success = false, message = "Send button click failed")
                }
            }
        }

        val tool = SendWhatsAppMessageTool(
            context = null,
            automationProvider = mockAutomation,
            contactLookupOverride = {
                listOf(SendWhatsAppMessageTool.ContactInfo("Rahul", "+919876543210"))
            }
        )

        val result = tool.execute(
            mapOf(
                "contactName" to "Rahul",
                "message" to "I'll reach in 10 minutes",
                "isConfirmed" to true
            )
        )

        assertFalse(result.success)
        assertEquals("I couldn't verify that WhatsApp sent the message.", result.message)
    }

    @Test
    fun whatsappTool_succeedsWhenSendConfirmedByAccessibility() = runBlocking {
        val mockAutomation = object : AndroidAutomationProvider {
            override suspend fun execute(action: AutomationAction): ToolResult {
                return when (action) {
                    is AutomationAction.ReadVisibleScreen -> ToolResult(
                        success = true,
                        message = "Screen ready",
                        data = mapOf("packageName" to "com.whatsapp", "summary" to "WhatsApp Chat Window: rahul")
                    )
                    else -> ToolResult(success = true, message = "Clicked Send")
                }
            }
        }

        val tool = SendWhatsAppMessageTool(
            context = null,
            automationProvider = mockAutomation,
            contactLookupOverride = {
                listOf(SendWhatsAppMessageTool.ContactInfo("Rahul", "+919876543210"))
            }
        )

        val result = tool.execute(
            mapOf(
                "contactName" to "Rahul",
                "message" to "I'll reach in 10 minutes",
                "isConfirmed" to true
            )
        )

        assertTrue(result.success)
        assertTrue(result.message.contains("WhatsApp message sent to Rahul"))
    }

    // =========================================================================
    // PART 6 & 7: YOUTUBE SEARCH & APP LAUNCH SYNCHRONIZATION
    // =========================================================================

    @Test
    fun searchYouTubeTool_executesSearchAndPopulatesReferenceContext() = runBlocking {
        val mockAutomation = object : AndroidAutomationProvider {
            override suspend fun execute(action: AutomationAction): ToolResult {
                return ToolResult(
                    success = true,
                    message = "Screen ready",
                    data = mapOf(
                        "packageName" to "com.google.android.youtube",
                        "summary" to "YouTube Results: Java Spring Boot tutorial, 1st video, 2nd video"
                    )
                )
            }
        }

        val tool = SearchYouTubeTool(
            context = null,
            automationProvider = mockAutomation,
            intentLauncher = { true }
        )

        val result = tool.execute(mapOf("query" to "Java Spring Boot tutorials"))
        assertTrue(result.success)
        assertEquals("Java Spring Boot tutorials", result.data["searchQuery"])
        assertEquals("YouTube", result.data["targetApp"])
        assertTrue(result.message.contains("Searching YouTube for \"Java Spring Boot tutorials\""))
    }

    @Test
    fun appLaunchSynchronizer_verifiesAppReadinessWithinTimeout() = runBlocking {
        var checkCount = 0
        val mockAutomation = object : AndroidAutomationProvider {
            override suspend fun execute(action: AutomationAction): ToolResult {
                checkCount++
                val pkg = if (checkCount >= 2) "com.google.android.youtube" else "com.launcher"
                return ToolResult(
                    success = true,
                    message = "Screen state",
                    data = mapOf("packageName" to pkg, "summary" to "YouTube Home Screen")
                )
            }
        }

        val synchronizer = AppLaunchSynchronizer(
            automationProvider = mockAutomation,
            context = null
        )

        val result = synchronizer.launchAndSynchronize(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            timeoutMs = 4000L
        )

        assertTrue(result.success)
        assertEquals("com.google.android.youtube", result.packageName)
    }

    // =========================================================================
    // PART 8-15: BACKEND HEALTH, CLASSIFICATION, CIRCUIT BREAKER & RECOVERY
    // =========================================================================

    @Test
    fun backendHealthManager_failureClassificationDistinguishesCauses() {
        assertEquals(NetworkFailureType.DNS_FAILURE, BackendHealthManager.classifyFailure(UnknownHostException("Unable to resolve host")))
        assertEquals(NetworkFailureType.CONNECT_TIMEOUT, BackendHealthManager.classifyFailure(ConnectException("Connection refused")))
        assertEquals(NetworkFailureType.READ_TIMEOUT, BackendHealthManager.classifyFailure(SocketTimeoutException("Read timed out")))
        assertEquals(NetworkFailureType.NETWORK_UNAVAILABLE, BackendHealthManager.classifyFailure(IOException("Network unreachable")))
    }

    @Test
    fun backendHealthManager_tripsCircuitBreakerAfterConsecutiveFailures() {
        assertEquals(BackendHealthState.AVAILABLE, BackendHealthManager.healthState.value)

        // 1st failure -> DEGRADED
        BackendHealthManager.recordFailure(SocketTimeoutException("Timeout 1"))
        assertEquals(BackendHealthState.DEGRADED, BackendHealthManager.healthState.value)
        assertTrue(BackendHealthManager.isCloudAvailable())

        // 2nd failure -> DEGRADED
        BackendHealthManager.recordFailure(SocketTimeoutException("Timeout 2"))
        assertEquals(BackendHealthState.DEGRADED, BackendHealthManager.healthState.value)

        // 3rd failure -> trips circuit breaker to UNAVAILABLE
        BackendHealthManager.recordFailure(SocketTimeoutException("Timeout 3"))
        assertEquals(BackendHealthState.UNAVAILABLE, BackendHealthManager.healthState.value)
        assertFalse(BackendHealthManager.isCloudAvailable())
    }

    @Test
    fun backendHealthManager_recoversAutomaticallyOnSuccess() {
        // Trip circuit breaker
        BackendHealthManager.recordFailure(SocketTimeoutException("1"))
        BackendHealthManager.recordFailure(SocketTimeoutException("2"))
        BackendHealthManager.recordFailure(SocketTimeoutException("3"))
        assertEquals(BackendHealthState.UNAVAILABLE, BackendHealthManager.healthState.value)

        // Next successful request restores AVAILABLE without restarting app
        BackendHealthManager.recordSuccess()
        assertEquals(BackendHealthState.AVAILABLE, BackendHealthManager.healthState.value)
        assertTrue(BackendHealthManager.isCloudAvailable())
        assertEquals(0, BackendHealthManager.diagnostics.value.consecutiveFailures)
    }
}
