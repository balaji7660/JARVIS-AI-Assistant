package com.jarvis.assistant.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEntityTest {

    @Test
    fun memoryEntity_defaultValues() {
        val memory = MemoryEntity(
            content = "User prefers dark mode.",
            category = MemoryCategory.PREFERENCE.name
        )

        assertEquals(0L, memory.id)
        assertEquals("User prefers dark mode.", memory.content)
        assertEquals(MemoryCategory.PREFERENCE.name, memory.category)
        assertEquals(MemoryCategory.PREFERENCE, MemoryCategory.fromString(memory.category))
        assertTrue(memory.isActive)
        assertTrue(memory.createdAt > 0)
        assertEquals(memory.createdAt, memory.updatedAt)
    }

    @Test
    fun memoryCategories_allPresent() {
        val expected = listOf(
            MemoryCategory.PREFERENCE,
            MemoryCategory.PERSONAL_CONTEXT,
            MemoryCategory.PROJECT,
            MemoryCategory.INSTRUCTION,
            MemoryCategory.OTHER
        )

        assertEquals(5, MemoryCategory.entries.size)
        expected.forEach { category ->
            assertNotNull(MemoryCategory.valueOf(category.name))
        }
    }
}
