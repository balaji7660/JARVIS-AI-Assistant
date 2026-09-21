package com.jarvis.assistant.tools.automation

import com.jarvis.assistant.tools.ToolResult

/**
 * Architectural interface for future secure privileged actions (e.g. Shizuku in later milestones).
 * In Milestone 4, this interface is non-operational; no root, Shizuku, or hidden privilege escalations exist.
 */
interface PrivilegedActionProvider {
    val isAvailable: Boolean
    suspend fun executePrivileged(actionName: String, params: Map<String, Any?>): ToolResult
}
