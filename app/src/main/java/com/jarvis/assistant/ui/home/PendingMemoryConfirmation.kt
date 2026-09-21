package com.jarvis.assistant.ui.home

import com.jarvis.assistant.memory.MemoryCategory

/**
 * Encapsulates an explicit user memory save request that requires UI confirmation.
 */
data class PendingMemoryConfirmation(
    val content: String,
    val category: MemoryCategory,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit
)
