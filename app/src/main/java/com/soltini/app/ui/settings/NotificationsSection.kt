package com.soltini.app.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
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
import com.soltini.app.messaging.BusyModeManager
import com.soltini.app.notifications.NotificationRepository
import com.soltini.app.ui.components.*
import com.soltini.app.ui.theme.*

@Composable
fun BusyModeCard(context: Context) {
    val busyManager = remember { com.soltini.app.messaging.BusyModeManager.getInstance(context) }
    var isBusy by remember { mutableStateOf(busyManager.isBusyModeActive()) }
    var reasonText by remember { mutableStateOf(busyManager.getBusyReason()) }
    var recentLogs by remember { mutableStateOf(busyManager.getRecentHistory(10)) }
    var showLogs by remember { mutableStateOf(false) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                PastelIconBadge(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    badgeColor = if (isBusy) PastelMintBadge else PastelPurpleBadge,
                    iconTint = if (isBusy) MintAccentDark else PurpleAccentStart,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Autonomous Messaging (Busy Mode)",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    GlassStatusBadge(
                        text = if (isBusy) "ACTIVE • Drafts (confirmation ke baad hi send)" else "INACTIVE",
                        style = if (isBusy) BadgeStyle.ACTIVE else BadgeStyle.INACTIVE
                    )
                }
            }

            GlassSwitch(
                checked = isBusy,
                onCheckedChange = { checked ->
                    if (checked) {
                        busyManager.enableBusyMode(reasonText)
                        isBusy = true
                    } else {
                        busyManager.disableBusyMode()
                        isBusy = false
                    }
                    recentLogs = busyManager.getRecentHistory(10)
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            "Jab aap busy hon (\"Myra main busy hoon\"), Myra WhatsApp, Instagram, Telegram aur SMS ke naye messages ka polite aur contextual reply khud bhejti hai.",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        GlassPillInput(
            value = reasonText,
            onValueChange = {
                reasonText = it
                if (isBusy) busyManager.enableBusyMode(it)
            },
            placeholder = "Busy Reason (e.g. driving, meeting, studying)",
            leadingIcon = Icons.Default.Edit
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = PastelMintBadge.copy(alpha = 0.55f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MintAccentDark,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Loop & Self-Reply Protection Active: Myra apne ya user ke messages ka reply nahi karegi. 3-minute cooldown active.",
                    color = LocalMyraTokens.current.textPrimary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        if (recentLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLogs = !showLogs },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Recent Auto-Replies (${recentLogs.size})",
                    color = PurpleAccentStart,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Icon(
                    if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = PurpleAccentStart,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (showLogs) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recentLogs.forEach { log ->
                        Surface(
                            color = Color(0xFFF6F3FC),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${log.appName}: ${log.sender}",
                                        color = LocalMyraTokens.current.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    GlassStatusBadge(text = "Auto-replied", style = BadgeStyle.ACTIVE)
                                }
                                if (log.incomingText.isNotBlank()) {
                                    Text(
                                        "In: \"${log.incomingText}\"",
                                        color = LocalMyraTokens.current.textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    "Out: \"${log.replyText}\"",
                                    color = MintAccentDark,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Notifications & Direct Reply Section (Option B) ───────────────────────────
@Composable
fun NotificationsSettingsSection(context: Context) {
    val notifRepo = remember { NotificationRepository.getInstance(context) }
    val isGranted = SettingsPermChecks.checkNotificationAccess(context)
    val ignoredApps by notifRepo.ignoredApps.collectAsState()
    val recentNotifs by notifRepo.notifications.collectAsState()
    var newAppToIgnore by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    badgeColor = if (isGranted) PastelMintBadge else PastelYellowBadge,
                    iconTint = if (isGranted) MintAccentDark else AmberWarning,
                    size = 46.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Notification Listener Access",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        if (isGranted) "Active • Myra can read & reply to notifications" else "Permission Required • Needed to read incoming messages",
                        color = if (isGranted) MintAccentDark else AmberWarning,
                        fontSize = 12.sp
                    )
                }
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(14.dp))
                GlassGradientButton(
                    text = "Enable Notification Access",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (_: Exception) {}
                    }
                )
            }
        }

        // How it works card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            contentPadding = 16.dp
        ) {
            Text(
                "💡 Voice Commands (On-Demand):",
                color = PurpleAccentStart,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "• \"Koi notification aaya kya?\" / \"Read my notifications\"\n" +
                    "• \"Rahul ko reply karo: Main 10 minute mein aa raha hoon\"\n" +
                    "• \"WhatsApp pe reply kar do: Theek hai\"\n" +
                    "• \"Chrome ke notifications ignore karo\"",
                color = LocalMyraTokens.current.textSecondary,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
        }

        // Ignored Apps Management Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Text(
                "Selective App Ignore List",
                color = LocalMyraTokens.current.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                "By default, all apps are allowed. Only apps you explicitly tell Myra or add below will be ignored.",
                color = LocalMyraTokens.current.textSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPillInput(
                    value = newAppToIgnore,
                    onValueChange = { newAppToIgnore = it },
                    placeholder = "App name (e.g. Chrome)",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                GlassGradientButton(
                    text = "Ignore",
                    icon = Icons.Default.Add,
                    enabled = newAppToIgnore.isNotBlank(),
                    onClick = {
                        if (newAppToIgnore.isNotBlank()) {
                            notifRepo.ignoreApp(newAppToIgnore)
                            newAppToIgnore = ""
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (ignoredApps.isEmpty()) {
                Text(
                    "No apps ignored. Myra reads notifications from all messaging apps when asked.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    "Currently Ignored Apps (${ignoredApps.size}):",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ignoredApps.forEach { appName ->
                        Surface(
                            color = Color(0xFFFFE5EA),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    appName.replaceFirstChar { it.uppercase() },
                                    color = RedError,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = RedError,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { notifRepo.unignoreApp(appName) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Captured Notifications Preview
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            contentPadding = 18.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Recent Captured Notifications",
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "${recentNotifs.size} in memory",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 11.sp
                    )
                }
                if (recentNotifs.isNotEmpty()) {
                    TextButton(onClick = { notifRepo.clearAllNotifications() }) {
                        Text("Clear", color = RedError, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (recentNotifs.isEmpty()) {
                Text(
                    if (isGranted) "No active notifications right now. Any notification received will appear here."
                    else "Grant Notification Access above to start capturing notifications.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentNotifs.take(8).forEach { item ->
                        Surface(
                            color = Color(0xFFF6F3FC),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            item.appName,
                                            color = PurpleAccentStart,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "• ${item.getTimeAgo()}",
                                            color = LocalMyraTokens.current.textSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    if (item.canReply) {
                                        GlassStatusBadge(text = "Direct Reply", style = BadgeStyle.ACTIVE)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    item.title,
                                    color = LocalMyraTokens.current.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                if (item.text.isNotBlank()) {
                                    Text(
                                        item.text,
                                        color = LocalMyraTokens.current.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Screen Companion Settings Section ─────────────────────────────────────────
