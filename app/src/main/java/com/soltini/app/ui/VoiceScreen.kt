package com.soltini.app.ui

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soltini.app.network.ConnectionState
import com.soltini.app.ui.components.MyraOrbState
import com.soltini.app.ui.components.MyraStatusOrb
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassQuickActionChip
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*

@Composable
fun VoiceScreen(
    viewModel: MainViewModel,
    onOpenLogs: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenHomeAutomation: () -> Unit = {},
    onOpenApisHub: () -> Unit = {},
    onOpenBrowser: () -> Unit = {},
    onOpenWeather: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appSettings = remember { com.soltini.app.settings.AppSettings(context) }
    val isBlueGlass = LocalMyraTokens.current.mode == MyraThemeMode.BLUE_GLASS // read here: LazyColumn content isn't composable
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val micAmplitude by viewModel.micAmplitude.collectAsStateWithLifecycle()
    val speakerAmplitude by viewModel.speakerAmplitude.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val transcripts by viewModel.transcripts.collectAsStateWithLifecycle()
    val hasMicPermission by viewModel.hasMicPermission.collectAsStateWithLifecycle()
    val isLitePaused by viewModel.isLitePaused.collectAsStateWithLifecycle()
    val isHardPaused by viewModel.isHardPaused.collectAsStateWithLifecycle()
    val isPaused = isLitePaused || isHardPaused

    LaunchedEffect(hasMicPermission, connectionState, isPaused) {
        if (hasMicPermission && !isPaused && !appSettings.isBedtimeQuietHoursActive() && (connectionState == ConnectionState.Disconnected || connectionState == ConnectionState.Error)) {
            viewModel.connect()
        }
    }

    val isLive = connectionState == ConnectionState.Live

    // Subtle tactile haptic cue when Myra transitions between listening and speaking
    var wasSpeaking by remember { mutableStateOf(false) }
    LaunchedEffect(isSpeaking) {
        if (isLive && isSpeaking != wasSpeaking) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            wasSpeaking = isSpeaking
        }
    }

    var hasAssistant by remember { mutableStateOf(com.soltini.app.ui.settings.SettingsPermChecks.checkAssistantRole(context)) }
    var hasAccessibility by remember { mutableStateOf(com.soltini.app.ui.settings.SettingsPermChecks.checkAccessibility(context)) }
    var hasBattery by remember { mutableStateOf(com.soltini.app.ui.settings.SettingsPermChecks.checkBattery(context)) }

    LifecycleResumeEffect(Unit) {
        hasAssistant = com.soltini.app.ui.settings.SettingsPermChecks.checkAssistantRole(context)
        hasAccessibility = com.soltini.app.ui.settings.SettingsPermChecks.checkAccessibility(context)
        hasBattery = com.soltini.app.ui.settings.SettingsPermChecks.checkBattery(context)
        onPauseOrDispose {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .myraBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Soft pastel ambient light aura
        AmbientBackground(isLive = isLive, isSpeaking = isSpeaking)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp)
        ) {
            // Top bar
            item {
                TopBar(
                    connectionState = connectionState,
                    appName = appSettings.appDisplayName,
                    onOpenLogs = onOpenLogs,
                    onOpenSettings = onOpenSettings,
                    onOpenHomeAutomation = onOpenHomeAutomation,
                    onOpenApisHub = onOpenApisHub,
                    onReconnect = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.connect()
                    }
                )
            }

            // Hero Iridescent Glass Orb Card
            item {
                HeroOrbGlassCard(
                    connectionState = connectionState,
                    isSpeaking = isSpeaking,
                    isMuted = isMuted,
                    isPaused = isPaused,
                    micAmplitude = micAmplitude,
                    speakerAmplitude = speakerAmplitude,
                    onToggleMute = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.toggleMute()
                    }
                )
            }

            // Autonomous Protocol Runner (J.A.R.V.I.S. Auto-Task Engine)
            item {
                com.soltini.app.ui.components.AutonomousProtocolCard(
                    onLaunchCustom = { goal ->
                        viewModel.sendUserMessage("Execute protocol: $goal")
                    }
                )
            }

            // OmniRoute AI Gateway & Failover Telemetry
            item {
                com.soltini.app.ui.components.OmniRouteGatewayCard()
            }

            // Blue Glass home dashboard: shortcut cards to existing destinations (Classic keeps its own nav)
            if (isBlueGlass) {
                item {
                    HomeShortcutCards(
                        onOpenBrowser = onOpenBrowser,
                        onOpenHomeAutomation = onOpenHomeAutomation,
                        onOpenApisHub = onOpenApisHub,
                        onOpenLogs = onOpenLogs,
                        onOpenWeather = onOpenWeather,
                        onOpenMemory = onOpenMemory
                    )
                }
            }

            // Banking Pause Mode Card
            item {
                DualPauseModeCard(
                    isLitePaused = isLitePaused,
                    isHardPaused = isHardPaused,
                    onToggleLite = { viewModel.toggleLitePauseMode() },
                    onToggleHard = { viewModel.toggleHardPauseMode() }
                )
            }

            // Quick voice tips / command chips
            item {
                CommandChipsRow(
                    onSelectCommand = { cmd -> viewModel.sendUserMessage(cmd) }
                )
            }

            // Background persistence warning card if battery optimization is killing the service
            if (!hasBattery) {
                item {
                    BackgroundPersistenceAlert(context = context)
                }
            }

            // Agent capabilities status cards
            item {
                AgentStatusRow(
                    hasAssistant = hasAssistant,
                    hasAccessibility = hasAccessibility,
                    hasBattery = hasBattery,
                    onOpenSettings = onOpenSettings,
                    onFixBattery = {
                        try {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            ).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val appDetails = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                ).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(appDetails)
                            } catch (_: Exception) {}
                        }
                    }
                )
            }

            // Error card
            if (errorMessage != null) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut()
                    ) {
                        ErrorCard(errorMessage!!) { viewModel.connect() }
                    }
                }
            }

            // Transcript
            if (transcripts.isNotEmpty()) {
                item {
                    TranscriptHeader(onClear = { viewModel.clearTranscript() })
                }
                items(transcripts, key = { it.id }) { entry ->
                    TranscriptBubble(entry)
                }
            }
        }

        // Floating Bottom Message / Command Pill Input Bar
        ChatCommandInputBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            onSendCommand = { cmd ->
                viewModel.sendUserMessage(cmd)
            }
        )
    }
}

