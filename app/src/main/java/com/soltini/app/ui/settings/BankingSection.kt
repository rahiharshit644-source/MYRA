package com.soltini.app.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.security.VoiceUnlockActivity
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.components.*
import com.soltini.app.ui.theme.*
import com.soltini.app.voiceprint.UnifiedVoiceAuth

@Composable
fun BankingProtectionCard(context: Context) {
    val appSettings = remember { AppSettings(context) }
    val voiceAuth = remember { UnifiedVoiceAuth.getInstance(context) }
    var autoHideEnabled by remember { mutableStateOf(appSettings.isBankingProtectionEnabled) }
    var isPaused by remember {
        mutableStateOf(
            appSettings.isBankingModePaused ||
                (com.soltini.app.overlay.OverlayService.getInstance()?.isHiddenForBanking() == true)
        )
    }
    var isVoiceLockEnrolled by remember { mutableStateOf(voiceAuth.isOwnerEnrolled()) }
    var isVoiceLockActive by remember { mutableStateOf(voiceAuth.isVoiceLockEnabled()) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Security,
                badgeColor = PastelMintBadge,
                iconTint = MintAccentDark,
                size = 46.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Banking App Protection", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Fixes banking apps (GPay, PhonePe, Paytm, YONO, SBI, HDFC) closing or blocking launch",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp, lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Auto-Hide Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Auto-Hide Overlay & A11y", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "Auto-hides floating orb & pauses screen reading whenever a banking app comes to foreground",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            GlassSwitch(
                checked = autoHideEnabled,
                onCheckedChange = { checked ->
                    autoHideEnabled = checked
                    appSettings.isBankingProtectionEnabled = checked
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Manual Pause Toggle Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Manual Pause (Banking Mode)", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    if (isPaused) "Overlay is PAUSED right now" else "Overlay is ACTIVE",
                    color = if (isPaused) AmberWarning else MintAccentDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            GlassSecondaryButton(
                text = if (isPaused) "Resume" else "Pause Now",
                onClick = {
                    if (voiceAuth.isOwnerEnrolled() && voiceAuth.isVoiceLockEnabled() && !voiceAuth.isOwnerVerifiedRecently(30_000L)) {
                        VoiceUnlockActivity.startForBankingToggle(context)
                    } else {
                        val newState = !isPaused
                        isPaused = newState
                        appSettings.isBankingModePaused = newState
                        com.soltini.app.overlay.OverlayService.getInstance()?.setHiddenForBanking(newState)
                    }
                },
                containerColor = if (isPaused) PastelMintBadge else PastelYellowBadge,
                contentColor = if (isPaused) MintAccentDark else AmberWarning
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Voice-Lock Banking Mode Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF6F3FC)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        PastelIconBadge(
                            icon = Icons.Default.Mic,
                            badgeColor = PastelPurpleBadge,
                            iconTint = PurpleAccentStart,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Voice-Lock Banking Mode", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                if (isVoiceLockEnrolled) "Voice Verified & Protected" else "Not enrolled (Tap below to enroll)",
                                color = if (isVoiceLockEnrolled) MintAccentDark else AmberWarning,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isVoiceLockEnrolled) {
                        GlassSwitch(
                            checked = isVoiceLockActive,
                            onCheckedChange = { checked ->
                                isVoiceLockActive = checked
                                voiceAuth.setVoiceLockEnabled(checked)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Protects sensitive actions (overlay resume/unpause and confirmed destructive file operations) with acoustic voice embedding verification.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(10.dp))
                GlassGradientButton(
                    text = if (isVoiceLockEnrolled) "Re-enroll Voice Signature" else "Enroll Boss Voice",
                    icon = Icons.Default.Mic,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { VoiceUnlockActivity.startEnrollment(context) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassSecondaryButton(
            text = "Open Accessibility Settings",
            icon = Icons.Default.Accessibility,
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
