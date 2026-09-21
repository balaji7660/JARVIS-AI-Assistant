package com.jarvis.assistant.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Valid memory categories approved for JARVIS persistent memory.
 * Arbitrary uncontrolled categories are prohibited.
 */
enum class MemoryCategory {
    PREFERENCE,
    PERSONAL_CONTEXT,
    PROJECT,
    INSTRUCTION,
    OTHER;

    companion object {
        fun fromString(value: String): MemoryCategory {
            return entries.find { it.name.equals(value.trim(), ignoreCase = true) } ?: OTHER
        }
    }
}

/**
 * Room entity representing an explicit, user-confirmed persistent memory.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val category: String,

    val content: String,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis(),

    val source: String = "user_explicit",

    val isActive: Boolean = true
)