@Composable
private fun ChatCommandInputBar(
    modifier: Modifier = Modifier,
    onSendCommand: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier.shadow(
            elevation = 10.dp,
            shape = CircleShape,
            ambientColor = PrimaryPurple.copy(alpha = 0.18f),
            spotColor = PrimaryPurple.copy(alpha = 0.24f)
        ),
        shape = CircleShape,
        color = GlassCardSolid,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = BadgePurpleTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_command_input"),
                placeholder = {
                    Text(
                        text = "Ask Myra or type a command...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalMyraTokens.current.textSecondary
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = TextCharcoal,
                    unfocusedTextColor = TextCharcoal,
                    cursorColor = BadgePurpleTint
                ),
                maxLines = 2,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalMyraTokens.current.textPrimary)
            )

            Spacer(modifier = Modifier.width(6.dp))

            val canSend = text.isNotBlank()
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .testTag("send_command_button")
                    .size(44.dp)
                    .shadow(
                        elevation = if (canSend) 6.dp else 0.dp,
                        shape = CircleShape,
                        ambientColor = PrimaryPurple.copy(alpha = 0.3f),
                        spotColor = PrimaryPink.copy(alpha = 0.35f)
                    )
                    .clip(CircleShape)
                    .background(
                        brush = if (canSend) PrimaryHorizontalBrush else Brush.horizontalGradient(
                            listOf(GlassSurfaceSubtle, GlassSurfaceSubtle)
                        ),
                        shape = CircleShape
                    )
                    .clickable(enabled = canSend) {
                        val trimmed = text.trim()
                        if (trimmed.isNotBlank()) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSendCommand(trimmed)
                            text = ""
                            focusManager.clearFocus()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = if (canSend) Color.White else TextMidGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Ambient Background Glow ────────────────────────────────────────────────────
@Composable
private fun AmbientBackground(isLive: Boolean, isSpeaking: Boolean) {
    val inf = rememberInfiniteTransition(label = "bg")
    val shift by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgShift"
    )
    val glowColor = when {
        !isLive -> PrimaryPurple.copy(alpha = 0.10f)
        isSpeaking -> PrimaryPink.copy(alpha = 0.18f)
        else -> SecondaryMint.copy(alpha = 0.16f)
    }
    val animGlow by animateColorAsState(glowColor, tween(800), label = "glow")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width * (0.3f + shift * 0.4f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(animGlow, Color.Transparent),
                center = Offset(cx, size.height * 0.24f),
                radius = size.width * 0.85f
            ),
            radius = size.width * 0.85f,
            center = Offset(cx, size.height * 0.24f)
        )
    }
}

