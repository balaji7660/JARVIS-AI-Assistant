package com.jarvis.assistant

import com.jarvis.assistant.tools.impl.CallContactTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallContactToolTest {

    @Test
    fun singleMatch_initiatesCallSuccessfully() = runBlocking {
        var dialedNumber: String? = null
        val tool = CallContactTool(
            context = null,
            contactLookupOverride = { query ->
                if (query.equals("Daddy", ignoreCase = true)) {
                    listOf(CallContactTool.ContactInfo("Daddy", "+1234567890"))
                } else {
                    emptyList()
                }
            },
            callActionOverride = { number ->
                dialedNumber = number
                true
            }
        )

        val result = tool.execute(mapOf("contactName" to "Daddy"))

        assertTrue(result.success)
        assertTrue(result.message.contains("Calling Daddy"))
        assertEquals("+1234567890", dialedNumber)
    }

    @Test
    fun multipleMatches_asksUserToDisambiguate() = runBlocking {
        val tool = CallContactTool(
            context = null,
            contactLookupOverride = { query ->
                listOf(
                    CallContactTool.ContactInfo("Daddy Mobile", "+1111111111"),
                    CallContactTool.ContactInfo("Daddy Office", "+2222222222")
                )
            },
            callActionOverride = { true }
        )

        val result = tool.execute(mapOf("contactName" to "Daddy"))

        assertFalse(result.success)
        assertTrue(result.message.contains("Daddy Mobile") && result.message.contains("Daddy Office"))
        assertTrue(result.message.contains("multiple contacts matching"))
    }

    @Test
    fun contactNotFound_reportsClearMessage() = runBlocking {
        val tool = CallContactTool(
            context = null,
            contactLookupOverride = { emptyList() },
            callActionOverride = { true }
        )

        val result = tool.execute(mapOf("contactName" to "NonExistentPerson"))

        assertFalse(result.success)
        assertTrue(result.message.contains("couldn't find") && result.message.contains("NonExistentPerson"))
    }

    @Test
    fun missingContactName_returnsError() = runBlocking {
        val tool = CallContactTool(
            context = null,
            contactLookupOverride = { emptyList() },
            callActionOverride = { true }
        )

        val result = tool.execute(emptyMap())

        assertFalse(result.success)
        assertTrue(result.message.contains("specify the name"))
    }

    @Test
    fun missingPermission_returnsNeedsPermission() = runBlocking {
        val tool = CallContactTool(
            context = null,
            contactLookupOverride = null,
            callActionOverride = null
        )

        val result = tool.execute(mapOf("contactName" to "Daddy"))

        assertFalse(result.success)
        assertTrue(result.message.contains("permission"))
        assertEquals("READ_CONTACTS", result.data["needsPermission"])
    }

    @Test
    fun privacyCheck_contactDataNotExposedRemotely() = runBlocking {
        val tool = CallContactTool(
            context = null,
            contactLookupOverride = { listOf(CallContactTool.ContactInfo("Mom", "+9876543210")) },
            callActionOverride = { true }
        )

        val result = tool.execute(mapOf("contactName" to "Mom"))

        assertTrue(result.success)
        // Data map should NOT leak raw contacts address book
        assertFalse(result.data.containsKey("allContacts"))
        assertEquals("Mom", result.data["contactName"])
    }
}
