package com.jarvis.assistant.tools

import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry responsible for registering and retrieving approved JARVIS tools.
 * Tools not registered here cannot be invoked under any circumstances.
 */
class ToolRegistry {

    private val tools = ConcurrentHashMap<String, JarvisTool>()

    fun register(tool: JarvisTool) {
        tools[tool.name] = tool
    }

    fun get(name: String): JarvisTool? {
        return tools[name]
    }

    fun contains(name: String): Boolean {
        return tools.containsKey(name)
    }

    fun getAll(): List<JarvisTool> {
        return tools.values.toList()
    }

    fun clear() {
        tools.clear()
    }
}
