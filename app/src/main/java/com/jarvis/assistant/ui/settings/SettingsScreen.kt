package com.jarvis.assistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.TextMuted
import com.jarvis.assistant.ui.theme.TextPrimary
import com.jarvis.assistant.ui.theme.TextSecondary

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.assistant.ui.home.HomeViewModel

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMemories: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearAllConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var micPermissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            micPermissionDenied = false
            if (!uiState.isWakeWordEnabled) {
                viewModel.toggleWakeWord()
            }
        } else {
            micPermissionDenied = true
        }
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
                .padding(horizontal = 20.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(JarvisSurfaceVariant.copy(alpha = 0.7f))
                        .border(1.dp, JarvisCyan.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = JarvisCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                Column {
                    Text(
                        text = "JARVIS Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "CONFIGURATION // PROTOCOL LEVEL 1",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisCyan.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Settings Sections List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SettingsSectionCard(
                        title = "Assistant",
                        subtitle = "Personality, wake sensitivity, haptic feedback",
                        icon = Icons.Outlined.SmartToy,
                        statusText = "Configured"
                    )
                }

                item {
                    SettingsSectionCard(
                        title = "Voice",
                        subtitle = "Speech synthesizer engine, pitch, rate",
                        icon = Icons.Outlined.Mic,
                        statusText = "Milestone 2"
                    )
                }

                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var serverUrlText by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(com.jarvis.assistant.data.remote.ApiClient.getBaseUrl())
                    }
                    var isSavedFeedback by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(false)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JarvisCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = JarvisSurface.copy(alpha = 0.9f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(JarvisSurfaceVariant)
                                        .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Psychology,
                                        contentDescription = "AI Backend",
                                        tint = JarvisCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.size(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "AI Backend Server",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Cloud Host / Public Tunnel Endpoint",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            androidx.compose.material3.OutlinedTextField(
                                value = serverUrlText,
                                onValueChange = {
                                    serverUrlText = it
                                    isSavedFeedback = false
                                },
                                label = { Text("Server URL (http/https)", color = TextMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = JarvisCyan,
                                    unfocusedBorderColor = JarvisCyan.copy(alpha = 0.3f),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = JarvisCyan
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        com.jarvis.assistant.data.remote.ApiClient.setBaseUrl(serverUrlText.trim(), context)
                                        serverUrlText = com.jarvis.assistant.data.remote.ApiClient.getBaseUrl()
                                        isSavedFeedback = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (isSavedFeedback) "Connected!" else "Save & Connect",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                androidx.compose.material3.OutlinedButton(
                                    onClick = {
                                        val defaultUrl = com.jarvis.assistant.data.remote.ApiClient.DEFAULT_BASE_URL
                                        com.jarvis.assistant.data.remote.ApiClient.setBaseUrl(defaultUrl, context)
                                        serverUrlText = defaultUrl
                                        isSavedFeedback = false
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f))
                                ) {
                                    Text("Reset", color = JarvisCyan)
                                }
                            }
                        }
                    }
                }

                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val isAccessEnabledState = androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(
                            com.jarvis.assistant.automation.AccessibilityUtils.isAccessibilityServiceEnabled(context)
                        )
                    }
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

                    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                isAccessEnabledState.value = com.jarvis.assistant.automation.AccessibilityUtils.isAccessibilityServiceEnabled(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        isAccessEnabledState.value = com.jarvis.assistant.automation.AccessibilityUtils.isAccessibilityServiceEnabled(context)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    val isAccessEnabled = isAccessEnabledState.value

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isAccessEnabled) JarvisCyan.copy(alpha = 0.35f) else com.jarvis.assistant.ui.theme.JarvisAmber.copy(alpha = 0.35f),
                                RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = JarvisSurface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(JarvisSurfaceVariant)
                                            .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = "Automation",
                                            tint = if (isAccessEnabled) JarvisCyan else com.jarvis.assistant.ui.theme.JarvisAmber,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(14.dp))

                                    Column {
                                        Text(
                                            text = "JARVIS Automation",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Accessibility Access",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(JarvisSurfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (isAccessEnabled) "ENABLED" else "DISABLED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isAccessEnabled) JarvisCyan else com.jarvis.assistant.ui.theme.JarvisAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Text(
                                text = "JARVIS needs Accessibility access to inspect and interact with visible controls on your explicit command. Sensitive fields such as passwords and payment credentials are never captured.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )

                            androidx.compose.material3.Button(
                                onClick = {
                                    com.jarvis.assistant.automation.AccessibilityUtils.openAccessibilitySettings(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (isAccessEnabled) JarvisSurfaceVariant else com.jarvis.assistant.ui.theme.JarvisAmber,
                                    contentColor = if (isAccessEnabled) JarvisCyan else JarvisSurface
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Open Accessibility Settings",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Wake Word Section
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (uiState.isWakeWordEnabled) JarvisCyan.copy(alpha = 0.35f) else JarvisCyan.copy(alpha = 0.15f),
                                RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = JarvisSurface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(JarvisSurfaceVariant)
                                            .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Mic,
                                            contentDescription = "Wake Word",
                                            tint = if (uiState.isWakeWordEnabled) JarvisCyan else TextMuted,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(14.dp))

                                    Column {
                                        Text(
                                            text = "Wake Word",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = if (uiState.isWakeWordEnabled) "Listening locally" else "Wake word detection is disabled.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (uiState.isWakeWordEnabled) JarvisCyan else TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = uiState.isWakeWordEnabled,
                                    onCheckedChange = {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (uiState.isWakeWordEnabled) {
                                            viewModel.toggleWakeWord()
                                        } else {
                                            if (hasPermission) {
                                                micPermissionDenied = false
                                                viewModel.toggleWakeWord()
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = JarvisCyan,
                                        checkedTrackColor = JarvisSurfaceVariant,
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = JarvisSurface
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(JarvisSurfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Wake phrase:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "\"Hey JARVIS\"",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = JarvisCyan
                                )
                            }

                            if (micPermissionDenied) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(com.jarvis.assistant.ui.theme.JarvisAmber.copy(alpha = 0.12f))
                                        .border(1.dp, com.jarvis.assistant.ui.theme.JarvisAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Microphone permission is required to detect \"Hey JARVIS\" locally.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = com.jarvis.assistant.ui.theme.JarvisAmber
                                    )
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = com.jarvis.assistant.ui.theme.JarvisAmber,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Grant Permission", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Text(
                                text = "Wake word listening runs strictly on-device. Audio is processed locally and never streamed to the cloud or OpenAI.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Persistent Memory Section
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JarvisCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = JarvisSurface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(JarvisSurfaceVariant)
                                            .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Memory,
                                            contentDescription = "Memory",
                                            tint = JarvisCyan,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(14.dp))

                                    Column {
                                        Text(
                                            text = "Persistent Memory",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Local SQLite Room DB",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(JarvisSurfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "${uiState.storedMemoryCount} / 500",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Text(
                                text = "Stores explicit user preferences, personal context, and instructions. Sensitive secrets (passwords, tokens, OTPs) are strictly blocked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = onNavigateToMemories,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = JarvisCyan
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("View Memories", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }

                                androidx.compose.material3.OutlinedButton(
                                    onClick = { showClearAllConfirm = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = com.jarvis.assistant.ui.theme.JarvisAmber
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, com.jarvis.assistant.ui.theme.JarvisAmber.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Clear All", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Conversational Context Section (Milestone 10)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                JarvisCyan.copy(alpha = 0.25f),
                                RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = JarvisSurface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(JarvisSurfaceVariant)
                                            .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Psychology,
                                            contentDescription = "Context",
                                            tint = JarvisCyan,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(14.dp))

                                    Column {
                                        Text(
                                            text = "Conversational Context",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Short-term In-Memory Buffer",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(JarvisSurfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "BOUNDED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = JarvisCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Text(
                                text = "Tracks short-term conversation turns, foreground app context, and reference resolution for follow-up commands. Lazily expires after 5 minutes of inactivity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )

                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.clearCurrentContext() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = com.jarvis.assistant.ui.theme.JarvisAmber
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, com.jarvis.assistant.ui.theme.JarvisAmber.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Clear Current Context", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Floating Assistant Overlay Section (Milestone 11)
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val isOverlayRunning by com.jarvis.assistant.overlay.JarvisOverlayController.isOverlayRunning.collectAsState()
                    val canDrawOverlaysState = androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(
                            com.jarvis.assistant.overlay.JarvisOverlayController.canDrawOverlays(context)
                        )
                    }
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

                    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                canDrawOverlaysState.value = com.jarvis.assistant.overlay.JarvisOverlayController.canDrawOverlays(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        canDrawOverlaysState.value = com.jarvis.assistant.overlay.JarvisOverlayController.canDrawOverlays(context)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    val canDraw = canDrawOverlaysState.value

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (canDraw) JarvisCyan.copy(alpha = 0.35f) else com.jarvis.assistant.ui.theme.JarvisAmber.copy(alpha = 0.35f),
                                RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = JarvisSurface.copy(alpha = 0.9f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(JarvisSurfaceVariant)
                                            .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = "Floating Overlay",
                                            tint = if (canDraw) JarvisCyan else com.jarvis.assistant.ui.theme.JarvisAmber,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(14.dp))

                                    Column {
                                        Text(
                                            text = "Floating Overlay",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Persistent HUD & Quick Controls",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(JarvisSurfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = if (canDraw) "OVERLAY: ENABLED" else "OVERLAY: PERMISSION REQUIRED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (canDraw) JarvisCyan else com.jarvis.assistant.ui.theme.JarvisAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Text(
                                text = "Allows the futuristic JARVIS orb to float above third-party applications for instant access to voice commands, screen analysis, and task progression.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )

                            if (!canDraw) {
                                Button(
                                    onClick = {
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = com.jarvis.assistant.ui.theme.JarvisAmber,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("ENABLE OVERLAY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (isOverlayRunning) {
                                        Button(
                                            onClick = {
                                                com.jarvis.assistant.overlay.JarvisOverlayController.stopOverlay(context)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = com.jarvis.assistant.ui.theme.JarvisRed
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("DISABLE OVERLAY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                com.jarvis.assistant.overlay.JarvisOverlayController.startOverlay(context)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = JarvisCyan,
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("START FLOATING ORB", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SettingsSectionCard(
                        title = "Privacy",
                        subtitle = "Local encryption, offline fallback, diagnostics",
                        icon = Icons.Outlined.Security,
                        statusText = "Encrypted"
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "JARVIS CORE v1.0.0 // MILESTONE 6 BUILD",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        if (showClearAllConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showClearAllConfirm = false },
                title = { Text("Clear All Memories?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently delete all stored preferences and memories from SQLite. This action cannot be undone.", color = TextSecondary) },
                containerColor = JarvisSurface,
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllMemories()
                            showClearAllConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = com.jarvis.assistant.ui.theme.JarvisAmber)
                    ) {
                        Text("Delete Everything", color = JarvisSurface, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showClearAllConfirm = false }) {
                        Text("Cancel", color = JarvisCyan)
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, JarvisCyan.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = JarvisSurface.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(JarvisSurfaceVariant)
                        .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = JarvisCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.size(14.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisSurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
