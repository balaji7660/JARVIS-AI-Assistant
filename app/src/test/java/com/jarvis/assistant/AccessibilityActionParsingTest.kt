package com.jarvis.assistant

import com.jarvis.assistant.automation.ScreenSnapshot
import com.jarvis.assistant.automation.VisibleElement
import com.jarvis.assistant.tools.automation.AutomationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityActionParsingTest {

    @Test
    fun automationAction_instances_holdCorrectProperties() {
        val clickText = AutomationAction.ClickText("Submit")
        assertEquals("Submit", clickText.text)

        val clickView = AutomationAction.ClickView("com.example:id/login_button")
        assertEquals("com.example:id/login_button", clickView.viewId)

        val typeText = AutomationAction.TypeText("Hello world")
        assertEquals("Hello world", typeText.text)

        assertEquals(AutomationAction.ReadVisibleScreen, AutomationAction.ReadVisibleScreen)
        assertEquals(AutomationAction.ScrollForward, AutomationAction.ScrollForward)
        assertEquals(AutomationAction.ScrollBackward, AutomationAction.ScrollBackward)
        assertEquals(AutomationAction.PressBack, AutomationAction.PressBack)
    }

    @Test
    fun visibleElement_properties_areAccurate() {
        val normalElement = VisibleElement(
            text = "Normal Username",
            contentDescription = null,
            viewId = "com.app:id/username",
            className = "android.widget.EditText",
            clickable = true,
            enabled = true,
            scrollable = false,
            isPassword = false
        )
        assertEquals("Normal Username", normalElement.text)
        assertTrue(normalElement.clickable)
        assertFalse(normalElement.isPassword)

        val passwordElement = VisibleElement(
            text = "SuperSecret123",
            contentDescription = "Password input",
            viewId = "com.app:id/password",
            className = "android.widget.EditText",
            clickable = true,
            enabled = true,
            scrollable = false,
            isPassword = true
        )
        assertTrue(passwordElement.isPassword)
    }

    @Test
    fun screenSnapshot_holdsElementsAndCounts() {
        val elements = listOf(
            VisibleElement(
                text = "Settings",
                contentDescription = null,
                viewId = "com.app:id/title",
                className = "android.widget.TextView",
                clickable = false,
                enabled = true,
                scrollable = false,
                isPassword = false
            ),
            VisibleElement(
                text = "Toggle Wi-Fi",
                contentDescription = null,
                viewId = "com.app:id/switch_wifi",
                className = "android.widget.Switch",
                clickable = true,
                enabled = true,
                scrollable = false,
                isPassword = false
            )
        )

        val snapshot = ScreenSnapshot(
            packageName = "com.android.settings",
            elements = elements,
            isTruncated = false
        )

        assertEquals("com.android.settings", snapshot.packageName)
        assertEquals(2, snapshot.elements.size)
        assertFalse(snapshot.isTruncated)
    }
}
