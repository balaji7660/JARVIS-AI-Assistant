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

    private val packageCandidatesMap = mapOf(
        "youtube" to listOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.mango",
            "app.revanced.android.youtube",
            "com.vanced.android.youtube"
        ),
        "chrome" to listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "com.google.android.apps.chrome"
        ),
        "settings" to listOf(
            "com.android.settings",
            "com.miui.securitycenter"
        ),
        "calculator" to listOf(
            "com.google.android.calculator",
            "com.miui.calculator",
            "com.android.calculator2",
            "com.sec.android.app.popupcalculator",
            "com.coloros.calculator"
        ),
        "camera" to listOf(
            "com.android.camera2",
            "com.android.camera",
            "com.google.android.GoogleCamera"
        ),
        "maps" to listOf(
            "com.google.android.apps.maps"
        ),
        "clock" to listOf(
            "com.google.android.deskclock",
            "com.android.deskclock"
        ),
        "calendar" to listOf(
            "com.google.android.calendar",
            "com.android.calendar"
        ),
        "contacts" to listOf(
            "com.google.android.contacts",
            "com.android.contacts"
        ),
        "messages" to listOf(
            "com.google.android.apps.messaging",
            "com.android.mms"
        ),
        "phone" to listOf(
            "com.google.android.dialer",
            "com.android.dialer"
        ),
        "dialer" to listOf(
            "com.google.android.dialer",
            "com.android.dialer"
        ),
        "whatsapp" to listOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
    )

    private val sensitiveKeywords = listOf(
        "bank", "wallet", "paytm", "gpay", "phonepe", "crypto",
        "binance", "authenticator", "password", "keychain", "packageinstaller"
    )

    private fun normalizeAppName(input: String): String {
        var clean = input.lowercase().trim()
        clean = clean.replace("\"", "").replace("'", "").replace("`", "")
        if (clean.startsWith("open ")) clean = clean.removePrefix("open ").trim()
        if (clean.startsWith("launch ")) clean = clean.removePrefix("launch ").trim()
        if (clean.startsWith("start ")) clean = clean.removePrefix("start ").trim()
        if (clean.startsWith("the ")) clean = clean.removePrefix("the ").trim()
        if (clean.endsWith(" app")) clean = clean.removeSuffix(" app").trim()
        if (clean.endsWith(" application")) clean = clean.removeSuffix(" application").trim()
        return clean.trim()
    }

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val rawName = (arguments["appName"]
            ?: arguments["packageName"]
            ?: arguments["package"]
            ?: arguments["name"]
            ?: arguments["app"]
            ?: arguments["app_name"]
            ?: arguments["query"])
            ?.toString()?.trim()

        if (rawName.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "Application name argument is missing or empty.",
                data = mapOf("directResponse" to true)
            )
        }

        // Custom test launcher override
        if (customLauncher != null) {
            val success = customLauncher.invoke(rawName)
            return if (success) {
                ToolResult(
                    success = true,
                    message = "Opening $rawName, boss.",
                    data = mapOf("app" to rawName, "directResponse" to true)
                )
            } else {
                ToolResult(
                    success = false,
                    message = "I couldn't find $rawName on this device, boss.",
                    data = mapOf("app" to rawName, "directResponse" to true)
                )
            }
        }

        if (context == null) {
            return ToolResult(
                success = false,
                message = "Android context not available to launch application.",
                data = mapOf("app" to rawName, "directResponse" to true)
            )
        }

        val pm: PackageManager = context.packageManager
        val normalized = normalizeAppName(rawName)

        // Sensitive application check
        if (sensitiveKeywords.any { normalized.contains(it) }) {
            return ToolResult(
                success = false,
                message = "Launching sensitive applications like $rawName is protected and cannot be done automatically, boss.",
                data = mapOf("app" to rawName, "isSensitive" to true, "directResponse" to true)
            )
        }

        var launchIntent: Intent? = null
        var resolvedPackage: String? = null
        var resolvedLabel: String = rawName

        // Strategy 1: Check known candidate packages (e.g. YouTube, Chrome, Calculator, Settings)
        val candidates = packageCandidatesMap[normalized]
        if (candidates != null) {
            for (pkg in candidates) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    launchIntent = intent
                    resolvedPackage = pkg
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        resolvedLabel = pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        resolvedLabel = rawName
                    }
                    break
                }
            }
        }

        // Strategy 2: If input looks like an explicit package name (contains a dot)
        if (launchIntent == null && rawName.contains('.')) {
            val intent = pm.getLaunchIntentForPackage(rawName)
            if (intent != null) {
                launchIntent = intent
                resolvedPackage = rawName
                resolvedLabel = rawName
            }
        }

        // Strategy 3: Dynamic launcher activity lookup via PackageManager
        if (launchIntent == null) {
            val launcherQuery = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(launcherQuery, 0)

            // 3a. Exact app label match
            for (info in resolveInfos) {
                val label = info.loadLabel(pm).toString().trim()
                val normLabel = normalizeAppName(label)
                if (label.equals(rawName, ignoreCase = true) || normLabel.equals(normalized, ignoreCase = true)) {
                    val pkg = info.activityInfo.packageName
                    launchIntent = pm.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setClassName(pkg, info.activityInfo.name)
                    }
                    resolvedPackage = pkg
                    resolvedLabel = label
                    break
                }
            }

            // 3b. Starts with / Substring / Package name contains
            if (launchIntent == null) {
                for (info in resolveInfos) {
                    val label = info.loadLabel(pm).toString().trim()
                    val normLabel = normalizeAppName(label)
                    val pkg = info.activityInfo.packageName.lowercase()

                    if (normLabel.startsWith(normalized) || normLabel.contains(normalized) ||
                        normalized.contains(normLabel) || pkg.contains(normalized)) {
                        val realPkg = info.activityInfo.packageName
                        launchIntent = pm.getLaunchIntentForPackage(realPkg) ?: Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            setClassName(realPkg, info.activityInfo.name)
                        }
                        resolvedPackage = realPkg
                        resolvedLabel = label
                        break
                    }
                }
            }
        }

        // Strategy 4: Fallback to installed applications query
        if (launchIntent == null) {
            try {
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (appInfo in installedApps) {
                    val label = pm.getApplicationLabel(appInfo).toString().trim()
                    val normLabel = normalizeAppName(label)
                    val pkg = appInfo.packageName.lowercase()

                    if (normLabel.equals(normalized, ignoreCase = true) ||
                        normLabel.contains(normalized) ||
                        pkg.contains(normalized)) {
                        val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                        if (intent != null) {
                            launchIntent = intent
                            resolvedPackage = appInfo.packageName
                            resolvedLabel = label
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Strategy 5: System intent fallbacks (Settings, Camera, etc.)
        if (launchIntent == null) {
            when {
                normalized == "settings" || normalized.contains("setting") -> {
                    launchIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
                    resolvedPackage = "com.android.settings"
                    resolvedLabel = "Settings"
                }
            }
        }

        // Strategy 6: Verify and Launch
        return if (launchIntent != null) {
            try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ToolResult(
                    success = true,
                    message = "Opening $resolvedLabel, boss.",
                    data = mapOf(
                        "app" to resolvedLabel,
                        "package" to (resolvedPackage ?: "unknown"),
                        "directResponse" to true
                    )
                )
            } catch (e: Exception) {
                ToolResult(
                    success = false,
                    message = "Failed to launch $resolvedLabel: ${e.message}",
                    data = mapOf("app" to resolvedLabel, "directResponse" to true)
                )
            }
        } else {
            ToolResult(
                success = false,
                message = "I couldn't find $rawName on this device, boss.",
                data = mapOf("app" to rawName, "directResponse" to true)
            )
        }
    }
}
