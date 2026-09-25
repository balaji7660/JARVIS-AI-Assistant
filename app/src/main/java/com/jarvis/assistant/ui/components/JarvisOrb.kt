package com.jarvis.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.home.AssistantState
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisBlueLight
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisCyanBright
import com.jarvis.assistant.ui.theme.JarvisGreen
import com.jarvis.assistant.ui.theme.JarvisNeonPink
import com.jarvis.assistant.ui.theme.JarvisNeonPurple
import com.jarvis.assistant.ui.theme.JarvisRed
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisOrb(
    state: AssistantState,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp
) {
    // Dynamic animation specs tailored to assistant state
    val pulseDuration = when (state) {
        AssistantState.IDLE -> 3000
        AssistantState.PASSIVE_WAKE, AssistantState.WAKE_LISTENING -> 2200
        AssistantState.ACTIVE_LISTENING, AssistantState.LISTENING -> 1100
        AssistantState.PROCESSING, AssistantState.THINKING -> 650
        AssistantState.SPEAKING -> 850
        AssistantState.EXECUTING -> 1000
        AssistantState.WAITING_FOR_CONFIRMATION, AssistantState.WAITING_FOR_CLARIFICATION -> 1500
        AssistantState.ERROR -> 1400
    }

    val rotationDurationOuter = when (state) {
        AssistantState.IDLE -> 16000
        AssistantState.PASSIVE_WAKE, AssistantState.WAKE_LISTENING -> 12000
        AssistantState.ACTIVE_LISTENING, AssistantState.LISTENING -> 5000
        AssistantState.PROCESSING, AssistantState.THINKING -> 2200
        AssistantState.SPEAKING -> 4500
        AssistantState.EXECUTING -> 3500
        AssistantState.WAITING_FOR_CONFIRMATION, AssistantState.WAITING_FOR_CLARIFICATION -> 6000
        AssistantState.ERROR -> 12000
    }

    val rotationDurationInner = when (state) {
        AssistantState.IDLE -> 12000
        AssistantState.PASSIVE_WAKE, AssistantState.WAKE_LISTENING -> 8000
        AssistantState.ACTIVE_LISTENING, AssistantState.LISTENING -> 3500
        AssistantState.PROCESSING, AssistantState.THINKING -> 1800
        AssistantState.SPEAKING -> 3200
        AssistantState.EXECUTING -> 2500
        AssistantState.WAITING_FOR_CONFIRMATION, AssistantState.WAITING_FOR_CLARIFICATION -> 4500
        AssistantState.ERROR -> 9000
    }

    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransitions")

    // Breathing pulse scale
    val minScale = when (state) {
        AssistantState.IDLE -> 0.94f
        AssistantState.LISTENING -> 0.88f
        AssistantState.THINKING -> 0.92f
        AssistantState.SPEAKING -> 0.86f
        else -> 0.92f
    }
    val maxScale = when (state) {
        AssistantState.IDLE -> 1.04f
        AssistantState.LISTENING -> 1.15f
        AssistantState.THINKING -> 1.08f
        AssistantState.SPEAKING -> 1.18f
        else -> 1.06f
    }

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbPulseScale"
    )

    // Outer ring rotation (clockwise)
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDurationOuter, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OuterRotation"
    )

    // Inner ring rotation (counter-clockwise)
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDurationInner, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "InnerRotation"
    )

    // Concentric shockwave ripple alpha
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.IDLE -> 2400
                    AssistantState.LISTENING -> 1200
                    AssistantState.THINKING -> 800
                    AssistantState.SPEAKING -> 1000
                    else -> 1800
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleAlpha"
    )

    // Dynamic colors responsive to assistant state
    val primaryColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> JarvisCyan
            AssistantState.PASSIVE_WAKE, AssistantState.WAKE_LISTENING -> JarvisCyanBright
            AssistantState.ACTIVE_LISTENING, AssistantState.LISTENING -> JarvisNeonPurple
            AssistantState.PROCESSING, AssistantState.THINKING -> JarvisCyanBright
            AssistantState.SPEAKING -> JarvisCyan
            AssistantState.EXECUTING -> JarvisGreen
            AssistantState.WAITING_FOR_CONFIRMATION -> JarvisAmber
            AssistantState.WAITING_FOR_CLARIFICATION -> JarvisNeonPink
            AssistantState.ERROR -> JarvisRed
        },
        animationSpec = tween(500),
        label = "PrimaryColor"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> JarvisBlueLight
            AssistantState.PASSIVE_WAKE, AssistantState.WAKE_LISTENING -> JarvisCyan
            AssistantState.ACTIVE_LISTENING, AssistantState.LISTENING -> JarvisNeonPink
            AssistantState.PROCESSING, AssistantState.THINKING -> JarvisBlueLight
            AssistantState.SPEAKING -> JarvisBlueLight
            AssistantState.EXECUTING -> JarvisCyanBright
            AssistantState.WAITING_FOR_CONFIRMATION -> JarvisCyan
            AssistantState.WAITING_FOR_CLARIFICATION -> JarvisCyanBright
            AssistantState.ERROR -> JarvisAmber
        },
        animationSpec = tween(500),
        label = "SecondaryColor"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 2.8f
            val currentRadius = baseRadius * pulseScale

            val isHighEnergy = state == AssistantState.LISTENING ||
                    state == AssistantState.THINKING ||
                    state == AssistantState.SPEAKING

            // 1. Outermost Ambient Glow aura
            val ambientGlowBrush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = if (isHighEnergy) 0.38f else 0.18f),
                    secondaryColor.copy(alpha = if (isHighEnergy) 0.18f else 0.06f),
                    Color.Transparent
                ),
                center = center,
                radius = currentRadius * 1.7f
            )
            drawCircle(
                brush = ambientGlowBrush,
                radius = currentRadius * 1.7f,
                center = center
            )

            // 2. Concentric expanding sonar/ripple waves
            val rippleRadius = baseRadius * (1.1f + (1f - rippleAlpha) * 0.45f)
            drawCircle(
                color = primaryColor.copy(alpha = rippleAlpha * 0.45f),
                radius = rippleRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 3. Outer Segmented Orbital Arcs
            rotate(outerRotation, pivot = center) {
                val outerArcRadius = currentRadius * 1.28f
                val outerArcRect = androidx.compose.ui.geometry.Rect(
                    center.x - outerArcRadius,
                    center.y - outerArcRadius,
                    center.x + outerArcRadius,
                    center.y + outerArcRadius
                )

                // Segment 1
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(primaryColor.copy(alpha = 0.1f), primaryColor.copy(alpha = 0.95f))
                    ),
                    startAngle = 0f,
                    sweepAngle = if (state == AssistantState.THINKING) 120f else 75f,
                    useCenter = false,
                    topLeft = outerArcRect.topLeft,
                    size = outerArcRect.size,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Segment 2
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(secondaryColor.copy(alpha = 0.1f), secondaryColor.copy(alpha = 0.95f))
                    ),
                    startAngle = 140f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = outerArcRect.topLeft,
                    size = outerArcRect.size,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Segment 3
                drawArc(
                    color = primaryColor.copy(alpha = 0.55f),
                    startAngle = 270f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = outerArcRect.topLeft,
                    size = outerArcRect.size,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Subtle orbital HUD tick marks around outer circle
                val tickCount = if (state == AssistantState.THINKING) 16 else 12
                for (i in 0 until tickCount) {
                    val angleRad = Math.toRadians((i * (360f / tickCount)).toDouble())
                    val r1 = outerArcRadius + 6.dp.toPx()
                    val r2 = outerArcRadius + 11.dp.toPx()
                    val start = Offset(
                        center.x + (r1 * cos(angleRad)).toFloat(),
                        center.y + (r1 * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        center.x + (r2 * cos(angleRad)).toFloat(),
                        center.y + (r2 * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = secondaryColor.copy(alpha = 0.4f),
                        start = start,
                        end = end,
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // 4. Inner Ring with counter rotation
            rotate(innerRotation, pivot = center) {
                val innerRingRadius = currentRadius * 1.12f
                val innerArcRect = androidx.compose.ui.geometry.Rect(
                    center.x - innerRingRadius,
                    center.y - innerRingRadius,
                    center.x + innerRingRadius,
                    center.y + innerRingRadius
                )

                drawArc(
                    color = secondaryColor.copy(alpha = 0.75f),
                    startAngle = 40f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = innerArcRect.topLeft,
                    size = innerArcRect.size,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )

                drawArc(
                    color = primaryColor.copy(alpha = 0.65f),
                    startAngle = 200f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = innerArcRect.topLeft,
                    size = innerArcRect.size,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 5. High-intensity Core Ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.85f),
                radius = currentRadius * 0.98f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // 6. Luminous Plasma Core with Multi-stop Radial Gradient
            val coreGradient = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isHighEnergy) 0.98f else 0.85f),
                    primaryColor.copy(alpha = 0.88f),
                    secondaryColor.copy(alpha = 0.6f),
                    primaryColor.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = center,
                radius = currentRadius * 0.95f
            )

            drawCircle(
                brush = coreGradient,
                radius = currentRadius * 0.95f,
                center = center
            )

            // 7. Center Micro-Node Core
            drawCircle(
                color = Color.White.copy(alpha = if (isHighEnergy) 0.98f else 0.75f),
                radius = currentRadius * 0.18f,
                center = center
            )
        }
    }
}
