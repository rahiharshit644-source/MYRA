package com.soltini.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.soltini.app.ui.components.BadgeStyle
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.settings.AiConfigSection
import com.soltini.app.ui.settings.MemorySection
import com.soltini.app.ui.settings.NotificationsSettingsSection
import com.soltini.app.ui.settings.PermissionsDashboardSection
import com.soltini.app.ui.settings.ScreenCompanionSection
import com.soltini.app.ui.settings.SettingsHeader
import com.soltini.app.ui.settings.SettingsMenuCard
import com.soltini.app.ui.settings.SettingsPermChecks
import com.soltini.app.ui.settings.VoiceSecuritySection
import com.soltini.app.ui.theme.AccentPurple
import com.soltini.app.ui.theme.AmberWarning
import com.soltini.app.ui.theme.LocalMyraTokens
import com.soltini.app.ui.theme.MintAccentDark
import com.soltini.app.ui.theme.PastelBlueBadge
import com.soltini.app.ui.theme.PastelMintBadge
import com.soltini.app.ui.theme.PastelPinkBadge
import com.soltini.app.ui.theme.PastelPurpleBadge
import com.soltini.app.ui.theme.PastelYellowBadge
import com.soltini.app.ui.theme.PurpleAccentStart
import com.soltini.app.ui.theme.myraBackground

// ── Screen Destination Routing ───────────────────────────────────────────────
enum class SettingsRoute {
    MAIN,
    PERMISSIONS,
    API,
    BANKING_SECURITY,
    SCREEN_COMPANION,
    MEMORY,
    NOTIFICATIONS,
    ARCHITECTURE,
    STORAGE_RAG,
    SCHEDULED_TASKS,
    VOICE_ID,
    PUBLIC_APIS_HUB,
    ORB_STYLE,
    AGENT_LOG,
    APPEARANCE
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel? = null,
    onOpenHomeAutomation: () -> Unit = {},
    onOpenVoiceEnrollment: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    var currentRoute by remember { mutableStateOf(SettingsRoute.MAIN) }

    BackHandler(enabled = currentRoute != SettingsRoute.MAIN) {
        currentRoute = SettingsRoute.MAIN
    }

    // --- Permission states ---
    var hasMic by remember { mutableStateOf(SettingsPermChecks.checkMic(context)) }
    var hasOverlay by remember { mutableStateOf(SettingsPermChecks.checkOverlay(context)) }
    var hasBattery by remember { mutableStateOf(SettingsPermChecks.checkBattery(context)) }
    var hasAccessibility by remember { mutableStateOf(SettingsPermChecks.checkAccessibility(context)) }
    var hasAssistantRole by remember { mutableStateOf(SettingsPermChecks.checkAssistantRole(context)) }
    var hasNotificationAccess by remember { mutableStateOf(SettingsPermChecks.checkNotificationAccess(context)) }
    var hasPhoneControl by remember { mutableStateOf(SettingsPermChecks.checkPhoneCallControl(context)) }
    var hasRestrictedSettings by remember { mutableStateOf(SettingsPermChecks.checkRestrictedSettings(context)) }
    val requiresAutoStart by remember { mutableStateOf(SettingsPermChecks.checkRequiresAutoStart(context)) }
    var autoStartRequested by remember {
        mutableStateOf(
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("autostart_requested", false)
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMic = granted }

    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPhoneControl = SettingsPermChecks.checkPhoneCallControl(context)
        if (hasPhoneControl) {
            com.soltini.app.telephony.CallNotificationManager.getInstance(context).startListening()
        }
    }

