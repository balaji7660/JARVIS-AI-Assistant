package com.jarvis.assistant.tools.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult

class OpenAppTool(
    private val context: Context? = null,
    private val customLauncher: ((String) -> Boolean)? = null
) : JarvisTool {

    override val name: String = "open_app"
    override val description: String = "Opens an installed Android application."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    private val commonPackageAliases = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "settings" to "com.android.settings",
        "camera" to "com.android.camera2",
        "maps" to "com.google.android.apps.maps",
        "calculator" to "com.google.android.calculator",
        "clock" to "com.google.android.deskclock",
        "calendar" to "com.google.android.calendar",
        "contacts" to "com.android.contacts",
        "messages" to "com.google.android.apps.messaging",
        "dialer" to "com.google.android.dialer",
        "phone" to "com.google.android.dialer"
    )

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val rawName = (arguments["appName"] ?: arguments["packageName"] ?: arguments["package"])
            ?.toString()?.trim()

        if (rawName.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "Application name argument is missing or empty."
            )
        }

        // Custom test launcher override
        if (customLauncher != null) {
            val success = customLauncher.invoke(rawName)
            return if (success) {
                ToolResult(
                    success = true,
                    message = "Opening $rawName, boss.",
                    data = mapOf("app" to rawName)
                )
            } else {
                ToolResult(
                    success = false,
                    message = "I couldn't find $rawName on this device, boss.",
                    data = mapOf("app" to rawName)
                )
            }
        }

        if (context == null) {
            return ToolResult(
                success = false,
                message = "Android context not available to launch application."
            )
        }

        val pm: PackageManager = context.packageManager
        val normalized = rawName.lowercase()

        // 1. Direct package lookup (or from aliases)
        val candidatePackage = commonPackageAliases[normalized] ?: rawName
        var launchIntent = pm.getLaunchIntentForPackage(candidatePackage)

        // 2. Search installed applications by display label
        if (launchIntent == null) {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val label = pm.getApplicationLabel(appInfo).toString()
                if (label.equals(rawName, ignoreCase = true) || label.lowercase().contains(normalized)) {
                    launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) break
                }
            }
        }

        // 3. Launch if found
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ToolResult(
                success = true,
                message = "Opening $rawName, boss.",
                data = mapOf("app" to rawName)
            )
        } else {
            ToolResult(
                success = false,
                message = "I couldn't find $rawName on this device, boss.",
                data = mapOf("app" to rawName)
            )
        }
    }
}