// ── Top Bar ────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    connectionState: ConnectionState,
    appName: String = "Myra",
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHomeAutomation: () -> Unit,
    onOpenApisHub: () -> Unit = {},
    onReconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Brand Identity
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (LocalMyraTokens.current.mode == MyraThemeMode.BLUE_GLASS) {
            com.soltini.app.ui.components.MyraAvatarOrb(size = 46.dp)
          } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.25f),
                        spotColor = LocalMyraTokens.current.accentSecondary.copy(alpha = 0.30f)
                    )
                    .clip(CircleShape)
                    .background(LocalMyraTokens.current.accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
          }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = appName,
                    color = LocalMyraTokens.current.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Text(
                    text = "AI Companion",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Actions & Status Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(connectionState)

            if (connectionState == ConnectionState.Disconnected || connectionState == ConnectionState.Error) {
                SmallGlassIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Reconnect",
                    tint = BadgePurpleTint,
                    onClick = onReconnect
                )
            }
            SmallGlassIconButton(
                icon = Icons.Default.ElectricBolt,
                contentDescription = "Smart Home Automation",
                tint = SecondaryMintDark,
                onClick = onOpenHomeAutomation
            )
            SmallGlassIconButton(
                icon = Icons.Default.BugReport,
                contentDescription = "Logs",
                tint = TextMidGray,
                onClick = onOpenLogs
            )
            SmallGlassIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextMidGray,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun SmallGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = CircleShape,
        color = GlassCardWhite,
        modifier = Modifier
            .size(38.dp)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = PrimaryPurple.copy(alpha = 0.12f),
                spotColor = PrimaryPurple.copy(alpha = 0.16f)
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StatusPill(connectionState: ConnectionState) {
    val (bg, fg, label) = when (connectionState) {
        ConnectionState.Live -> Triple(BadgeMintBg, SecondaryMintDark, "Live")
        ConnectionState.Connecting -> Triple(BadgeYellowBg, BadgeYellowTint, "Connecting")
        ConnectionState.Reconnecting -> Triple(BadgeYellowBg, BadgeYellowTint, "Reconnecting")
        ConnectionState.Disconnected -> Triple(GlassSurfaceSubtle, TextMidGray, "Offline")
        ConnectionState.Error -> Triple(Color(0xFFFDE8E8), RedError, "Error")
    }
    GlassStatusBadge(
        text = label,
        isActive = connectionState == ConnectionState.Live,
        activeBg = bg,
        activeContent = fg,
        inactiveBg = bg,
        inactiveContent = fg
    )
}

