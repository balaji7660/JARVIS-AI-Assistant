package com.jarvis.assistant.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.ui.components.JarvisOrb
import com.jarvis.assistant.ui.components.StatusText
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisCyanBright
import com.jarvis.assistant.ui.theme.JarvisNeonPurple
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceElevated
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.TextMuted
import com.jarvis.assistant.ui.theme.TextPrimary
import com.jarvis.assistant.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibilityStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val activity = context as? MainActivity
        val isPermanentlyDenied = if (!isGranted && activity != null) {
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            false
        }
        viewModel.onPermissionResult(isGranted, isPermanentlyDenied)
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            JarvisBackground,
            Color(0xFF090E18),
            JarvisBackground
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top HUD Bar: JARVIS Header & Settings Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "JARVIS",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "VOICE ENGINE ACTIVE // PROTOCOL 2.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisCyan.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isAccessibilityEnabled) JarvisCyan else JarvisAmber)
                        )
                        Text(
                            text = if (uiState.isAccessibilityEnabled) "AUTOMATION: READY" else "AUTOMATION: ACCESS REQUIRED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isAccessibilityEnabled) JarvisCyan.copy(alpha = 0.85f) else JarvisAmber.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val hudColor = when {
                            uiState.wakeWordState == com.jarvis.assistant.wakeword.WakeWordState.ERROR -> JarvisAmber
                            uiState.wakeWordState == com.jarvis.assistant.wakeword.WakeWordState.WAKE_DETECTED ||
                            uiState.wakeWordState == com.jarvis.assistant.wakeword.WakeWordState.TRANSITIONING -> JarvisCyanBright
                            uiState.state == AssistantState.SPEAKING -> JarvisCyan
                            uiState.state == AssistantState.LISTENING ||
                            uiState.wakeWordState == com.jarvis.assistant.wakeword.WakeWordState.COMMAND_LISTENING -> JarvisNeonPurple
                            uiState.isWakeWordEnabled -> JarvisCyan
                            else -> TextMuted
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(hudColor)
                        )
                        Text(
                            text = uiState.hudStateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = hudColor.copy(alpha = 0.95f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    if (uiState.screenAnalysisState != ScreenAnalysisState.IDLE) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val dotColor = when (uiState.screenAnalysisState) {
                                ScreenAnalysisState.ANALYZING -> JarvisCyan
                                ScreenAnalysisState.PROTECTED -> com.jarvis.assistant.ui.theme.JarvisAmber
                                ScreenAnalysisState.COMPLETE -> com.jarvis.assistant.ui.theme.JarvisGreen
                                ScreenAnalysisState.FAILED -> com.jarvis.assistant.ui.theme.JarvisRed
                                else -> TextMuted
                            }
                            val label = when (uiState.screenAnalysisState) {
                                ScreenAnalysisState.ANALYZING -> "👁 SCREEN: ANALYZING // Inspecting visual context"
                                ScreenAnalysisState.PROTECTED -> "👁 SCREEN: PROTECTED // Credential screen detected"
                                ScreenAnalysisState.COMPLETE -> "👁 SCREEN: COMPLETE"
                                ScreenAnalysisState.FAILED -> "👁 SCREEN: FAILED"
                                else -> ""
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = dotColor.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(JarvisSurfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, JarvisCyan.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = JarvisCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Permission Banner (if denied)
            AnimatedVisibility(
                visible = uiState.permissionDenied,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisSurface.copy(alpha = 0.95f))
                        .border(1.dp, JarvisAmber.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Microphone permission is required for voice commands.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisAmber,
                            textAlign = TextAlign.Center
                        )

                        if (uiState.permissionPermanentlyDenied) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = JarvisCyan
                                ),
                                border = ButtonDefaults.outlinedButtonBorder(true),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = "Open Settings",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text("Open Settings", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Center Area: JarvisOrb + Status & Transcription Text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                JarvisOrb(
                    state = uiState.state,
                    size = 280.dp
                )

                Spacer(modifier = Modifier.height(32.dp))

                StatusText(
                    state = uiState.state,
                    statusMessage = uiState.statusMessage,
                    spokenText = uiState.spokenText,
                    activeToolName = uiState.activeToolName
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { viewModel.clearCurrentContext() },
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = JarvisCyan.copy(alpha = 0.85f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Clear Current Context",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Bottom Area: Microphone Trigger Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                MicButton(
                    state = uiState.state,
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.onMicTapped(hasPermission = true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = when (uiState.state) {
                        AssistantState.WAKE_LISTENING -> "Wake word active... Say 'Hey JARVIS' or tap mic"
                        AssistantState.LISTENING -> "Listening... Tap to finish"
                        AssistantState.SPEAKING -> "Speaking... Tap to interrupt"
                        AssistantState.THINKING -> "Processing response..."
                        else -> "Tap microphone to speak"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        uiState.pendingConfirmation?.let { confirmation ->
            com.jarvis.assistant.ui.components.ConfirmationDialog(
                confirmation = confirmation
            )
        }

        uiState.pendingMemoryConfirmation?.let { memoryConfirmation ->
            com.jarvis.assistant.ui.components.MemoryConfirmationDialog(
                confirmation = memoryConfirmation
            )
        }
    }
}

@Composable
private fun MicButton(
    state: AssistantState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = state == AssistantState.LISTENING
    val isSpeaking = state == AssistantState.SPEAKING
    val isThinking = state == AssistantState.THINKING

    val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.18f else if (isSpeaking) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 650 else 2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MicScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.LISTENING -> JarvisNeonPurple
            AssistantState.THINKING -> JarvisCyanBright
            AssistantState.SPEAKING -> JarvisCyan
            AssistantState.ERROR -> JarvisAmber
            else -> JarvisCyan
        },
        animationSpec = tween(400),
        label = "ButtonColor"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isListening) 0.45f else 0.15f,
        targetValue = if (isListening) 0.85f else 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 650 else 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Box(
        modifier = modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing halo
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(buttonColor.copy(alpha = glowAlpha * 0.45f))
        )

        // Main button
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            JarvisSurfaceElevated,
                            JarvisSurfaceVariant
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    color = buttonColor.copy(alpha = 0.85f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Filled.Mic else Icons.Filled.MicNone,
                contentDescription = "Microphone",
                tint = if (isListening) JarvisCyanBright else buttonColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
