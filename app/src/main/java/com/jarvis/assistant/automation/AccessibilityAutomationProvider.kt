package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import kotlinx.coroutines.withTimeoutOrNull
import java.util.LinkedList

class AccessibilityAutomationProvider(
    private val serviceProvider: () -> JarvisAccessibilityService? = { JarvisAccessibilityService.getInstance() }
) : AndroidAutomationProvider {

    companion object {
        private const val TAG = "AccessAutoProvider"
        const val MAX_NODES_INSPECTED = 300
        const val MAX_ELEMENTS_RETURNED = 100
        const val MAX_TEXT_LENGTH = 500
        const val ACTION_TIMEOUT_MS = 5000L
    }

    override suspend fun execute(action: AutomationAction): ToolResult {
        val result = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
            val service = serviceProvider.invoke()
            if (service == null) {
                return@withTimeoutOrNull ToolResult(
                    success = false,
                    message = "JARVIS Accessibility access is disabled."
                )
            }

            when (action) {
                is AutomationAction.ReadVisibleScreen -> handleReadVisibleScreen(service)
                is AutomationAction.ClickText -> handleClickText(service, action.text)
                is AutomationAction.ClickView -> handleClickView(service, action.viewId)
                is AutomationAction.TypeText -> handleTypeText(service, action.text)
                is AutomationAction.ScrollForward -> handleScroll(service, forward = true)
                is AutomationAction.ScrollBackward -> handleScroll(service, forward = false)
                is AutomationAction.PressBack -> handlePressBack(service)
            }
        }

        return result ?: ToolResult(
            success = false,
            message = "Accessibility action timed out after 5 seconds."
        )
    }

    private fun handleReadVisibleScreen(service: JarvisAccessibilityService): ToolResult {
        val root = service.getRootInActiveWindowSafe()
            ?: return ToolResult(success = false, message = "Unable to inspect active window.")

        val snapshot = traverseAndSanitize(root)
        val summary = snapshot.elements
            .filter { !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank() }
            .joinToString("; ") { el ->
                val label = el.text ?: el.contentDescription ?: ""
                val type = if (el.clickable) "[Button: $label]" else "[Text: $label]"
                type
            }

        return ToolResult(
            success = true,
            message = if (summary.isNotBlank()) "Visible screen: $summary" else "Screen is active but no readable text controls were detected.",
            data = mapOf(
                "packageName" to (snapshot.packageName ?: "unknown"),
                "elementCount" to snapshot.elements.size,
                "isTruncated" to snapshot.isTruncated,
                "summary" to summary
            )
        )
    }

    private fun handleClickText(service: JarvisAccessibilityService, targetText: String): ToolResult {
        val trimmed = targetText.trim()
        if (trimmed.isBlank()) {
            return ToolResult(success = false, message = "Target click text cannot be empty.")
        }

        val root = service.getRootInActiveWindowSafe()
            ?: return ToolResult(success = false, message = "Active window is not available to click.")

        val matchingClickableNodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(root)
        var inspectedCount = 0

        while (queue.isNotEmpty() && inspectedCount < MAX_NODES_INSPECTED) {
            val node = queue.poll() ?: continue
            inspectedCount++

            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val matches = text.contains(trimmed, ignoreCase = true) || desc.contains(trimmed, ignoreCase = true)

            if (matches) {
                // Find clickable ancestor or self
                val clickableNode = findClickableTarget(node)
                if (clickableNode != null && !matchingClickableNodes.contains(clickableNode)) {
                    matchingClickableNodes.add(clickableNode)
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return when {
            matchingClickableNodes.isEmpty() -> {
                ToolResult(
                    success = false,
                    message = "No visible clickable element matching '$targetText' was found."
                )
            }
            matchingClickableNodes.size > 1 -> {
                ToolResult(
                    success = false,
                    message = "Multiple visible controls match '$targetText'."
                )
            }
            else -> {
                val targetNode = matchingClickableNodes[0]
                val performed = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (performed) {
                    ToolResult(success = true, message = "Clicked $targetText.")
                } else {
                    ToolResult(success = false, message = "Failed to perform click on '$targetText'.")
                }
            }
        }
    }

    private fun handleClickView(service: JarvisAccessibilityService, viewId: String): ToolResult {
        val trimmed = viewId.trim()
        if (trimmed.isBlank()) {
            return ToolResult(success = false, message = "View ID cannot be empty.")
        }

        val root = service.getRootInActiveWindowSafe()
            ?: return ToolResult(success = false, message = "Active window is not available.")

        val nodes = root.findAccessibilityNodeInfosByViewId(trimmed)
        if (nodes.isNullOrEmpty()) {
            return ToolResult(success = false, message = "No visible element with view ID '$viewId' was found.")
        }

        for (node in nodes) {
            val clickableNode = findClickableTarget(node) ?: node
            if (clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return ToolResult(success = true, message = "Clicked view $viewId.")
            }
        }

        return ToolResult(success = false, message = "Failed to click view with ID '$viewId'.")
    }

    private fun handleTypeText(service: JarvisAccessibilityService, text: String): ToolResult {
        if (text.length > MAX_TEXT_LENGTH) {
            return ToolResult(success = false, message = "Input text exceeds maximum allowed length of $MAX_TEXT_LENGTH characters.")
        }

        val root = service.getRootInActiveWindowSafe()
            ?: return ToolResult(success = false, message = "Active window is not available.")

        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFocusedEditableNode(root)

        if (focusedNode == null) {
            return ToolResult(
                success = false,
                message = "No focused editable input field found to type into."
            )
        }

        if (focusedNode.isPassword) {
            return ToolResult(
                success = false,
                message = "Typing into password or sensitive credential fields is blocked for security."
            )
        }

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val performed = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        return if (performed) {
            ToolResult(success = true, message = "Typed text successfully.")
        } else {
            ToolResult(success = false, message = "Failed to input text into focused field.")
        }
    }

    private fun handleScroll(service: JarvisAccessibilityService, forward: Boolean): ToolResult {
        val root = service.getRootInActiveWindowSafe()
            ?: return ToolResult(success = false, message = "Active window is not available to scroll.")

        val scrollableNode = findScrollableNode(root)
            ?: return ToolResult(success = false, message = "No visible scrollable container found.")

        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val performed = scrollableNode.performAction(action)

        val dir = if (forward) "forward" else "backward"
        return if (performed) {
            ToolResult(success = true, message = "Scrolled $dir.")
        } else {
            ToolResult(success = false, message = "Failed to scroll $dir.")
        }
    }

    private fun handlePressBack(service: JarvisAccessibilityService): ToolResult {
        val performed = service.performGlobalActionSafe(AccessibilityService.GLOBAL_ACTION_BACK)
        return if (performed) {
            ToolResult(success = true, message = "Back action executed.")
        } else {
            ToolResult(success = false, message = "Failed to execute back action.")
        }
    }

    private fun traverseAndSanitize(root: AccessibilityNodeInfo): ScreenSnapshot {
        val elements = mutableListOf<VisibleElement>()
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(root)
        var inspectedCount = 0
        var isTruncated = false

        while (queue.isNotEmpty()) {
            if (inspectedCount >= MAX_NODES_INSPECTED || elements.size >= MAX_ELEMENTS_RETURNED) {
                isTruncated = true
                break
            }

            val node = queue.poll() ?: continue
            inspectedCount++

            val isPassword = node.isPassword
            val rawText = node.text?.toString()
            val sanitizedText = when {
                isPassword -> "[REDACTED_PASSWORD]"
                rawText != null -> rawText.take(MAX_TEXT_LENGTH).trim()
                else -> null
            }

            val rawDesc = node.contentDescription?.toString()
            val sanitizedDesc = when {
                isPassword -> null
                rawDesc != null -> rawDesc.take(MAX_TEXT_LENGTH).trim()
                else -> null
            }

            if (!sanitizedText.isNullOrBlank() || !sanitizedDesc.isNullOrBlank() || node.isClickable) {
                elements.add(
                    VisibleElement(
                        text = sanitizedText,
                        contentDescription = sanitizedDesc,
                        viewId = node.viewIdResourceName,
                        className = node.className?.toString(),
                        clickable = node.isClickable,
                        enabled = node.isEnabled,
                        scrollable = node.isScrollable,
                        isPassword = isPassword
                    )
                )
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return ScreenSnapshot(
            packageName = root.packageName?.toString(),
            elements = elements,
            isTruncated = isTruncated
        )
    }

    private fun findClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var curr: AccessibilityNodeInfo? = node
        while (curr != null) {
            if (curr.isClickable && curr.isEnabled) {
                return curr
            }
            curr = curr.parent
        }
        return null
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < MAX_NODES_INSPECTED) {
            val node = queue.poll() ?: continue
            count++

            if (node.isFocused && node.isEditable) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = LinkedList<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < MAX_NODES_INSPECTED) {
            val node = queue.poll() ?: continue
            count++

            if (node.isScrollable) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}
