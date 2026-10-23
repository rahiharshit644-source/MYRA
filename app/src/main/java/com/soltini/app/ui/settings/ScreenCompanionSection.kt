package com.soltini.app.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.companion.ScreenCompanionManager
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.components.*
import com.soltini.app.ui.theme.*

@Composable
fun ScreenCompanionSection(
    context: Context,
    appSettings: AppSettings?
) {
    val companionManager = remember { com.soltini.app.companion.ScreenCompanionManager.getInstance(context) }
    var isLiveCompanionOn by remember { mutableStateOf(companionManager.isLiveCompanionEnabled.value) }
    var isBankingProtected by remember { mutableStateOf(appSettings?.isBankingProtectionEnabled ?: true) }
    var snapshotResult by remember { mutableStateOf<com.soltini.app.companion.ScreenCompanionManager.ScreenSnapshot?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 26.dp,
            contentPadding = 22.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PastelIconBadge(
                    icon = Icons.Default.Visibility,
                    badgeColor = PastelMintBadge,
                    iconTint = MintAccentDark,
                    size = 68.dp,
                    iconSize = 34.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Live Screen Companion",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalMyraTokens.current.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Myra sits next to you like a real companion, watching your screen and giving helpful tips, corrections, and thoughts as you work or type.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMyraTokens.current.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        // Proactive Companion Toggle Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Proactive Human Eye",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "When ON, Myra notices when you are typing or stuck on a screen and speaks up with sweet, helpful advice naturally.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                GlassSwitch(
                    checked = isLiveCompanionOn,
                    onCheckedChange = { newState ->
                        isLiveCompanionOn = newState
                        companionManager.setLiveCompanionEnabled(newState)
                    }
                )
            }
        }

        // Banking & Privacy Protection Toggle Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Banking & Privacy Shield",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Instantly hides observation and clears screen memory whenever banking, UPI, or password fields are open.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                GlassSwitch(
                    checked = isBankingProtected,
                    onCheckedChange = { newState ->
                        isBankingProtected = newState
                        appSettings?.isBankingProtectionEnabled = newState
                    }
                )
            }
        }

        // Live Test Button
        GlassGradientButton(
            text = "Test Screen Observation Now",
            icon = Icons.Default.Visibility,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                snapshotResult = companionManager.captureCurrentScreenContext()
            }
        )

        // Test Output Preview
        snapshotResult?.let { snap ->
            GlassCodeBlockContainer(
                title = if (snap.isAvailable) "ACTIVE APP: ${snap.currentApp}" else "OBSERVATION STATUS",
                headerTrailing = {
                    GlassStatusBadge(
                        text = if (snap.isAvailable) "Captured" else "Unavailable",
                        style = if (snap.isAvailable) BadgeStyle.ACTIVE else BadgeStyle.WARNING
                    )
                }
            ) {
                if (!snap.isAvailable) {
                    Text(
                        snap.errorMessage ?: "Screen not available",
                        color = Color(0xFFFBBF24),
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    Text(
                        snap.toHumanSummary(),
                        color = Color(0xFFE6EDF3),
                        style = MaterialTheme.typography.labelMedium,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Example Voice Prompts
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            contentPadding = 16.dp
        ) {
            Text(
                "Try saying to Myra:",
                color = PurpleAccentStart,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("• \"Dekho screen pe kya chal raha hai\"", color = LocalMyraTokens.current.textPrimary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("• \"Yeh email check karke batao kaisa likha hai\"", color = LocalMyraTokens.current.textPrimary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("• \"Mera code dekh ke batao kya issue hai\"", color = LocalMyraTokens.current.textPrimary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("• \"Screen companion mode on karo\"", color = LocalMyraTokens.current.textPrimary, fontSize = 12.sp)
        }
    }
}
