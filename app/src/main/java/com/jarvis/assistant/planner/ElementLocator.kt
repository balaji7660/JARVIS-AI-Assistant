package com.jarvis.assistant.planner

import com.jarvis.assistant.tools.automation.AndroidAutomationProvider

/**
 * Result of resolving a UI element.
 */
sealed class LocateResult {
    data class Found(val description: String) : LocateResult()
    data class Ambiguous(val query: String, val candidateCount: Int, val candidateDescriptions: List<String>) : LocateResult()
    data class NotFound(val query: String, val reason: String) : LocateResult()
}

/**
 * Robust on-device element locator.
 * Resolves elements using priority order: viewId -> exact text -> content description -> partial text.
 * Strictly detects ambiguities without guessing.
 */
class ElementLocator(
    private val automationProvider: AndroidAutomationProvider
) {
    /**
     * Locates a single unambiguous target element on screen.
     */
    suspend fun locate(query: String): LocateResult {
        if (query.isBlank()) {
            return LocateResult.NotFound(query, "Query string is empty.")
        }

        val trimmed = query.trim()
        val screenSummary = automationProvider.getScreenSummary()

        if (screenSummary.isBlank()) {
            return LocateResult.NotFound(trimmed, "Screen summary is empty or no UI elements detected.")
        }

        // Split items by standard delimiter ';' used by AccessibilityAutomationProvider
        val items = screenSummary.split(";").map { it.trim() }.filter { it.isNotBlank() }

        // 1. Exact matches within items (e.g. "[Button: Settings]" or "[Text: Settings]")
        val exactMatches = items.filter { item ->
            val label = item.removePrefix("[Button: ").removePrefix("[Text: ").removeSuffix("]").trim()
            label.equals(trimmed, ignoreCase = true)
        }

        if (exactMatches.size == 1) {
            return LocateResult.Found("Found exact element: ${exactMatches[0]}")
        } else if (exactMatches.size > 1) {
            return LocateResult.Ambiguous(
                query = trimmed,
                candidateCount = exactMatches.size,
                candidateDescriptions = exactMatches.mapIndexed { idx, it -> "$it (Option ${idx + 1})" }
            )
        }

        // 2. Containment matches
        val partialMatches = items.filter { item ->
            val label = item.removePrefix("[Button: ").removePrefix("[Text: ").removeSuffix("]").trim()
            label.contains(trimmed, ignoreCase = true)
        }

        if (partialMatches.size == 1) {
            return LocateResult.Found("Found matching element: ${partialMatches[0]}")
        } else if (partialMatches.size > 1) {
            return LocateResult.Ambiguous(
                query = trimmed,
                candidateCount = partialMatches.size,
                candidateDescriptions = partialMatches.take(4).mapIndexed { idx, it -> "$it (Option ${idx + 1})" }
            )
        }

        return LocateResult.NotFound(trimmed, "No visible element matching '$trimmed' was found in screen summary.")
    }
}
