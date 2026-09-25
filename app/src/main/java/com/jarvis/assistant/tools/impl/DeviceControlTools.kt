package com.jarvis.assistant.tools.impl

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.jarvis.assistant.automation.JarvisAccessibilityService
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult

/**
 * Tool: get_battery_status
 * Checks the device's battery level, charging status, and power connection.
 */
class GetBatteryStatusTool(
    private val context: Context? = null,
    private val batteryLevelOverride: Int? = null,
    private val isChargingOverride: Boolean? = null
) : JarvisTool {

    override val name: String = "get_battery_status"
    override val description: String = "Returns the current battery level percentage and charging status of the device."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (batteryLevelOverride != null) {
            val chargingText = if (isChargingOverride == true) "charging" else "not charging"
            return ToolResult(
                success = true,
                message = "Battery is at $batteryLevelOverride% and is $chargingText, boss.",
                data = mapOf("level" to batteryLevelOverride, "isCharging" to (isChargingOverride ?: false))
            )
        }

        if (context == null) {
            return ToolResult(
                success = false,
                message = "Android context is required to query battery status.",
                data = mapOf("directResponse" to true)
            )
        }

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct: Int = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugSource = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC adapter"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless charger"
            else -> null
        }

        val message = when {
            batteryPct == -1 -> "I couldn't determine your battery level, boss."
            isCharging && plugSource != null -> "Your battery is at $batteryPct% and currently charging via $plugSource, boss."
            isCharging -> "Your battery is at $batteryPct% and currently charging, boss."
            else -> "Your battery is at $batteryPct%, boss."
        }

        return ToolResult(
            success = batteryPct >= 0,
            message = message,
            data = mapOf(
                "level" to batteryPct,
                "isCharging" to isCharging,
                "plugSource" to (plugSource ?: "battery"),
                "directResponse" to true
            )
        )
    }
}

/**
 * Tool: set_volume
 * Adjusts or sets device volume for music/media, ringtone, alarm, or call.
 */
class SetVolumeTool(
    private val context: Context? = null,
    private val volumeActionOverride: ((Int, Int) -> Boolean)? = null
) : JarvisTool {

    override val name: String = "set_volume"
    override val description: String = "Sets the device volume level (0-100%) or adjusts volume up/down/mute."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val streamType = when ((arguments["stream"] as? String)?.lowercase()?.trim()) {
            "ring", "ringtone" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "call", "voice" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }

        val direction = (arguments["direction"] as? String)?.lowercase()?.trim()
        val percent = (arguments["volumePercent"] as? Number)?.toInt()
            ?: (arguments["percent"] as? Number)?.toInt()
            ?: (arguments["level"] as? Number)?.toInt()

        if (volumeActionOverride != null) {
            volumeActionOverride.invoke(streamType, percent ?: 50)
            return ToolResult(true, "Volume updated successfully, boss.", mapOf("directResponse" to true))
        }

        if (audioManager == null) {
            return ToolResult(false, "Audio service unavailable on this device, boss.", mapOf("directResponse" to true))
        }

        val maxVolume = audioManager.getStreamMaxVolume(streamType)

        when {
            direction == "mute" -> {
                audioManager.setStreamVolume(streamType, 0, AudioManager.FLAG_SHOW_UI)
                return ToolResult(true, "Volume muted, boss.", mapOf("volumePercent" to 0, "directResponse" to true))
            }
            direction == "unmute" -> {
                val restored = (maxVolume * 0.4).toInt().coerceAtLeast(1)
                audioManager.setStreamVolume(streamType, restored, AudioManager.FLAG_SHOW_UI)
                return ToolResult(true, "Volume unmuted, boss.", mapOf("volumePercent" to 40, "directResponse" to true))
            }
            direction == "up" -> {
                audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                val current = audioManager.getStreamVolume(streamType)
                val pct = (current * 100) / maxVolume
                return ToolResult(true, "Turned volume up to $pct%, boss.", mapOf("volumePercent" to pct, "directResponse" to true))
            }
            direction == "down" -> {
                audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                val current = audioManager.getStreamVolume(streamType)
                val pct = (current * 100) / maxVolume
                return ToolResult(true, "Turned volume down to $pct%, boss.", mapOf("volumePercent" to pct, "directResponse" to true))
            }
            percent != null -> {
                val clamped = percent.coerceIn(0, 100)
                val targetIndex = (clamped * maxVolume) / 100
                audioManager.setStreamVolume(streamType, targetIndex, AudioManager.FLAG_SHOW_UI)
                return ToolResult(true, "Set volume to $clamped%, boss.", mapOf("volumePercent" to clamped, "directResponse" to true))
            }
            else -> {
                return ToolResult(false, "Please specify a volume percentage or direction (up, down, mute), boss.", mapOf("directResponse" to true))
            }
        }
    }
}

