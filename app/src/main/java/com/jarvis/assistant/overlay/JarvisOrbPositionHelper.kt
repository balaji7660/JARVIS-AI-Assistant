package com.jarvis.assistant.overlay

/**
 * Encapsulates safe coordinates for the floating orb on the screen.
 */
data class OrbPosition(
    val x: Int,
    val y: Int
)

/**
 * Screen dimensions and insets used for boundary calculations.
 */
data class ScreenBounds(
    val width: Int,
    val height: Int,
    val topInset: Int = 0,
    val bottomInset: Int = 0,
    val edgeMargin: Int = 16
)

/**
 * Helper responsible for clamping coordinates within usable screen bounds,
 * calculating left/right edge snapping, and adjusting positions upon rotation.
 */
object JarvisOrbPositionHelper {

    /**
     * Clamps raw (x, y) coordinates so the orb remains fully visible within usable screen bounds.
     */
    fun clampPosition(
        x: Int,
        y: Int,
        orbSize: Int,
        bounds: ScreenBounds
    ): OrbPosition {
        val minX = bounds.edgeMargin
        val maxX = maxOf(minX, bounds.width - orbSize - bounds.edgeMargin)

        val minY = bounds.topInset + bounds.edgeMargin
        val maxY = maxOf(minY, bounds.height - bounds.bottomInset - orbSize - bounds.edgeMargin)

        val clampedX = x.coerceIn(minX, maxX)
        val clampedY = y.coerceIn(minY, maxY)

        return OrbPosition(clampedX, clampedY)
    }

    /**
     * Snaps the orb to either the left or right edge of the screen depending on its current horizontal center.
     */
    fun snapToEdge(
        currentX: Int,
        currentY: Int,
        orbSize: Int,
        bounds: ScreenBounds
    ): OrbPosition {
        val clamped = clampPosition(currentX, currentY, orbSize, bounds)
        val orbCenterX = clamped.x + (orbSize / 2)
        val screenCenterX = bounds.width / 2

        val snappedX = if (orbCenterX < screenCenterX) {
            bounds.edgeMargin
        } else {
            maxOf(bounds.edgeMargin, bounds.width - orbSize - bounds.edgeMargin)
        }

        return OrbPosition(x = snappedX, y = clamped.y)
    }

    /**
     * Adjusts existing coordinates to fit safely within new screen bounds after an orientation or configuration change.
     */
    fun adjustForConfigurationChange(
        previousPosition: OrbPosition,
        orbSize: Int,
        newBounds: ScreenBounds
    ): OrbPosition {
        return snapToEdge(
            currentX = previousPosition.x,
            currentY = previousPosition.y,
            orbSize = orbSize,
            bounds = newBounds
        )
    }
}
