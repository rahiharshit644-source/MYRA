package com.soltini.app.ui.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.ui.components.BadgeStyle
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassGradientButton
import com.soltini.app.ui.components.GlassSecondaryButton
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.AmberWarning
import com.soltini.app.ui.theme.LocalMyraTokens
import com.soltini.app.ui.theme.MintAccentDark
import com.soltini.app.ui.theme.MiuiOrange
import com.soltini.app.ui.theme.PastelMintBadge
import com.soltini.app.ui.theme.PastelPurpleBadge
import com.soltini.app.ui.theme.PastelYellowBadge
import com.soltini.app.ui.theme.PurpleAccentStart
import com.soltini.app.ui.theme.RedError

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    tier: PermTier,
    onGrant: () -> Unit
) {
    val (badgeBg, iconTint, badgeStyle, tierLabel) = when (tier) {
        PermTier.REQUIRED -> Quad(Color(0xFFFFE5EA), RedError, BadgeStyle.ERROR, "Required")
        PermTier.AGENT -> Quad(PastelPurpleBadge, PurpleAccentStart, BadgeStyle.PURPLE, "Agent")
        PermTier.RECOMMENDED -> Quad(PastelYellowBadge, AmberWarning, BadgeStyle.WARNING, "Recommended")
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PastelIconBadge(
                icon = if (isGranted) Icons.Default.CheckCircle else icon,
                badgeColor = if (isGranted) PastelMintBadge else badgeBg,
                iconTint = if (isGranted) MintAccentDark else iconTint,
                size = 48.dp,
                iconSize = 24.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassStatusBadge(
                        text = if (isGranted) "Active" else tierLabel,
                        style = if (isGranted) BadgeStyle.ACTIVE else badgeStyle
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.width(10.dp))
                GlassGradientButton(
                    text = "Grant",
                    onClick = onGrant
                )
            }
        }
    }
}

@Composable
fun RestrictedSettingsCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PastelIconBadge(
                icon = Icons.Default.Warning,
                badgeColor = PastelYellowBadge,
                iconTint = AmberWarning,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "Enable Restricted Settings",
                    color = AmberWarning,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "On Android 13+, sideloaded apps need extra permission to access Accessibility.\n\n" +
                        "1. Go to Settings → Apps → Myra\n" +
                        "2. Tap the three-dot menu (⋮) at the top right\n" +
                        "3. Tap \"Allow restricted settings\"\n" +
                        "4. Come back and tap Grant on Accessibility Service above.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun XiaomiActionsCard(context: Context) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Warning,
                badgeColor = PastelYellowBadge,
                iconTint = MiuiOrange,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "MIUI Background Kill Protection",
                color = MiuiOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your phone aggressively kills background apps. Use the buttons below to open each setting:",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        MiuiActionRow(
            label = "1. Open Autostart Settings",
            hint = "Security → Permissions → Autostart → toggle ON",
            buttonLabel = "Open"
        ) {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = context.packageManager.resolveActivity(intent, 0)
            if (resolved != null) {
                try { context.startActivity(intent) } catch (_: Exception) {}
            } else {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MiuiActionRow(
            label = "2. Lock in Recent Apps",
            hint = "Recents → Long-press Myra card → tap 🔒 lock icon",
            buttonLabel = null
        ) {}

        Spacer(modifier = Modifier.height(12.dp))

        GlassSecondaryButton(
            text = "Re-open Xiaomi Setup Guide",
            icon = Icons.Default.AutoAwesome,
            onClick = {
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("miui_onboarding_complete", false).apply()
                Toast.makeText(
                    context,
                    "Relaunch Myra to see the full setup guide",
                    Toast.LENGTH_LONG
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
            containerColor = PastelYellowBadge,
            contentColor = MiuiOrange
        )
    }
}

@Composable
fun MiuiActionRow(
    label: String,
    hint: String,
    buttonLabel: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = LocalMyraTokens.current.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (buttonLabel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            GlassSecondaryButton(
                text = buttonLabel,
                onClick = onClick
            )
        }
    }
}

@Composable
fun PermissionSummaryBar(
    grantedRequired: Int,
    totalRequired: Int,
    grantedAgent: Int,
    totalAgent: Int
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem(
                label = "Core Permissions",
                granted = grantedRequired,
                total = totalRequired,
                color = if (grantedRequired == totalRequired) MintAccentDark else RedError
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(42.dp)
                    .background(Color(0xFFE4DCF5))
            )
            SummaryItem(
                label = "Agent Capabilities",
                granted = grantedAgent,
                total = totalAgent,
                color = if (grantedAgent == totalAgent) MintAccentDark else PurpleAccentStart
            )
        }
    }
}

@Composable
fun SummaryItem(label: String, granted: Int, total: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$granted / $total",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Text(label, color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
    }
}

