package com.jarvis.assistant.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisRed
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.TextPrimary

@Composable
fun JarvisQuickActionsMenu(
    onActionSelected: (QuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(220.dp)
            .border(1.dp, JarvisCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisBackground.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = "JARVIS ACTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            HorizontalDivider(
                color = JarvisCyan.copy(alpha = 0.2f),
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            QuickActionItem(
                title = "Ask JARVIS",
                icon = Icons.Outlined.Mic,
                iconColor = JarvisCyan,
                onClick = { onActionSelected(QuickAction.ASK_JARVIS) }
            )

            QuickActionItem(
                title = "Analyze Screen",
                icon = Icons.Outlined.Visibility,
                iconColor = JarvisCyan,
                onClick = { onActionSelected(QuickAction.ANALYZE_SCREEN) }
            )

            QuickActionItem(
                title = "Go Home",
                icon = Icons.Outlined.Home,
                iconColor = Color(0xFFB0BEC5),
                onClick = { onActionSelected(QuickAction.GO_HOME) }
            )

            QuickActionItem(
                title = "Back",
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                iconColor = Color(0xFFB0BEC5),
                onClick = { onActionSelected(QuickAction.BACK) }
            )

            QuickActionItem(
                title = "Stop Current Task",
                icon = Icons.Outlined.StopCircle,
                iconColor = JarvisRed,
                onClick = { onActionSelected(QuickAction.STOP_TASK) }
            )

            QuickActionItem(
                title = "Open JARVIS App",
                icon = Icons.Outlined.OpenInNew,
                iconColor = JarvisAmber,
                onClick = { onActionSelected(QuickAction.OPEN_JARVIS_APP) }
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
