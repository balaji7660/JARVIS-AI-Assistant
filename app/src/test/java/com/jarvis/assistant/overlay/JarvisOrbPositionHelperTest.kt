package com.jarvis.assistant.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisOrbPositionHelperTest {

    private val bounds = ScreenBounds(
        width = 1080,
        height = 2400,
        topInset = 80,
        bottomInset = 120,
        edgeMargin = 20
    )
    private val orbSize = 80

    @Test
    fun clampPosition_whenWithinUsableArea_preservesCoordinates() {
        val clamped = JarvisOrbPositionHelper.clampPosition(200, 500, orbSize, bounds)
        assertEquals(200, clamped.x)
        assertEquals(500, clamped.y)
    }

    @Test
    fun clampPosition_whenDraggedTooFarLeftOrTop_clampsToMarginAndInset() {
        val clamped = JarvisOrbPositionHelper.clampPosition(-50, 10, orbSize, bounds)
        assertEquals(20, clamped.x) // edgeMargin
        assertEquals(100, clamped.y) // topInset + edgeMargin
    }

    @Test
    fun clampPosition_whenDraggedTooFarRightOrBottom_clampsWithinBounds() {
        val clamped = JarvisOrbPositionHelper.clampPosition(2000, 3000, orbSize, bounds)
        val expectedMaxX = bounds.width - orbSize - bounds.edgeMargin // 1080 - 80 - 20 = 980
        val expectedMaxY = bounds.height - bounds.bottomInset - orbSize - bounds.edgeMargin // 2400 - 120 - 80 - 20 = 2180

        assertEquals(expectedMaxX, clamped.x)
        assertEquals(expectedMaxY, clamped.y)
    }

    @Test
    fun snapToEdge_whenOnLeftSideOfScreen_snapsToLeftMargin() {
        val snapped = JarvisOrbPositionHelper.snapToEdge(
            currentX = 300, // Center = 340 < 540
            currentY = 600,
            orbSize = orbSize,
            bounds = bounds
        )

        assertEquals(bounds.edgeMargin, snapped.x)
        assertEquals(600, snapped.y)
    }

    @Test
    fun snapToEdge_whenOnRightSideOfScreen_snapsToRightMargin() {
        val snapped = JarvisOrbPositionHelper.snapToEdge(
            currentX = 700, // Center = 740 > 540
            currentY = 600,
            orbSize = orbSize,
            bounds = bounds
        )

        val expectedRightX = bounds.width - orbSize - bounds.edgeMargin
        assertEquals(expectedRightX, snapped.x)
        assertEquals(600, snapped.y)
    }

    @Test
    fun adjustForConfigurationChange_adjustsToNewScreenBounds() {
        val portraitPos = OrbPosition(x = 980, y = 1200)

        // Landscape bounds
        val landscapeBounds = ScreenBounds(
            width = 2400,
            height = 1080,
            topInset = 40,
            bottomInset = 40,
            edgeMargin = 20
        )

        val adjusted = JarvisOrbPositionHelper.adjustForConfigurationChange(
            previousPosition = portraitPos,
            orbSize = orbSize,
            newBounds = landscapeBounds
        )

        assertTrue(adjusted.x >= landscapeBounds.edgeMargin)
        assertTrue(adjusted.x <= landscapeBounds.width - orbSize - landscapeBounds.edgeMargin)
        assertTrue(adjusted.y <= landscapeBounds.height - landscapeBounds.bottomInset - orbSize - landscapeBounds.edgeMargin)
    }
}
