package com.jarvis.assistant.overlay

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.planner.TaskExecutionState
import com.jarvis.assistant.ui.home.AssistantState
import com.jarvis.assistant.ui.home.HomeUiState
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisGreen
import com.jarvis.assistant.ui.theme.JarvisNeonPurple
import com.jarvis.assistant.ui.theme.JarvisRed
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.TextMuted
import com.jarvis.assistant.ui.theme.TextPrimary

/**
 * Compact JARVIS HUD control panel displayed when tapping the floating orb.
 */
@Composable
fun JarvisOverlayPanel(
    uiState: HomeUiState,
    onClose: () -> Unit,
    onMicTapped: () -> Unit,
    onStopTask: () -> Unit,
    onConfirmSafety: () -> Unit,
    onCancelSafety: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTaskRunning = uiState.taskState == TaskExecutionState.EXECUTING ||
            uiState.taskState == TaskExecutionState.PLANNING ||
            uiState.taskState == TaskExecutionState.VERIFYING ||
            uiState.taskState == TaskExecutionState.RETRYING

    Card(
        modifier = modifier
            .width(300.dp)
            .border(1.dp, JarvisCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisBackground.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Status Pill & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isTaskRunning) JarvisAmber else JarvisCyan)
                    )
                    Text(
                        text = uiState.hudStateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTaskRunning) JarvisAmber else JarvisCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close Panel",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = JarvisCyan.copy(alpha = 0.2f), thickness = 1.dp)

            // Safety Confirmation Modal Card (if pending)
            if (uiState.pendingConfirmation != null) {
                val conf = uiState.pendingConfirmation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JarvisAmber.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = JarvisSurface.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = "Safety Alert",
                                tint = JarvisAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "CONFIRM ACTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisAmber,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = conf.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (conf.arguments.isNotEmpty()) {
                            Text(
                                text = conf.arguments.entries.joinToString("\n") { "\"${it.key}\": \"${it.value}\"" },
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisCyan.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancelSafety,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisRed)
                            ) {
                                Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onConfirmSafety,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisAmber)
                            ) {
                                Text("CONFIRM", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Task Progress Information (if executing or planned)
            if (uiState.totalSteps > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JarvisCyan.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = JarvisSurfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "STEP ${uiState.currentStepIndex} / ${uiState.totalSteps}",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        if (!uiState.taskStatusDetail.isNullOrBlank()) {
                            Text(
                                text = uiState.taskStatusDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Status / Response Message
            val displayText = when {
                !uiState.errorMessage.isNullOrBlank() -> uiState.errorMessage
                uiState.responseText.isNotBlank() -> uiState.responseText
                uiState.statusMessage.isNotBlank() -> uiState.statusMessage
                else -> "Ready, boss."
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (!uiState.errorMessage.isNullOrBlank()) JarvisRed else TextPrimary,
                fontSize = 13.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // Bottom Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTaskRunning) {
                    Button(
                        onClick = onStopTask,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisRed)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.StopCircle,
                            contentDescription = "Stop",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STOP",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                IconButton(
                    onClick = onMicTapped,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isMicActive) JarvisNeonPurple else JarvisCyan)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = "Voice Input",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
