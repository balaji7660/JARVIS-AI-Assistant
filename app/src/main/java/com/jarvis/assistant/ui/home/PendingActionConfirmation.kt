package com.jarvis.assistant.ui.home

data class PendingActionConfirmation(
    val toolName: String,
    val description: String,
    val arguments: Map<String, Any?> = emptyMap(),
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit
)
