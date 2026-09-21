package com.jarvis.assistant.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenPrivacyFilterTest {

    private lateinit var filter: ScreenPrivacyFilter

    @Before
    fun setUp() {
        filter = ScreenPrivacyFilter()
    }

    @Test
    fun processAndFilter_blocksPasswordFields() {
        val capture = ScreenCapture(bitmap = null, width = 100, height = 100)
        val snapshot = ScreenSnapshot(
            packageName = "com.example.app",
            elements = listOf(
                VisibleElement(text = "Password", isPassword = true)
            )
        )

        val result = filter.processAndFilter(capture, snapshot)
        assertTrue(result is ScreenPrivacyResult.Blocked)
        assertEquals(ScreenPrivacyFilter.REASON_CREDENTIAL_SCREEN, (result as ScreenPrivacyResult.Blocked).reason)
    }

    @Test
    fun processAndFilter_blocksOtpText() {
        val capture = ScreenCapture(bitmap = null, width = 100, height = 100)
        val snapshot = ScreenSnapshot(
            packageName = "com.example.app",
            elements = listOf(
                VisibleElement(text = "Your OTP is 948201")
            )
        )

        val result = filter.processAndFilter(capture, snapshot)
        assertTrue(result is ScreenPrivacyResult.Blocked)
        assertEquals(ScreenPrivacyFilter.REASON_CREDENTIAL_SCREEN, (result as ScreenPrivacyResult.Blocked).reason)
    }

    @Test
    fun processAndFilter_blocksCardNumberPattern() {
        val capture = ScreenCapture(bitmap = null, width = 100, height = 100)
        val snapshot = ScreenSnapshot(
            packageName = "com.example.app",
            elements = listOf(
                VisibleElement(text = "Card: 4111 2222 3333 4444")
            )
        )

        val result = filter.processAndFilter(capture, snapshot)
        assertTrue(result is ScreenPrivacyResult.Blocked)
        assertEquals(ScreenPrivacyFilter.REASON_CREDENTIAL_SCREEN, (result as ScreenPrivacyResult.Blocked).reason)
    }

    @Test
    fun processAndFilter_blocksSensitivePackages() {
        val capture = ScreenCapture(bitmap = null, width = 100, height = 100, packageName = "com.google.android.apps.authenticator2")
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.apps.authenticator2",
            elements = emptyList()
        )

        val result = filter.processAndFilter(capture, snapshot)
        assertTrue(result is ScreenPrivacyResult.Blocked)
        assertEquals(ScreenPrivacyFilter.REASON_CREDENTIAL_SCREEN, (result as ScreenPrivacyResult.Blocked).reason)
    }

    @Test
    fun processAndFilter_blocksSafeScreenWithoutBitmap() {
        val capture = ScreenCapture(bitmap = null, width = 200, height = 200, packageName = "com.android.settings")
        val snapshot = ScreenSnapshot(
            packageName = "com.android.settings",
            elements = listOf(
                VisibleElement(text = "Network & internet", clickable = true),
                VisibleElement(text = "Connected devices", clickable = true)
            )
        )

        val result = filter.processAndFilter(capture, snapshot)
        assertTrue(result is ScreenPrivacyResult.Blocked)
        assertEquals(ScreenPrivacyFilter.REASON_NO_IMAGE, (result as ScreenPrivacyResult.Blocked).reason)
    }

    @Test
    fun helperMethods_workAccurately() {
        assertTrue(filter.isSensitivePackage("com.lastpass.lpandroid"))
        assertTrue(filter.isSensitivePackage("org.keepass.droid"))
        assertFalse(filter.isSensitivePackage("com.android.settings"))

        assertTrue(filter.isSensitiveText("Enter PIN code:"))
        assertTrue(filter.isSensitiveText("One-Time Password"))
        assertTrue(filter.isSensitiveText("Your verification code is 4921"))
        assertFalse(filter.isSensitiveText("Battery status is 95%"))
        assertFalse(filter.isSensitiveText("Settings search"))
    }
}