/**
 * Tool: get_volume
 * Returns the current device volume level.
 */
class GetVolumeTool(
    private val context: Context? = null,
    private val mockVolumePct: Int? = null
) : JarvisTool {

    override val name: String = "get_volume"
    override val description: String = "Queries the current volume percentage and mute status."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (mockVolumePct != null) {
            return ToolResult(true, "Current volume is at $mockVolumePct%, boss.", mapOf("volumePercent" to mockVolumePct, "directResponse" to true))
        }

        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult(false, "Audio service unavailable, boss.", mapOf("directResponse" to true))

        val streamType = when ((arguments["stream"] as? String)?.lowercase()?.trim()) {
            "ring", "ringtone" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "call", "voice" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }

        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        val pct = if (max > 0) (current * 100) / max else 0

        return ToolResult(
            success = true,
            message = "Current volume is at $pct%, boss.",
            data = mapOf("volumePercent" to pct, "maxVolume" to max, "currentLevel" to current, "directResponse" to true)
        )
    }
}

/**
 * Tool: set_brightness
 * Sets the device screen brightness (0-100%).
 */
class SetBrightnessTool(
    private val context: Context? = null,
    private val brightnessOverride: ((Int) -> Boolean)? = null
) : JarvisTool {

    override val name: String = "set_brightness"
    override val description: String = "Sets the screen brightness percentage (0-100%)."
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val percent = (arguments["brightnessPercent"] as? Number)?.toInt()
            ?: (arguments["percent"] as? Number)?.toInt()
            ?: (arguments["level"] as? Number)?.toInt()

        if (percent == null) {
            return ToolResult(false, "Please specify a brightness percentage between 0 and 100, boss.", mapOf("directResponse" to true))
        }

        val clamped = percent.coerceIn(0, 100)
        if (brightnessOverride != null) {
            brightnessOverride.invoke(clamped)
            return ToolResult(true, "Brightness set to $clamped%, boss.", mapOf("brightnessPercent" to clamped, "directResponse" to true))
        }

        if (context == null) {
            return ToolResult(false, "Android context required to adjust brightness.", mapOf("directResponse" to true))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
            return ToolResult(
                success = false,
                message = "I need permission to modify system settings to change brightness. I've opened the permission screen for you, boss.",
                data = mapOf("needsPermission" to "WRITE_SETTINGS", "directResponse" to true)
            )
        }

        val targetValue = (clamped * 255) / 100
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                targetValue
            )
            ToolResult(true, "Screen brightness set to $clamped%, boss.", mapOf("brightnessPercent" to clamped, "directResponse" to true))
        } catch (e: Exception) {
            ToolResult(false, "Failed to change brightness: ${e.message}", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: get_brightness
 * Returns the current device brightness percentage.
 */
class GetBrightnessTool(
    private val context: Context? = null,
    private val mockBrightnessPct: Int? = null
) : JarvisTool {

    override val name: String = "get_brightness"
    override val description: String = "Queries the current screen brightness percentage."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (mockBrightnessPct != null) {
            return ToolResult(true, "Current screen brightness is at $mockBrightnessPct%, boss.", mapOf("brightnessPercent" to mockBrightnessPct, "directResponse" to true))
        }

        if (context == null) {
            return ToolResult(false, "Android context required.", mapOf("directResponse" to true))
        }

        return try {
            val value = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            val pct = (value * 100) / 255
            ToolResult(true, "Current screen brightness is at $pct%, boss.", mapOf("brightnessPercent" to pct, "directResponse" to true))
        } catch (e: Exception) {
            ToolResult(false, "Could not determine screen brightness, boss.", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: toggle_flashlight
 * Turns the device torch/flashlight on, off, or toggles state.
 */
class ToggleFlashlightTool(
    private val context: Context? = null,
    private val torchOverride: ((Boolean) -> Boolean)? = null
) : JarvisTool {

    override val name: String = "toggle_flashlight"
    override val description: String = "Turns the device flashlight on, off, or toggles it."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    companion object {
        @Volatile
        var isTorchOn: Boolean = false
    }

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val action = (arguments["action"] as? String)?.lowercase()?.trim()
        val enabledArg = arguments["enabled"] as? Boolean

        val targetState = when {
            enabledArg != null -> enabledArg
            action == "on" || action == "true" -> true
            action == "off" || action == "false" -> false
            else -> !isTorchOn
        }

        if (torchOverride != null) {
            torchOverride.invoke(targetState)
            isTorchOn = targetState
            val stateText = if (targetState) "on" else "off"
            return ToolResult(true, "Flashlight turned $stateText, boss.", mapOf("isTorchOn" to targetState, "directResponse" to true))
        }

        val cameraManager = context?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolResult(false, "Camera manager unavailable on this device, boss.", mapOf("directResponse" to true))

        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }

            if (cameraId == null) {
                return ToolResult(false, "No flashlight available on this device, boss.", mapOf("directResponse" to true))
            }

            cameraManager.setTorchMode(cameraId, targetState)
            isTorchOn = targetState
            val stateText = if (targetState) "on" else "off"
            ToolResult(true, "Flashlight turned $stateText, boss.", mapOf("isTorchOn" to targetState, "directResponse" to true))
        } catch (e: Exception) {
            Log.e("ToggleFlashlightTool", "Error setting torch mode", e)
            ToolResult(false, "Could not control flashlight: ${e.message}", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: open_camera
 * Launches the device camera directly.
 */
class OpenCameraTool(
    private val context: Context? = null,
    private val launcherOverride: (() -> Boolean)? = null
) : JarvisTool {

    override val name: String = "open_camera"
    override val description: String = "Opens the device camera to take a photo or record video."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (launcherOverride != null) {
            launcherOverride.invoke()
            return ToolResult(true, "Opening camera, boss.", mapOf("directResponse" to true))
        }

        if (context == null) {
            return ToolResult(false, "Android context required to open camera.", mapOf("directResponse" to true))
        }

        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            ToolResult(true, "Opening camera, boss.", mapOf("directResponse" to true))
        } catch (e: Exception) {
            ToolResult(false, "Could not open camera: ${e.message}", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: get_device_info
 * Retrieves hardware model, OS version, and storage/RAM metrics.
 */
class GetDeviceInfoTool(
    private val context: Context? = null
) : JarvisTool {

    override val name: String = "get_device_info"
    override val description: String = "Retrieves information about the device model, Android OS version, storage, and memory."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: "Android"
        val model = Build.MODEL ?: "Device"
        val androidVersion = Build.VERSION.RELEASE ?: "14"
        val sdkInt = Build.VERSION.SDK_INT

        var freeStorageGb: Long = -1
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            freeStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
        } catch (_: Exception) {}

        var availRamGb: Long = -1
        if (context != null) {
            try {
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager?.getMemoryInfo(memInfo)
                availRamGb = memInfo.availMem / (1024 * 1024 * 1024)
            } catch (_: Exception) {}
        }

        val details = buildString {
            append("You are running on a $manufacturer $model with Android $androidVersion (API $sdkInt).")
            if (freeStorageGb >= 0) append(" Free storage: ${freeStorageGb}GB.")
            if (availRamGb >= 0) append(" Available RAM: ${availRamGb}GB.")
        }

        return ToolResult(
            success = true,
            message = details,
            data = mapOf(
                "manufacturer" to manufacturer,
                "model" to model,
                "androidVersion" to androidVersion,
                "sdkInt" to sdkInt,
                "freeStorageGb" to freeStorageGb,
                "availableRamGb" to availRamGb,
                "directResponse" to true
            )
        )
    }
}

/**
 * Tool: get_network_status
 * Checks Wi-Fi, Cellular, Ethernet, and internet connectivity.
 */
class GetNetworkStatusTool(
    private val context: Context? = null,
    private val mockNetworkType: String? = null
) : JarvisTool {

    override val name: String = "get_network_status"
    override val description: String = "Checks internet and network connection status (Wi-Fi, Mobile Data, Offline)."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (mockNetworkType != null) {
            return ToolResult(true, "You are connected via $mockNetworkType, boss.", mapOf("networkType" to mockNetworkType, "isConnected" to true, "directResponse" to true))
        }

        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ToolResult(false, "Connectivity service unavailable, boss.", mapOf("directResponse" to true))

        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)

        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return ToolResult(true, "Your device is currently offline, boss.", mapOf("isConnected" to false, "networkType" to "Offline", "directResponse" to true))
        }

        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Network"
        }

        return ToolResult(
            success = true,
            message = "You are currently online and connected via $type, boss.",
            data = mapOf("isConnected" to true, "networkType" to type, "directResponse" to true)
        )
    }
}

/**
 * Tool: open_wifi_settings
 */
class OpenWifiSettingsTool(
    private val context: Context? = null
) : JarvisTool {

    override val name: String = "open_wifi_settings"
    override val description: String = "Opens the Android Wi-Fi settings page."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context?.startActivity(intent)
            ToolResult(true, "Opening Wi-Fi settings, boss.", mapOf("directResponse" to true))
        } catch (e: Exception) {
            ToolResult(false, "Could not open Wi-Fi settings: ${e.message}", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: open_bluetooth_settings
 */
class OpenBluetoothSettingsTool(
    private val context: Context? = null
) : JarvisTool {

    override val name: String = "open_bluetooth_settings"
    override val description: String = "Opens the Android Bluetooth settings page."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context?.startActivity(intent)
            ToolResult(true, "Opening Bluetooth settings, boss.", mapOf("directResponse" to true))
        } catch (e: Exception) {
            ToolResult(false, "Could not open Bluetooth settings: ${e.message}", mapOf("directResponse" to true))
        }
    }
}

/**
 * Tool: lock_screen
 * Locks the device screen safely via AccessibilityService.
 */
class LockScreenTool(
    private val lockActionOverride: (() -> Boolean)? = null
) : JarvisTool {

    override val name: String = "lock_screen"
    override val description: String = "Locks the device screen immediately."
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        if (lockActionOverride != null) {
            val locked = lockActionOverride.invoke()
            return if (locked) {
                ToolResult(true, "Screen locked, boss.", mapOf("directResponse" to true))
            } else {
                ToolResult(false, "Failed to lock screen, boss.", mapOf("directResponse" to true))
            }
        }

        val service = JarvisAccessibilityService.getInstance()
        if (service == null) {
            return ToolResult(
                success = false,
                message = "Accessibility service is required to lock the screen. Please ensure JARVIS accessibility service is enabled in Settings, boss.",
                data = mapOf("directResponse" to true)
            )
        }

        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalActionSafe(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }

        return if (success) {
            ToolResult(true, "Screen locked, boss.", mapOf("directResponse" to true))
        } else {
            ToolResult(false, "Screen locking requires Android 9 (Pie) or higher, boss.", mapOf("directResponse" to true))
        }
    }
}