// ── Hero Iridescent Glass Orb Card ──────────────────────────────────────────────
@Composable
private fun HeroOrbGlassCard(
    connectionState: ConnectionState,
    isSpeaking: Boolean,
    isMuted: Boolean,
    isPaused: Boolean,
    micAmplitude: Float,
    speakerAmplitude: Float,
    onToggleMute: () -> Unit
) {
    val isLive = connectionState == ConnectionState.Live && !isPaused
    val isConnecting = connectionState == ConnectionState.Connecting || connectionState == ConnectionState.Reconnecting

    // One clear state for the orb, the chip and the text, so it is obvious whether Myra is on / hearing / talking.
    val orbState = when {
        isPaused -> MyraOrbState.PAUSED
        isConnecting -> MyraOrbState.CONNECTING
        !isLive -> MyraOrbState.OFF
        isMuted -> MyraOrbState.MUTED
        isSpeaking -> MyraOrbState.SPEAKING
        else -> MyraOrbState.LISTENING
    }

    val protocolState by com.soltini.app.protocol.AutoTaskProtocolManager.getInstance(androidx.compose.ui.platform.LocalContext.current).protocolState.collectAsState()
    val isProtocolActive = protocolState?.isRunning == true

    val statusTitle = when {
        isProtocolActive -> protocolState?.name ?: "Protocol Running"
        orbState == MyraOrbState.PAUSED -> "Session Paused"
        orbState == MyraOrbState.CONNECTING -> "Connecting…"
        orbState == MyraOrbState.OFF -> "Myra is OFF"
        orbState == MyraOrbState.MUTED -> "Microphone Muted"
        orbState == MyraOrbState.SPEAKING -> "Myra is Speaking"
        else -> "Myra is Listening"
    }

    val statusSubtitle = when {
        isProtocolActive -> "Executing: ${protocolState?.steps?.getOrNull(protocolState?.currentStepIndex ?: 0)?.title ?: "Autonomous goal"}"
        orbState == MyraOrbState.PAUSED -> "Accessibility & microphone are safely paused"
        orbState == MyraOrbState.CONNECTING -> "Starting live voice, please wait"
        orbState == MyraOrbState.OFF -> "Tap reconnect in the top bar to start live voice"
        orbState == MyraOrbState.MUTED -> "Tap the mic button below to resume listening"
        orbState == MyraOrbState.SPEAKING -> "Delivering real-time voice response"
        else -> "Speak naturally or tap an autonomous protocol below"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        elevation = 8.dp,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top badge row inside hero card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassStatusBadge(
                    text = if (isProtocolActive) "Protocol Active" else orbState.chipLabel,
                    isActive = true,
                    activeBg = if (isProtocolActive) Color(0x2800E5FF) else orbState.colorFor(LocalMyraTokens.current.mode).copy(alpha = 0.14f),
                    activeContent = if (isProtocolActive) Color(0xFF0284C7) else orbState.colorFor(LocalMyraTokens.current.mode)
                )
                if (LocalMyraTokens.current.mode == MyraThemeMode.BLUE_GLASS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isProtocolActive) Color(0xFF00E5FF) else if (isLive) Color(0xFF00E5FF) else Color(0xFF94A3B8))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isProtocolActive) "AUTO-TASK RUNNING" else if (isLive) "J.A.R.V.I.S. HUD" else "STANDBY",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                } else {
                    Text(
                        text = "Floating Orb Ready",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Permanent main-screen orb: Curl Noise Orb (fixed colour, reacts only to voice level)
            MyraStatusOrb(
                state = orbState,
                level = if (!isLive || isMuted) 0f else (if (isSpeaking) speakerAmplitude else micAmplitude),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )

            if (LocalMyraTokens.current.mode == MyraThemeMode.BLUE_GLASS) {
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isProtocolActive) Color(0x2800E5FF) else Color(0x1800E5FF),
                    border = BorderStroke(1.dp, if (isProtocolActive) Color(0xFF00E5FF) else Color(0xFF00E5FF).copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isProtocolActive) "PROTOCOL: ${(protocolState?.progressPercent?.times(100))?.toInt() ?: 0}%" else "CORE: ${if (isSpeaking) "SYNTHESIZING" else if (isLive) "ACTIVE HUD" else "STANDBY"}",
                                color = Color(0xFF0369A1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                        Text(
                            text = if (isProtocolActive) protocolState?.name?.take(22) ?: "EDITH PROTOCOL v2.4" else "EDITH PROTOCOL v2.4",
                            color = Color(0xFF1E6FFB),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isProtocolActive) Color(0xFF00E5FF) else if (isLive) Color(0xFF00F5D4) else Color(0xFF94A3B8))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isProtocolActive) "EXECUTING" else if (isLive) "LINKED" else "OFFLINE",
                                color = Color(0xFF0369A1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom row: Status text + Primary Gradient Mic Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusTitle,
                        color = LocalMyraTokens.current.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusSubtitle,
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Circular Purple-Pink Gradient Mic Button with spring scale + haptics
                val micBrush = if (isMuted) {
                    Brush.linearGradient(listOf(Color(0xFFFDE8E8), Color(0xFFFCD2D2)))
                } else {
                    LocalMyraTokens.current.accentGradient
                }
                val micInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isMicPressed by micInteraction.collectIsPressedAsState()
                val micScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isMicPressed) 0.90f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "micScale"
                )
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .scale(micScale)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.30f),
                            spotColor = LocalMyraTokens.current.accentSecondary.copy(alpha = 0.35f)
                        )
                        .clip(CircleShape)
                        .background(micBrush)
                        .clickable(
                            interactionSource = micInteraction,
                            indication = null,
                            onClick = onToggleMute
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) RedError else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ── Command Chips Row ──────────────────────────────────────────────────────────
private val voiceCommands = listOf(
    "Protocol: Morning Sync start karo",
    "Protocol: Deep Focus chalao",
    "YouTube pe lofi beats chalao aur alarm set karo",
    "WhatsApp pe Rohit ko message bhejo aur camera kholo",
    "Chrome me ye form pura bhar do",
    "Zomato pe pizza search karke options dikhao",
    "Shopping app khol ke saree dikhao",
    "Protocol: System Boost activate karo",
    "Turn on living room light"
)

