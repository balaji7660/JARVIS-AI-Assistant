package com.jarvis.assistant.automation

/**
 * Sanitized, bounded representation of a visible UI element on the screen.
 * Passwords, auth tokens, and sensitive credential fields are redacted or excluded.
 */
data class VisibleElement(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val scrollable: Boolean = false,
    val isPassword: Boolean = false
)

/**
 * Bounded snapshot of the currently active foreground window.
 */
data class ScreenSnapshot(
    val packageName: String? = null,
    val elements: List<VisibleElement> = emptyList(),
    val isTruncated: Boolean = false
)
