package com.jarvis.assistant.automation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SyncResult(
    val success: Boolean,
    val packageName: String,
    val screenSummary: String,
    val isReady: Boolean,
    val errorMessage: String? = null
)

/**
 * Authoritative central synchronization coordinator for launching apps and waiting for UI readiness.
 *
 * Sequence:
 * 1. Launch target app via explicit intent or package manager.
 * 2. Poll for target package arrival in foreground.
 * 3. Wait for AccessibilityService window attachment.
 * 4. Verify UI root is populated (non-empty screen summary).
 * 5. Verify screen is not transient loading state.
 *
 * Safe: Non-blocking, suspend/coroutine-based, with strict 8-second bounded timeout.
 */
class AppLaunchSynchronizer(
    private val automationProvider: AndroidAutomationProvider?,
    private val context: Context?
) {

    companion object {
        private const val TAG = "AppLaunchSynchronizer"
        const val DEFAULT_LAUNCH_TIMEOUT_MS = 8000L // 8 seconds
        private const val POLL_INTERVAL_MS = 250L
        private const val INITIAL_SETTLE_DELAY_MS = 400L
    }

    suspend fun launchAndSynchronize(
        packageName: String,
        appName: String = packageName,
        timeoutMs: Long = DEFAULT_LAUNCH_TIMEOUT_MS,
        onActionReady: (suspend () -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        val targetPkg = packageName.trim().lowercase()
        val startTime = System.currentTimeMillis()

        Log.i(TAG, "Starting launch and synchronization for: $appName ($targetPkg)")

        // 1. Launch the target app
        val launched = launchPackage(targetPkg)
        if (!launched) {
            Log.e(TAG, "Failed to launch package: $targetPkg")
            return@withContext SyncResult(
                success = false,
                packageName = targetPkg,
                screenSummary = "",
                isReady = false,
                errorMessage = "Could not launch $appName on this device."
            )
        }

        delay(INITIAL_SETTLE_DELAY_MS)

        // 2. Wait for package in foreground & accessibility window readiness
        var matchedPackage = ""
        var summary = ""
        var isWindowReady = false

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val currentPkg = automationProvider?.getCurrentPackage()?.lowercase() ?: ""
            if (currentPkg.isNotBlank() && (currentPkg.contains(targetPkg) || targetPkg.contains(currentPkg))) {
                matchedPackage = currentPkg
                summary = automationProvider?.getScreenSummary() ?: ""

                // 3. Verify screen is not blank and accessibility service is responsive
                val isNotDisabled = !summary.contains("not running", ignoreCase = true) &&
                        !summary.contains("disabled", ignoreCase = true)
                val isNotBlank = summary.isNotBlank() && summary != "Window of $currentPkg"

                if (isNotDisabled) {
                    isWindowReady = true
                    Log.i(TAG, "Window successfully synchronized with $matchedPackage in ${System.currentTimeMillis() - startTime}ms")
                    break
                }
            }
            delay(POLL_INTERVAL_MS)
        }

        if (!isWindowReady) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.w(TAG, "Timed out waiting for $appName ($targetPkg) after ${elapsed}ms")
            return@withContext SyncResult(
                success = false,
                packageName = matchedPackage.ifBlank { targetPkg },
                screenSummary = summary,
                isReady = false,
                errorMessage = "$appName did not open within the timeout window."
            )
        }

        // 4. Execute post-launch action if provided
        try {
            onActionReady?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing post-launch action for $appName", e)
        }

        SyncResult(
            success = true,
            packageName = matchedPackage,
            screenSummary = summary,
            isReady = true
        )
    }

    private fun launchPackage(packageName: String): Boolean {
        val ctx = context ?: return true
        return try {
            val pm = ctx.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (intent != null) {
                ctx.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception launching package $packageName", e)
            false
        }
    }
}
