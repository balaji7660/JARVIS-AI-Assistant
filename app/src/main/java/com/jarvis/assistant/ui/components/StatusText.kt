package com.jarvis.assistant.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.home.AssistantState
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisBlueLight
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisCyanBright
import com.jarvis.assistant.ui.theme.JarvisGreen
import com.jarvis.assistant.ui.theme.JarvisNeonPink
import com.jarvis.assistant.ui.theme.JarvisNeonPurple
import com.jarvis.assistant.ui.theme.JarvisRed
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.TextMuted
import com.jarvis.assistant.ui.theme.TextPrimary
import com.jarvis.assistant.ui.theme.TextSecondary

@Composable
fun StatusText(
    state: AssistantState,
    statusMessage: String,
    spokenText: String = "",
    activeToolName: String? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LedBlink")
    val isAnimated = state == AssistantState.LISTENING || state == AssistantState.THINKING || state == AssistantState.SPEAKING
    val ledPulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isAnimated) 450 else 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LedPulse"
    )

    val stateColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> JarvisCyan
            AssistantState.PASSIVE_WAKE, AssistantState.WAKE_LISTENING -> JarvisCyanBright
            AssistantState.ACTIVE_LISTENING, AssistantState.LISTENING -> JarvisNeonPurple
            AssistantState.PROCESSING, AssistantState.THINKING -> JarvisCyanBright
            AssistantState.SPEAKING -> JarvisBlueLight
            AssistantState.EXECUTING -> JarvisGreen
            AssistantState.WAITING_FOR_CONFIRMATION -> JarvisAmber
            AssistantState.WAITING_FOR_CLARIFICATION -> JarvisNeonPink
            AssistantState.ERROR -> JarvisRed
        },
        animationSpec = tween(400),
        label = "StateColor"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status Badge Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(JarvisSurfaceVariant.copy(alpha = 0.75f))
                .border(
                    width = 1.dp,
                    color = stateColor.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulsing LED dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(ledPulse)
                        .clip(CircleShape)
                        .background(stateColor)
                )

                Text(
                    text = state.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = stateColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
            }
        }

        // Subtle tool execution indicator badge
        if (activeToolName != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ACTION // EXECUTING: ${activeToolName.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisCyanBright,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Optional User Query Transcript
        if (spokenText.isNotBlank() && state != AssistantState.IDLE) {
            Text(
                text = "\"$spokenText\"",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Dynamic State Dialogue / Phrase
        AnimatedContent(
            targetState = statusMessage,
            transitionSpec = {
                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "MessageTransition"
        ) { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
