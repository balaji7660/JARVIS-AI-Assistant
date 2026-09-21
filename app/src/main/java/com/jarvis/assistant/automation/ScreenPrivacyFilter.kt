package com.jarvis.assistant.automation

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

sealed class ScreenPrivacyResult {
    data class Safe(val base64Image: String, val width: Int, val height: Int) : ScreenPrivacyResult()
    data class Blocked(val reason: String) : ScreenPrivacyResult()
}

/**
 * Privacy filter protecting user credentials and sensitive screens from being captured or sent to the AI.
 * Resizes screenshots to a bounded dimension (max 1280px) and keeps processing strictly in-memory.
 */
class ScreenPrivacyFilter {

    companion object {
        const val MAX_IMAGE_DIMENSION = 1280
        const val JPEG_QUALITY = 80
        const val MAX_PAYLOAD_BASE64_BYTES = 2 * 1024 * 1024 // 2 MB max Base64

        const val REASON_CREDENTIAL_SCREEN = "I can't analyze sensitive credential screens."
        const val REASON_PAYLOAD_TOO_LARGE = "Image payload size limit exceeded."
        const val REASON_NO_IMAGE = "No image data available."

        // Patterns for OTP, PIN, and Credit Cards
        private val CARD_NUMBER_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b")
        private val OTP_PATTERN = Pattern.compile("(?i)\\b(otp|one[- ]time password|verification code|security code|passcode)\\b")
        private val PIN_PATTERN = Pattern.compile("(?i)\\b(enter pin|atm pin|secret pin)\\b")

        private val SENSITIVE_PACKAGES = setOf(
            "com.google.android.apps.authenticator2",
            "com.authy.authy",
            "com.lastpass.lpandroid",
            "com.onepassword.android",
            "com.bitwarden.mobile",
            "com.dashlane",
            "com.chase.sig.android",
            "com.bankofamerica.mobilebanking",
            "com.wf.wellsfargomobile"
        )
    }

    /**
     * Inspects active screen metadata and image, rejecting sensitive screens and compressing safe images.
     */
    fun processAndFilter(capture: ScreenCapture, snapshot: ScreenSnapshot?): ScreenPrivacyResult {
        // 1. Check Package Name
        val pkg = snapshot?.packageName ?: capture.packageName
        if (pkg != null && isSensitivePackage(pkg)) {
            return ScreenPrivacyResult.Blocked(REASON_CREDENTIAL_SCREEN)
        }

        // 2. Check Accessibility Snapshot for Passwords or Sensitive Tokens
        if (snapshot != null) {
            for (element in snapshot.elements) {
                if (element.isPassword) {
                    return ScreenPrivacyResult.Blocked(REASON_CREDENTIAL_SCREEN)
                }
                val text = element.text ?: element.contentDescription ?: ""
                if (isSensitiveText(text)) {
                    return ScreenPrivacyResult.Blocked(REASON_CREDENTIAL_SCREEN)
                }
            }
        }

        // 3. Verify Image Presence
        val bitmap = capture.bitmap ?: return ScreenPrivacyResult.Blocked(REASON_NO_IMAGE)

        // 4. Downscale while maintaining aspect ratio
        val (scaledBitmap, scaledWidth, scaledHeight) = downscaleIfNeeded(bitmap)

        // 5. In-Memory Compression (JPEG Quality 80)
        val stream = ByteArrayOutputStream()
        val compressSuccess = scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        if (!compressSuccess) {
            return ScreenPrivacyResult.Blocked("Failed to compress screen image.")
        }

        val byteArray = stream.toByteArray()
        val base64String = try {
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(byteArray)
        }

        // 6. Enforce payload limit
        if (base64String.length > MAX_PAYLOAD_BASE64_BYTES) {
            return ScreenPrivacyResult.Blocked(REASON_PAYLOAD_TOO_LARGE)
        }

        return ScreenPrivacyResult.Safe(
            base64Image = base64String,
            width = scaledWidth,
            height = scaledHeight
        )
    }

    fun isSensitivePackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return SENSITIVE_PACKAGES.contains(lower) ||
                lower.contains("authenticator") ||
                lower.contains("passwordmanager") ||
                lower.contains("keepass")
    }

    fun isSensitiveText(text: String): Boolean {
        if (text.isBlank()) return false
        if (OTP_PATTERN.matcher(text).find()) return true
        if (PIN_PATTERN.matcher(text).find()) return true
        if (CARD_NUMBER_PATTERN.matcher(text).find()) return true
        return false
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Triple<Bitmap, Int, Int> {
        val origWidth = bitmap.width
        val origHeight = bitmap.height

        if (origWidth <= MAX_IMAGE_DIMENSION && origHeight <= MAX_IMAGE_DIMENSION) {
            return Triple(bitmap, origWidth, origHeight)
        }

        val ratio = origWidth.toFloat() / origHeight.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (origWidth > origHeight) {
            newWidth = MAX_IMAGE_DIMENSION
            newHeight = (MAX_IMAGE_DIMENSION / ratio).toInt()
        } else {
            newHeight = MAX_IMAGE_DIMENSION
            newWidth = (MAX_IMAGE_DIMENSION * ratio).toInt()
        }

        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        return Triple(scaled, newWidth, newHeight)
    }
}
