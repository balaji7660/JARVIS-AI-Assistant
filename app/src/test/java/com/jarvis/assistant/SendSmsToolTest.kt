package com.jarvis.assistant

import com.jarvis.assistant.tools.impl.CallContactTool
import com.jarvis.assistant.tools.impl.SendSmsTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendSmsToolTest {

    @Test
    fun sendSms_validContact_dispatchesSuccessfully() = runBlocking {
        var sentRecipient: String? = null
        var sentMessage: String? = null

        val tool = SendSmsTool(
            contactLookupOverride = { query ->
                if (query.equals("Daddy", ignoreCase = true)) {
                    listOf(CallContactTool.ContactInfo("Daddy", "+1234567890"))
                } else {
                    emptyList()
                }
            },
            smsActionOverride = { number, text ->
                sentRecipient = number
                sentMessage = text
                true
            }
        )

        val result = tool.execute(mapOf("contactName" to "Daddy", "message" to "I'll be home at 8."))

        assertTrue(result.success)
        assertTrue(result.message.contains("Daddy"))
        assertEquals("+1234567890", sentRecipient)
        assertEquals("I'll be home at 8.", sentMessage)
    }

    @Test
    fun sendSms_multipleMatches_requestsDisambiguation() = runBlocking {
        val tool = SendSmsTool(
            contactLookupOverride = {
                listOf(
                    CallContactTool.ContactInfo("Daddy Mobile", "+111"),
                    CallContactTool.ContactInfo("Daddy Office", "+222")
                )
            },
            smsActionOverride = { _, _ -> true }
        )

        val result = tool.execute(mapOf("contactName" to "Daddy", "message" to "Hello"))

        assertFalse(result.success)
        assertTrue(result.message.contains("multiple contacts matching"))
    }

    @Test
    fun sendSms_missingMessage_returnsError() = runBlocking {
        val tool = SendSmsTool(
            contactLookupOverride = { emptyList() },
            smsActionOverride = { _, _ -> true }
        )

        val result = tool.execute(mapOf("contactName" to "Daddy"))

        assertFalse(result.success)
        assertTrue(result.message.contains("What message"))
    }

    @Test
    fun sendSms_missingRecipient_returnsError() = runBlocking {
        val tool = SendSmsTool(
            contactLookupOverride = { emptyList() },
            smsActionOverride = { _, _ -> true }
        )

        val result = tool.execute(mapOf("message" to "Hello"))

        assertFalse(result.success)
        assertTrue(result.message.contains("Who should I send"))
    }
}
