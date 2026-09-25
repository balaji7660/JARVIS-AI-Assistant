package com.jarvis.assistant.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.*

/**
 * Futuristic Arc-Reactor floating orb UI for JARVIS overlay.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JarvisFloatingOrb(
    visualState: OrbVisualState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbAnimation")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (visualState) {
                    OrbVisualState.EXECUTING -> 1500
                    OrbVisualState.PROCESSING -> 2000
                    OrbVisualState.SPEAKING -> 2800
                    OrbVisualState.ANALYZING -> 2500
                    OrbVisualState.CONFIRMATION -> 4000
                    else -> 6000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (visualState) {
                    OrbVisualState.LISTENING -> 800
                    OrbVisualState.SPEAKING -> 900
                    OrbVisualState.CONFIRMATION -> 1200
                    else -> 1800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val (primaryColor, glowColor) = when (visualState) {
        OrbVisualState.PASSIVE, OrbVisualState.IDLE -> JarvisCyan to JarvisCyanBright
        OrbVisualState.LISTENING -> JarvisNeonPurple to Color(0xFFD580FF)
        OrbVisualState.PROCESSING -> JarvisCyan to JarvisCyanBright
        OrbVisualState.ANALYZING -> JarvisCyanBright to Color(0xFF80FFFF)
        OrbVisualState.EXECUTING -> JarvisGreen to Color(0xFF80FFB2)
        OrbVisualState.SPEAKING -> JarvisBlueLight to JarvisCyan
        OrbVisualState.CONFIRMATION -> JarvisAmber to Color(0xFFFFD580)
        OrbVisualState.SUCCESS -> JarvisGreen to Color(0xFF80FFB2)
        OrbVisualState.ERROR -> JarvisRed to Color(0xFFFF8080)
    }

    Column(
        modifier = modifier
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = primaryColor, spotColor = glowColor)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.25f),
                            JarvisBackground.copy(alpha = 0.95f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(60.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = (size.minDimension / 2) * 0.9f

                // Outer boundary ring
                drawCircle(
                    color = primaryColor.copy(alpha = 0.4f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Rotating arc segmented ring
                drawArc(
                    color = primaryColor,
                    startAngle = rotationAngle,
                    sweepAngle = 100f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = glowColor,
                    startAngle = rotationAngle + 180f,
                    sweepAngle = 80f,
                    useCenter = false,
                    style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                )

                // Inner core with pulsating glow
                val coreRadius = (radius * 0.45f) * pulseScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, glowColor, primaryColor.copy(alpha = 0.4f)),
                        center = center,
                        radius = coreRadius
                    ),
                    radius = coreRadius,
                    center = center
                )
            }
        }

        // Compact status pill under orb
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(JarvisBackground.copy(alpha = 0.92f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = visualState.label,
                style = MaterialTheme.typography.labelSmall,
                color = primaryColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp
            )
        }
    }
}
