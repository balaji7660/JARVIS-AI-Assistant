package com.jarvis.assistant

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.tools.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    private val fakeTool = object : JarvisTool {
        override val name: String = "test_tool"
        override val description: String = "Test tool description"
        override val riskLevel: RiskLevel = RiskLevel.LOW

        override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
            return ToolResult(success = true, message = "Executed")
        }
    }

    @Before
    fun setUp() {
        registry = ToolRegistry()
    }

    @Test
    fun registerAndLookupTool_returnsRegisteredTool() {
        registry.register(fakeTool)

        assertTrue(registry.contains("test_tool"))
        val retrieved = registry.get("test_tool")
        assertNotNull(retrieved)
        assertEquals("test_tool", retrieved?.name)
        assertEquals(RiskLevel.LOW, retrieved?.riskLevel)
    }

    @Test
    fun lookupUnknownTool_returnsNull() {
        assertFalse(registry.contains("unknown_tool"))
        assertNull(registry.get("unknown_tool"))
    }

    @Test
    fun getAll_returnsAllRegisteredTools() {
        registry.register(fakeTool)
        val all = registry.getAll()

        assertEquals(1, all.size)
        assertEquals("test_tool", all[0].name)
    }

    @Test
    fun clear_removesAllTools() {
        registry.register(fakeTool)
        assertEquals(1, registry.getAll().size)

        registry.clear()
        assertEquals(0, registry.getAll().size)
        assertFalse(registry.contains("test_tool"))
    }
}