@Composable
private fun CommandChipsRow(
    onSelectCommand: (String) -> Unit = {}
) {
    Column {
        Text(
            text = "Quick Voice Shortcuts",
            color = LocalMyraTokens.current.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(voiceCommands) { cmd ->
                GlassQuickActionChip(
                    label = cmd,
                    icon = Icons.Default.AutoAwesome,
                    badgeBg = BadgePurpleBg,
                    badgeTint = BadgePurpleTint,
                    onClick = { onSelectCommand(cmd) }
                )
            }
        }
    }
}

// ── Agent Status Row ───────────────────────────────────────────────────────────
@Composable
private fun AgentStatusRow(
    hasAssistant: Boolean,
    hasAccessibility: Boolean,
    hasBattery: Boolean,
    onOpenSettings: () -> Unit,
    onFixBattery: () -> Unit
) {
    Column {
        Text(
            text = "Agent Capabilities",
            color = LocalMyraTokens.current.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AgentCapCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Assistant,
                title = "Assistant",
                subtitle = if (hasAssistant) "Active" else "Enable",
                isActive = hasAssistant,
                badgeBg = BadgePurpleBg,
                badgeTint = BadgePurpleTint,
                onClick = onOpenSettings
            )
            AgentCapCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Lock,
                title = "Lock Screen",
                subtitle = if (hasAccessibility) "Active" else "Enable",
                isActive = hasAccessibility,
                badgeBg = BadgePinkBg,
                badgeTint = BadgePinkTint,
                onClick = onOpenSettings
            )
            AgentCapCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ElectricBolt,
                title = "Background",
                subtitle = if (hasBattery) "Immune" else "Unrestrict",
                isActive = hasBattery,
                badgeBg = BadgeMintBg,
                badgeTint = BadgeMintTint,
                onClick = if (hasBattery) onOpenSettings else onFixBattery
            )
        }
    }
}

