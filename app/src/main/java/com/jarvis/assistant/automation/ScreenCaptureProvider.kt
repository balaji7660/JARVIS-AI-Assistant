package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Abstraction for on-demand screen capture.
 * Never runs continuous background capture.
 */
interface ScreenCaptureProvider {
    suspend fun captureCurrentScreen(): Result<ScreenCapture>
}

/**
 * Production implementation leveraging Android 11+ (API 30+) AccessibilityService.takeScreenshot.
 * Zero root or hidden APIs required.
 */
class AccessibilityScreenCaptureProvider(
    private val serviceProvider: () -> JarvisAccessibilityService? = { JarvisAccessibilityService.getInstance() }
) : ScreenCaptureProvider {

    companion object {
        private const val TAG = "ScreenCaptureProvider"
    }

    override suspend fun captureCurrentScreen(): Result<ScreenCapture> = withContext(Dispatchers.Default) {
        val service = serviceProvider()
        if (service == null) {
            return@withContext Result.failure(
                IllegalStateException("Accessibility Service is not connected. Enable JARVIS in Accessibility Settings.")
            )
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext Result.failure(
                UnsupportedOperationException("Screen capture requires Android 11 (API 30) or newer.")
            )
        }

        try {
            captureFromServiceApi30(service)
        } catch (e: Exception) {
            Log.e(TAG, "Screen capture failed", e)
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureFromServiceApi30(service: JarvisAccessibilityService): Result<ScreenCapture> =
        suspendCancellableCoroutine { continuation ->
            val executor = Executors.newSingleThreadExecutor()

            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        hardwareBuffer.close()

                        if (bitmap != null) {
                            // Copy to software bitmap so it can be safely resized and compressed
                            val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            bitmap.recycle()

                            val capture = ScreenCapture(
                                bitmap = softwareBitmap,
                                width = softwareBitmap.width,
                                height = softwareBitmap.height,
                                packageName = service.currentPackageName
                            )
                            continuation.resume(Result.success(capture))
                        } else {
                            continuation.resume(Result.failure(IllegalStateException("Failed to wrap hardware buffer to Bitmap.")))
                        }
                    } catch (e: Exception) {
                        continuation.resume(Result.failure(e))
                    } finally {
                        executor.shutdown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    executor.shutdown()
                    val msg = when (errorCode) {
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "Internal screenshot error."
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "No accessibility access."
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Screenshot requested too quickly."
                        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Invalid display."
                        else -> "Failed to capture screenshot (code $errorCode)."
                    }
                    continuation.resume(Result.failure(IllegalStateException(msg)))
                }
            }

            service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
        }
}

/**
 * Deterministic in-memory simulated provider for unit tests and offline environments.
 */
class SimulatedScreenCaptureProvider(
    private val width: Int = 1080,
    private val height: Int = 2400,
    private val packageName: String = "com.jarvis.assistant",
    private val simulatedFailure: Boolean = false
) : ScreenCaptureProvider {

    override suspend fun captureCurrentScreen(): Result<ScreenCapture> {
        if (simulatedFailure) {
            return Result.failure(IllegalStateException("Simulated capture failure."))
        }
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            null
        }
        return Result.success(
            ScreenCapture(
                bitmap = bitmap,
                width = width,
                height = height,
                packageName = packageName
            )
        )
    }
}
