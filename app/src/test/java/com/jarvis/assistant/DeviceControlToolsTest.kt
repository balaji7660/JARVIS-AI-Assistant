package com.jarvis.assistant

import com.jarvis.assistant.tools.impl.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceControlToolsTest {

    @Test
    fun getBatteryStatus_reportsLevelAndCharging() = runBlocking {
        val tool = GetBatteryStatusTool(
            batteryLevelOverride = 85,
            isChargingOverride = true
        )
        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.message.contains("85%"))
        assertTrue(result.message.contains("charging"))
        assertEquals(85, result.data["level"])
        assertEquals(true, result.data["isCharging"])
    }

    @Test
    fun setVolume_updatesTargetPercentage() = runBlocking {
        var updatedStream: Int? = null
        var updatedLevel: Int? = null

        val tool = SetVolumeTool(
            volumeActionOverride = { stream, level ->
                updatedStream = stream
                updatedLevel = level
                true
            }
        )

        val result = tool.execute(mapOf("volumePercent" to 75, "stream" to "music"))

        assertTrue(result.success)
        assertEquals(75, updatedLevel)
    }

    @Test
    fun getVolume_returnsConfiguredVolume() = runBlocking {
        val tool = GetVolumeTool(mockVolumePct = 60)
        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.message.contains("60%"))
        assertEquals(60, result.data["volumePercent"])
    }

    @Test
    fun setBrightness_clampsAndAppliesPercentage() = runBlocking {
        var appliedBrightness: Int? = null
        val tool = SetBrightnessTool(
            brightnessOverride = { pct ->
                appliedBrightness = pct
                true
            }
        )

        val result = tool.execute(mapOf("brightnessPercent" to 80))

        assertTrue(result.success)
        assertEquals(80, appliedBrightness)
        assertTrue(result.message.contains("80%"))
    }

    @Test
    fun getBrightness_returnsCurrentPercentage() = runBlocking {
        val tool = GetBrightnessTool(mockBrightnessPct = 50)
        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.message.contains("50%"))
        assertEquals(50, result.data["brightnessPercent"])
    }

    @Test
    fun toggleFlashlight_setsStateCorrectly() = runBlocking {
        var torchState: Boolean? = null
        val tool = ToggleFlashlightTool(
            torchOverride = { state ->
                torchState = state
                true
            }
        )

        // Turn on
        val resultOn = tool.execute(mapOf("action" to "on"))
        assertTrue(resultOn.success)
        assertEquals(true, torchState)

        // Turn off
        val resultOff = tool.execute(mapOf("action" to "off"))
        assertTrue(resultOff.success)
        assertEquals(false, torchState)
    }

    @Test
    fun getDeviceInfo_returnsHardwareAndOsDetails() = runBlocking {
        val tool = GetDeviceInfoTool(context = null)
        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.data.containsKey("manufacturer"))
        assertTrue(result.data.containsKey("model"))
        assertTrue(result.data.containsKey("androidVersion"))
    }

    @Test
    fun getNetworkStatus_reportsActiveTransport() = runBlocking {
        val tool = GetNetworkStatusTool(mockNetworkType = "Wi-Fi")
        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.message.contains("Wi-Fi"))
        assertEquals(true, result.data["isConnected"])
    }

    @Test
    fun lockScreen_executesOverrideSafely() = runBlocking {
        var lockExecuted = false
        val tool = LockScreenTool(
            lockActionOverride = {
                lockExecuted = true
                true
            }
        )

        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(lockExecuted)
        assertTrue(result.message.contains("Screen locked"))
    }
}