@Composable
private fun BackgroundPersistenceAlert(context: Context) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = 6.dp,
        containerColor = GlassCardWhite,
        onClick = {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val appDetails = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    ).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(appDetails)
                } catch (_: Exception) {}
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PastelIconBadge(
                icon = Icons.Default.ElectricBolt,
                backgroundColor = BadgeYellowBg,
                iconTint = BadgeYellowTint,
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background Running Restricted",
                    color = LocalMyraTokens.current.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Battery saver can pause Myra when the screen is off. Tap to set Unrestricted.",
                    color = LocalMyraTokens.current.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                color = BadgeYellowBg,
                shape = CircleShape
            ) {
                Text(
                    text = "Fix",
                    color = BadgeYellowTint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AgentCapCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean,
    badgeBg: Color,
    badgeTint: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier.height(114.dp),
        cornerRadius = 22.dp,
        elevation = 5.dp,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                PastelIconBadge(
                    icon = icon,
                    backgroundColor = badgeBg,
                    iconTint = badgeTint,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SecondaryMintDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    color = LocalMyraTokens.current.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    color = if (isActive) SecondaryMintDark else TextMidGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── Transcript ─────────────────────────────────────────────────────────────────
@Composable
private fun TranscriptHeader(onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Conversation",
            color = LocalMyraTokens.current.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            onClick = onClear,
            shape = CircleShape,
            color = BadgePurpleBg
        ) {
            Text(
                text = "Clear",
                color = BadgePurpleTint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TranscriptBubble(entry: TranscriptEntry) {
    val isUser = entry.sender == TranscriptEntry.Sender.USER
    val bubbleShape = RoundedCornerShape(
        topStart = if (isUser) 22.dp else 6.dp,
        topEnd = if (isUser) 6.dp else 22.dp,
        bottomStart = 22.dp,
        bottomEnd = 22.dp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .shadow(4.dp, CircleShape, ambientColor = PrimaryPurple.copy(alpha = 0.2f))
                    .clip(CircleShape)
                    .background(PrimaryGradientBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        val bubbleBrush: Brush = if (isUser) {
            PrimaryHorizontalBrush
        } else {
            Brush.linearGradient(listOf(GlassCardSolid, GlassCardSolid))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .shadow(
                    elevation = 5.dp,
                    shape = bubbleShape,
                    ambientColor = PrimaryPurple.copy(alpha = 0.14f),
                    spotColor = PrimaryPurple.copy(alpha = 0.18f)
                )
                .clip(bubbleShape)
                .background(bubbleBrush)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = entry.text,
                color = if (isUser) Color.White else TextCharcoal,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

// ── Error Card ─────────────────────────────────────────────────────────────────
@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = 5.dp,
        containerColor = Color(0xFFFFF5F5)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PastelIconBadge(
                icon = Icons.Default.WifiOff,
                backgroundColor = Color(0xFFFDE8E8),
                iconTint = RedError,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error,
                color = RedError,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = RedError,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Pause Mode Card ────────────────────────────────────────────────────────────
@Composable
fun DualPauseModeCard(
    isLitePaused: Boolean,
    isHardPaused: Boolean,
    onToggleLite: () -> Unit,
    onToggleHard: () -> Unit
) {
    val isAnyPaused = isLitePaused || isHardPaused

    val isBlue = LocalMyraTokens.current.mode == MyraThemeMode.BLUE_GLASS
    val badgeBg = when {
        isHardPaused -> Color(0xFFFDE8E8)
        isLitePaused -> BadgeYellowBg
        isBlue -> Color(0xFFE0F2FE)
        else -> BadgePurpleBg
    }
    val badgeTint = when {
        isHardPaused -> RedError
        isLitePaused -> BadgeYellowTint
        isBlue -> Color(0xFF0284C7)
        else -> BadgePurpleTint
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevation = 6.dp,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PastelIconBadge(
                    icon = when {
                        isHardPaused -> Icons.Default.Warning
                        isLitePaused -> Icons.Default.Pause
                        else -> Icons.Default.Lock
                    },
                    backgroundColor = badgeBg,
                    iconTint = badgeTint,
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isHardPaused -> "Force Stopped — Open app to resume"
                            isLitePaused -> "Lite Paused — Accessibility silenced"
                            else -> "Banking & Privacy Shield"
                        },
                        color = LocalMyraTokens.current.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = when {
                            isHardPaused -> "Opening Settings → Accessibility on resume"
                            isLitePaused -> "Mic & AI off. Instant resume, no re-enable."
                            else -> "Pause overlay & mic while using banking apps"
                        },
                        color = if (isAnyPaused) badgeTint else TextMidGray,
                        fontSize = 12.sp
                    )
                }
                GlassStatusBadge(
                    text = if (isAnyPaused) "Paused" else "Protected",
                    isActive = !isAnyPaused
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two pill/card buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Lite Pause button
                val liteActive = isLitePaused
                Surface(
                    onClick = onToggleLite,
                    shape = RoundedCornerShape(18.dp),
                    color = if (liteActive) BadgeYellowBg else GlassSurfaceSubtle,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (liteActive) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (liteActive) BadgeYellowTint else BadgePurpleTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (liteActive) "Resume" else "Lite Pause",
                            color = LocalMyraTokens.current.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "GPay · PhonePe · Paytm",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Hard Pause button
                val hardActive = isHardPaused
                Surface(
                    onClick = onToggleHard,
                    shape = RoundedCornerShape(18.dp),
                    color = if (hardActive) Color(0xFFFDE8E8) else GlassSurfaceSubtle,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (hardActive) Icons.Default.PlayArrow else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hardActive) RedError else BadgePinkTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (hardActive) "Resume" else "Force Stop",
                            color = LocalMyraTokens.current.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "HDFC · ICICI · SBI",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}



// ── Blue Glass home shortcuts (Tactical Command Modules) ─────────────────────────
@Composable
private fun HomeShortcutCards(
    onOpenBrowser: () -> Unit,
    onOpenHomeAutomation: () -> Unit,
    onOpenApisHub: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenMemory: () -> Unit
) {
    val t = LocalMyraTokens.current

    data class ShortcutSpec(
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val iconGradient: Brush,
        val action: () -> Unit
    )

    val items = listOf(
        ShortcutSpec(
            title = "Myra Browser",
            subtitle = "Headless & Web",
            icon = Icons.Default.Language,
            iconGradient = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0077B6))),
            action = onOpenBrowser
        ),
        ShortcutSpec(
            title = "Smart Home",
            subtitle = "ESP32 Relays",
            icon = Icons.Default.ElectricBolt,
            iconGradient = Brush.linearGradient(listOf(Color(0xFF00F5D4), Color(0xFF058C68))),
            action = onOpenHomeAutomation
        ),
        ShortcutSpec(
            title = "APIs Directory",
            subtitle = "1,756+ Global APIs",
            icon = Icons.Default.Public,
            iconGradient = Brush.linearGradient(listOf(Color(0xFF7000FF), Color(0xFF3A86FF))),
            action = onOpenApisHub
        ),
        ShortcutSpec(
            title = "Live Weather",
            subtitle = "Radar & Forecast",
            icon = Icons.Default.Cloud,
            iconGradient = Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7))),
            action = onOpenWeather
        ),
        ShortcutSpec(
            title = "Memory Vault",
            subtitle = "Facts & Insights",
            icon = Icons.Default.Psychology,
            iconGradient = Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF6366F1))),
            action = onOpenMemory
        ),
        ShortcutSpec(
            title = "System Logs",
            subtitle = "Live Execution",
            icon = Icons.AutoMirrored.Filled.Article,
            iconGradient = Brush.linearGradient(listOf(Color(0xFFFB923C), Color(0xFFDC2626))),
            action = onOpenLogs
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Tactical header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "COMMAND MODULES",
                    color = t.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
            }
            Text(
                text = "JARVIS CORE 2.0",
                color = Color(0xFF1E6FFB),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { spec ->
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = spec.action,
                        cornerRadius = 20.dp,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        ambientColor = Color(0xFF00E5FF).copy(alpha = 0.25f),
                                        spotColor = Color(0xFF1E6FFB).copy(alpha = 0.35f)
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(spec.iconGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = spec.icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = spec.title,
                                    color = t.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = spec.subtitle,
                                    color = t.textSecondary,
                                    fontSize = 10.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