    val roleLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            hasAssistantRole = SettingsPermChecks.checkAssistantRole(context)
        }
    } else null

    // Refresh permission states on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMic = SettingsPermChecks.checkMic(context)
                hasOverlay = SettingsPermChecks.checkOverlay(context)
                hasBattery = SettingsPermChecks.checkBattery(context)
                hasAccessibility = SettingsPermChecks.checkAccessibility(context)
                hasAssistantRole = SettingsPermChecks.checkAssistantRole(context)
                hasNotificationAccess = SettingsPermChecks.checkNotificationAccess(context)
                hasPhoneControl = SettingsPermChecks.checkPhoneCallControl(context)
                hasRestrictedSettings = SettingsPermChecks.checkRestrictedSettings(context)
                autoStartRequested = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .getBoolean("autostart_requested", false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .myraBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Navigation bar header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassCard(
                    modifier = Modifier.clickable {
                        if (currentRoute == SettingsRoute.MAIN) onBack() else currentRoute = SettingsRoute.MAIN
                    },
                    cornerRadius = 20.dp,
                    contentPadding = 8.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = PurpleAccentStart,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (currentRoute == SettingsRoute.MAIN) "Done" else "Back",
                            color = LocalMyraTokens.current.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }

                if (currentRoute != SettingsRoute.MAIN) {
                    val routeTitle = when (currentRoute) {
                        SettingsRoute.SCREEN_COMPANION -> "Screen Companion"
                        SettingsRoute.PERMISSIONS -> "Permissions & Agent"
                        SettingsRoute.VOICE_ID -> "Voice ID"
                        SettingsRoute.NOTIFICATIONS -> "Notifications"
                        SettingsRoute.API -> "API & Persona"
                        SettingsRoute.MEMORY -> "Memory System"
                        SettingsRoute.STORAGE_RAG -> "Storage & RAG"
                        SettingsRoute.ARCHITECTURE -> "Core Architecture"
                        SettingsRoute.SCHEDULED_TASKS -> "Scheduled Tasks"
                        SettingsRoute.PUBLIC_APIS_HUB -> "Public APIs Hub"
                        SettingsRoute.ORB_STYLE -> "Floating Orb"
                        SettingsRoute.AGENT_LOG -> "Agent Log"
                        SettingsRoute.APPEARANCE -> "Appearance & Themes"
                        else -> ""
                    }
                    GlassStatusBadge(text = routeTitle, style = BadgeStyle.PURPLE)
                }
            }

            if (currentRoute == SettingsRoute.MAIN) {
                SettingsHeader()
                Spacer(modifier = Modifier.height(22.dp))
            }

            when (currentRoute) {
                SettingsRoute.MAIN -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsMenuCard(
                            title = "Appearance & Themes",
                            subtitle = "Classic MYRA or Blue Glass",
                            icon = Icons.Default.Palette
                        ) { currentRoute = SettingsRoute.APPEARANCE }

                        SettingsMenuCard(
                            title = "1,750+ Public APIs Hub",
                            subtitle = "51 categories, zero-auth filter & live API playground",
                            icon = Icons.Default.Language,
                            badgeColor = PastelPurpleBadge,
                            iconTint = PurpleAccentStart
                        ) { currentRoute = SettingsRoute.PUBLIC_APIS_HUB }

                        SettingsMenuCard(
                            title = "Live Screen Companion",
                            subtitle = "Proactive visual context & privacy shield",
                            icon = Icons.Default.Visibility,
                            badgeColor = PastelMintBadge,
                            iconTint = MintAccentDark
                        ) { currentRoute = SettingsRoute.SCREEN_COMPANION }

                        SettingsMenuCard(
                            title = "Permissions & Agent",
                            subtitle = "Mic, overlay, accessibility & phone control",
                            icon = Icons.Default.Security,
                            badgeColor = PastelPurpleBadge,
                            iconTint = PurpleAccentStart
                        ) { currentRoute = SettingsRoute.PERMISSIONS }

                        SettingsMenuCard(
                            title = "Voice ID & Verification",
                            subtitle = "Biometric speaker gate & live test console",
                            icon = Icons.Default.RecordVoiceOver,
                            badgeColor = PastelPinkBadge,
                            iconTint = AccentPurple
                        ) { currentRoute = SettingsRoute.VOICE_ID }

                        SettingsMenuCard(
                            title = "Notifications & Direct Reply",
                            subtitle = "Read & reply to incoming messages by voice",
                            icon = Icons.Default.Notifications,
                            badgeColor = PastelYellowBadge,
                            iconTint = AmberWarning
                        ) { currentRoute = SettingsRoute.NOTIFICATIONS }

                        SettingsMenuCard(
                            title = "API & Persona",
                            subtitle = "Gemini key, assistant voice, persona & VAD",
                            icon = Icons.Default.Person,
                            badgeColor = PastelPurpleBadge,
                            iconTint = PurpleAccentStart
                        ) { currentRoute = SettingsRoute.API }

                        SettingsMenuCard(
                            title = "Memory System",
                            subtitle = "Facts, conversation logs & encrypted backup",
                            icon = Icons.Default.Storage,
                            badgeColor = PastelMintBadge,
                            iconTint = MintAccentDark
                        ) { currentRoute = SettingsRoute.MEMORY }

                        SettingsMenuCard(
                            title = "Storage & RAG Knowledge",
                            subtitle = "Authorized SAF folders & document search",
                            icon = Icons.Default.FolderOpen,
                            badgeColor = PastelBlueBadge,
                            iconTint = PurpleAccentStart
                        ) { currentRoute = SettingsRoute.STORAGE_RAG }

                        SettingsMenuCard(
                            title = "Modular Architecture & Registry",
                            subtitle = "Orchestrator, Memory 2.0 & 1,750+ APIs",
                            icon = Icons.Default.Memory,
                            badgeColor = PastelPinkBadge,
                            iconTint = AccentPurple
                        ) { currentRoute = SettingsRoute.ARCHITECTURE }

                        SettingsMenuCard(
                            title = "Scheduled Tasks & Experience",
                            subtitle = "Proactive routines, geofences & learned flows",
                            icon = Icons.Default.Schedule,
                            badgeColor = PastelYellowBadge,
                            iconTint = AmberWarning
                        ) { currentRoute = SettingsRoute.SCHEDULED_TASKS }

                        SettingsMenuCard(
                            title = "Floating Orb",
                            subtitle = "Choose the orb that floats over other apps",
                            icon = Icons.Default.AutoAwesome
                        ) { currentRoute = SettingsRoute.ORB_STYLE }

                        SettingsMenuCard(
                            title = "Agent Log",
                            subtitle = "Failures, learned routines & interrupted tasks",
                            icon = Icons.Default.Schedule
                        ) { currentRoute = SettingsRoute.AGENT_LOG }

                        SettingsMenuCard(
                            title = "Home Automation (ESP32)",
                            subtitle = "Smart home MQTT relays & firmware generator",
                            icon = Icons.Default.ElectricBolt,
                            badgeColor = PastelMintBadge,
                            iconTint = MintAccentDark
                        ) { onOpenHomeAutomation() }

                        Spacer(Modifier.height(14.dp))
                        AppIdentityRow()

                        Spacer(Modifier.height(90.dp))
                    }
                }

                SettingsRoute.PUBLIC_APIS_HUB -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        PublicApisHubScreen(
                            onBack = { currentRoute = SettingsRoute.MAIN },
                            onAskMyra = { prompt ->
                                viewModel?.sendUserMessage(prompt)
                                onBack()
                            }
                        )
                    }
                }

                SettingsRoute.VOICE_ID -> {
                    VoiceSecuritySection(
                        context = context,
                        onOpenVoiceEnrollment = onOpenVoiceEnrollment,
                        onNavigateBack = { currentRoute = SettingsRoute.MAIN }
                    )
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.SCHEDULED_TASKS -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ScheduledTasksScreen(
                            onBack = { currentRoute = SettingsRoute.MAIN }
                        )
                    }
                }

                SettingsRoute.STORAGE_RAG -> {
                    StorageRagSettingsSection(
                        context = context,
                        appSettings = viewModel?.getAppSettings()
                    )
                    Spacer(Modifier.height(14.dp))
                    ConversationMemoryCard(
                        context = context,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.ARCHITECTURE -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp)) {
                        ModularArchitectureScreen(
                            viewModel = viewModel,
                            context = context,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                SettingsRoute.APPEARANCE -> {
                    AppearanceSettingsSection()
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.ORB_STYLE -> {
                    OrbStyleSettingsSection(appSettings = viewModel?.getAppSettings())
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.AGENT_LOG -> {
                    AgentLogSection(context = context)
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.SCREEN_COMPANION -> {
                    ScreenCompanionSection(context = context, appSettings = viewModel?.getAppSettings())
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.NOTIFICATIONS -> {
                    NotificationsSettingsSection(context = context)
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.API -> {
                    AiConfigSection(
                        appSettings = viewModel?.getAppSettings(),
                        onSaveAndReconnect = { viewModel?.reconnect() }
                    )
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.MEMORY -> {
                    MemorySection(
                        memory2Engine = viewModel?.getMemory2Engine(),
                        appSettings = viewModel?.getAppSettings()
                    )
                    Spacer(Modifier.height(90.dp))
                }

                SettingsRoute.PERMISSIONS -> {
                    PermissionsDashboardSection(
                        context = context,
                        hasMic = hasMic,
                        hasOverlay = hasOverlay,
                        hasBattery = hasBattery,
                        hasAccessibility = hasAccessibility,
                        hasAssistantRole = hasAssistantRole,
                        hasNotificationAccess = hasNotificationAccess,
                        hasPhoneControl = hasPhoneControl,
                        hasRestrictedSettings = hasRestrictedSettings,
                        requiresAutoStart = requiresAutoStart,
                        autoStartRequested = autoStartRequested,
                        onGrantMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onGrantPhoneControl = {
                            phonePermLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.READ_CALL_LOG,
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.ANSWER_PHONE_CALLS,
                                    Manifest.permission.CALL_PHONE
                                )
                            )
                        },
                        roleLauncher = roleLauncher,
                        onAutoStartRequestedChange = { autoStartRequested = it }
                    )
                    Spacer(Modifier.height(90.dp))
                }

                else -> {}
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/** Settings row: shows the app's signing identity and whether it matches the pinned official certificate. */
@Composable
private fun AppIdentityRow() {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("App Identity & Security", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Shows which certificate this app is signed with and whether it matches your official build. Saved API keys and website passwords are encrypted on this phone.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { show = true }) { Text("View details") }
    }

    if (show) {
        val report = remember { com.soltini.app.security.AppIdentityVerifier.report(ctx) }
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("App Identity & Security") },
            text = { Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Myra app identity", report))
                }) { Text("Copy") }
            }
        )
    }
}