@Composable
fun PermissionsDashboardSection(
    context: Context,
    hasMic: Boolean,
    hasOverlay: Boolean,
    hasBattery: Boolean,
    hasAccessibility: Boolean,
    hasAssistantRole: Boolean,
    hasNotificationAccess: Boolean,
    hasPhoneControl: Boolean,
    hasRestrictedSettings: Boolean,
    requiresAutoStart: Boolean,
    autoStartRequested: Boolean,
    onGrantMic: () -> Unit,
    onGrantPhoneControl: () -> Unit,
    roleLauncher: ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult>?,
    onAutoStartRequestedChange: (Boolean) -> Unit
) {
    // ── Section: Required for Basic Function ────────────────
    SectionLabel("Required Permissions")
    Spacer(modifier = Modifier.height(12.dp))

    PermissionCard(
        icon = Icons.Default.Mic,
        title = "Microphone",
        description = "Capture your voice for real-time AI conversation.",
        isGranted = hasMic,
        tier = PermTier.REQUIRED,
        onGrant = onGrantMic
    )
    Spacer(modifier = Modifier.height(12.dp))
    PermissionCard(
        icon = Icons.Default.Layers,
        title = "Display Over Other Apps",
        description = "Show the Myra floating orb on top of any screen.",
        isGranted = hasOverlay,
        tier = PermTier.REQUIRED,
        onGrant = {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // ── Section: Agentic (AI Agent Capabilities) ─────────────
    SectionLabel("AI Agent Capabilities")
    Spacer(modifier = Modifier.height(12.dp))

    PermissionCard(
        icon = Icons.Default.Assistant,
        title = "Default Device Assistant",
        description = "Replace Google Assistant / Gemini. Long-press home to trigger Myra — exactly like Gemini.",
        isGranted = hasAssistantRole,
        tier = PermTier.AGENT,
        onGrant = {
            var launched = false
            if (!launched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                    val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                    roleLauncher?.launch(roleIntent)
                    launched = true
                } catch (_: Exception) {}
            }
            if (!launched) {
                try {
                    context.startActivity(
                        Intent("com.android.settings.action.MANAGE_VOICE_INPUT_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    launched = true
                } catch (_: Exception) {}
            }
            if (!launched) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    launched = true
                } catch (_: Exception) {}
            }
            if (!launched) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        }
    )
    Spacer(modifier = Modifier.height(12.dp))
    PermissionCard(
        icon = Icons.Default.Accessibility,
        title = "Accessibility Service",
        description = "Enables locking the device screen by voice. Myra only uses this to perform lock/unlock actions.",
        isGranted = hasAccessibility,
        tier = PermTier.AGENT,
        onGrant = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    )

    Spacer(modifier = Modifier.height(12.dp))
    PermissionCard(
        icon = Icons.Default.Notifications,
        title = "Notification Access",
        description = "Enables Myra to read incoming messages and notifications on-demand and reply directly.",
        isGranted = hasNotificationAccess,
        tier = PermTier.AGENT,
        onGrant = {
            try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {}
        }
    )

    Spacer(modifier = Modifier.height(12.dp))
    PermissionCard(
        icon = Icons.Default.Call,
        title = "Phone & Call Control",
        description = "Enables Myra to announce incoming callers aloud ('Boss, Rahul ka call aa raha hai') and answer or reject calls hands-free by voice.",
        isGranted = hasPhoneControl,
        tier = PermTier.AGENT,
        onGrant = onGrantPhoneControl
    )

    if (!hasRestrictedSettings && !hasAccessibility) {
        Spacer(modifier = Modifier.height(12.dp))
        RestrictedSettingsCard()
    }

    Spacer(modifier = Modifier.height(20.dp))

    // ── Section: Background & System ────────────────────────
    SectionLabel("Background & System")
    Spacer(modifier = Modifier.height(12.dp))

    PermissionCard(
        icon = Icons.Default.BatteryAlert,
        title = "Ignore Battery Optimization",
        description = "Prevents Android from killing the assistant in the background.",
        isGranted = hasBattery,
        tier = PermTier.RECOMMENDED,
        onGrant = {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    )

    if (requiresAutoStart) {
        Spacer(modifier = Modifier.height(12.dp))
        PermissionCard(
            icon = Icons.Default.PowerSettingsNew,
            title = "Auto-Start (${Build.MANUFACTURER})",
            description = "Allow Myra to automatically start in the background on your device.",
            isGranted = autoStartRequested,
            tier = PermTier.RECOMMENDED,
            onGrant = {
                SettingsPermChecks.launchAutoStart(context)
                onAutoStartRequestedChange(true)
                context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("autostart_requested", true).apply()
            }
        )
    }

    if (Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)) {
        Spacer(modifier = Modifier.height(16.dp))
        XiaomiActionsCard(context = context)
    }
}
