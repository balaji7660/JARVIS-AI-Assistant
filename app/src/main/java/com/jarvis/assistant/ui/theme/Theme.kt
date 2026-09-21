package com.jarvis.assistant.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisBackground,
    primaryContainer = JarvisSurfaceElevated,
    onPrimaryContainer = JarvisCyanBright,
    secondary = JarvisBlueLight,
    onSecondary = JarvisBackground,
    secondaryContainer = JarvisSurfaceVariant,
    onSecondaryContainer = JarvisBlueLight,
    tertiary = JarvisNeonPurple,
    onTertiary = TextPrimary,
    background = JarvisBackground,
    onBackground = TextPrimary,
    surface = JarvisSurface,
    onSurface = TextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderGlow
)

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = JarvisDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
