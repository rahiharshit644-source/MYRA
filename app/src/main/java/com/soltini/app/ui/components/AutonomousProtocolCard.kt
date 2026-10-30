package com.soltini.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.protocol.AutoTaskProtocolManager
import com.soltini.app.protocol.ProtocolPreset
import com.soltini.app.protocol.ProtocolState
import com.soltini.app.protocol.ProtocolStepStatus
import com.soltini.app.ui.theme.LocalMyraTokens

@Composable
fun AutonomousProtocolCard(
    modifier: Modifier = Modifier,
    onLaunchCustom: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val manager = remember { AutoTaskProtocolManager.getInstance(context) }
    val protocolState by manager.protocolState.collectAsState()
    val t = LocalMyraTokens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        val active = protocolState
        if (active != null && (active.isRunning || active.isCompleted)) {
            // Live Protocol Running / Completed Card
            ActiveProtocolHUD(
                state = active,
                onCancel = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    manager.cancelActiveProtocol()
                },
                onDismiss = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    manager.clearFinishedProtocol()
                }
            )
        } else {
            // Idle: Protocol Presets Launcher
            ProtocolPresetsLauncher(
                onSelectPreset = { preset ->
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    manager.launchProtocol(
                        name = preset.name,
                        goal = preset.goal,
                        explicitSteps = preset.defaultSteps
                    )
                }
            )
        }
    }
}

@Composable
private fun ActiveProtocolHUD(
    state: ProtocolState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalMyraTokens.current
    val inf = rememberInfiniteTransition(label = "hudPulse")
    val pulseAlpha by inf.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = t.cardColor,
        border = BorderStroke(1.2.dp, if (state.isRunning) Color(0xFF00E5FF).copy(alpha = 0.6f) else Color(0xFF00F5D4).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Telemetry Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isRunning) Color(0xFF00E5FF).copy(alpha = pulseAlpha)
                                else Color(0xFF00F5D4)
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.isRunning) "PROTOCOL RUNNING" else "PROTOCOL COMPLETED",
                        color = if (state.isRunning) Color(0xFF0284C7) else Color(0xFF059669),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1800E5FF),
                    border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                    onClick = if (state.isRunning) onCancel else onDismiss
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (state.isRunning) Icons.Default.Close else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (state.isRunning) Color(0xFFE11D48) else Color(0xFF059669),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (state.isRunning) "Abort" else "Dismiss",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.isRunning) Color(0xFFE11D48) else Color(0xFF059669)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Protocol Title & Goal
            Text(
                text = state.name,
                color = t.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = state.goal,
                color = t.textSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )

            Spacer(Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { state.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF00E5FF),
                trackColor = Color(0x3300E5FF)
            )

            Spacer(Modifier.height(10.dp))

            // Steps Telemetry
            state.steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (step.status) {
                        ProtocolStepStatus.COMPLETED -> Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(16.dp)
                        )
                        ProtocolStepStatus.RUNNING -> Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = pulseAlpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                        else -> Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "Step ${step.index + 1}: ${step.title}",
                        color = when (step.status) {
                            ProtocolStepStatus.RUNNING -> Color(0xFF0284C7)
                            ProtocolStepStatus.COMPLETED -> t.textPrimary
                            else -> t.textSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = if (step.status == ProtocolStepStatus.RUNNING) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }

            if (state.isCompleted && !state.outcomeMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1800F5D4),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Outcome: ${state.outcomeMessage}",
                        color = Color(0xFF059669),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtocolPresetsLauncher(
    onSelectPreset: (ProtocolPreset) -> Unit
) {
    val t = LocalMyraTokens.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
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
                    text = "AUTONOMOUS PROTOCOLS",
                    color = t.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sentryEngine = remember { com.soltini.app.security.JarvisSentryEngine.getInstance(context) }
                val sentryStatus by sentryEngine.sentryStatus.collectAsState()

                // Sentry Defense Quick Toggle
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (sentryStatus.isArmed) Color(0x28EF4444) else Color(0x1800E5FF),
                    border = BorderStroke(
                        0.8.dp,
                        if (sentryStatus.isArmed) Color(0xFFEF4444) else Color(0xFF00E5FF).copy(alpha = 0.35f)
                    ),
                    onClick = {
                        sentryEngine.toggleSentry()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Sentry Mode",
                            tint = if (sentryStatus.isArmed) Color(0xFFEF4444) else Color(0xFF00E5FF),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (sentryStatus.isArmed) "SENTRY ARMED" else "SENTRY OFF",
                            color = if (sentryStatus.isArmed) Color(0xFFEF4444) else Color(0xFF0284C7),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                var soundOn by remember { mutableStateOf(com.soltini.app.audio.JarvisSoundEngine.isEnabled) }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x1800E5FF),
                    border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                    onClick = {
                        soundOn = !soundOn
                        com.soltini.app.audio.JarvisSoundEngine.isEnabled = soundOn
                        if (soundOn) com.soltini.app.audio.JarvisSoundEngine.playTacticalBlip()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (soundOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Sound cues",
                            tint = if (soundOn) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (soundOn) "SFX ON" else "SFX OFF",
                            color = if (soundOn) Color(0xFF0284C7) else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                // Acoustic Noise Shield (RNNoise/DeepFilterNet DSP speech enhancer)
                val noiseTelemetry by com.soltini.app.audio.JarvisDeepNoiseSuppressor.telemetry.collectAsState()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (noiseTelemetry.mode) {
                        com.soltini.app.audio.NoiseSuppressionMode.EXTREME_NOISE -> Color(0x2800F5D4)
                        com.soltini.app.audio.NoiseSuppressionMode.BALANCED -> Color(0x1800E5FF)
                        com.soltini.app.audio.NoiseSuppressionMode.OFF -> Color(0x1094A3B8)
                    },
                    border = BorderStroke(
                        0.8.dp,
                        when (noiseTelemetry.mode) {
                            com.soltini.app.audio.NoiseSuppressionMode.EXTREME_NOISE -> Color(0xFF00F5D4)
                            com.soltini.app.audio.NoiseSuppressionMode.BALANCED -> Color(0xFF00E5FF).copy(alpha = 0.35f)
                            com.soltini.app.audio.NoiseSuppressionMode.OFF -> Color(0xFF64748B).copy(alpha = 0.3f)
                        }
                    ),
                    onClick = {
                        val nextMode = com.soltini.app.audio.JarvisDeepNoiseSuppressor.cycleMode()
                        if (nextMode != com.soltini.app.audio.NoiseSuppressionMode.OFF) {
                            com.soltini.app.audio.JarvisSoundEngine.playTacticalBlip()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Noise Shield",
                            tint = when (noiseTelemetry.mode) {
                                com.soltini.app.audio.NoiseSuppressionMode.EXTREME_NOISE -> Color(0xFF00F5D4)
                                com.soltini.app.audio.NoiseSuppressionMode.BALANCED -> Color(0xFF00E5FF)
                                com.soltini.app.audio.NoiseSuppressionMode.OFF -> Color(0xFF94A3B8)
                            },
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = when (noiseTelemetry.mode) {
                                com.soltini.app.audio.NoiseSuppressionMode.EXTREME_NOISE -> "NOISE SHIELD"
                                com.soltini.app.audio.NoiseSuppressionMode.BALANCED -> "SHIELD BAL"
                                com.soltini.app.audio.NoiseSuppressionMode.OFF -> "SHIELD OFF"
                            },
                            color = when (noiseTelemetry.mode) {
                                com.soltini.app.audio.NoiseSuppressionMode.EXTREME_NOISE -> Color(0xFF059669)
                                com.soltini.app.audio.NoiseSuppressionMode.BALANCED -> Color(0xFF0284C7)
                                com.soltini.app.audio.NoiseSuppressionMode.OFF -> Color(0xFF94A3B8)
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Voice Persona Switcher: JARVIS (British Stark) | EDITH (Tactical) | MYRA (Hinglish)
        val personaEngine = remember { com.soltini.app.audio.JarvisPersonaVoiceEngine.getInstance(context) }
        val activePersona by personaEngine.activePersona.collectAsState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.soltini.app.audio.VoicePersona.values().forEach { persona ->
                val isSelected = persona == activePersona
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0x2800E5FF) else Color(0x1000E5FF),
                    border = BorderStroke(
                        0.8.dp,
                        if (isSelected) Color(0xFF00E5FF) else Color(0xFF00E5FF).copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        personaEngine.switchPersona(persona, vocalizeGreeting = true)
                    }
                ) {
                    Text(
                        text = when (persona) {
                            com.soltini.app.audio.VoicePersona.JARVIS_BRITISH -> "🇬🇧 JARVIS"
                            com.soltini.app.audio.VoicePersona.EDITH_TACTICAL -> "🎯 EDITH"
                            com.soltini.app.audio.VoicePersona.MYRA_HINGLISH -> "🇮🇳 MYRA"
                        },
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF0284C7) else t.textSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )
                }
            }
        }

        // Tactical Autonomy Features Row: Voice Secretary | Graph-RAG | Multi-Wake Word
        val secretaryEngine = remember { com.soltini.app.secretary.MyraVoiceSecretaryEngine.getInstance(context) }
        val secretaryState by secretaryEngine.secretaryState.collectAsState()
        val graphEngine = remember { com.soltini.app.graph.MyraKnowledgeGraphEngine.getInstance(context) }
        val graphNodeCount by graphEngine.nodeCount.collectAsState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. WhatsApp & Call Voice Announcer (Strict Opt-in with Gemini Voice)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (secretaryState.isAnnouncerEnabled) Color(0x2800F5D4) else Color(0x1000E5FF),
                border = BorderStroke(0.8.dp, if (secretaryState.isAnnouncerEnabled) Color(0xFF00F5D4) else Color(0xFF00E5FF).copy(alpha = 0.25f)),
                modifier = Modifier.weight(1f),
                onClick = {
                    secretaryEngine.toggleVoiceAnnouncer()
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (secretaryState.isAnnouncerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Voice Announcer",
                        tint = if (secretaryState.isAnnouncerEnabled) Color(0xFF00F5D4) else Color(0xFF94A3B8),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (secretaryState.isAnnouncerEnabled) "NOTIF: GEMINI" else "NOTIF: SILENT",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (secretaryState.isAnnouncerEnabled) Color(0xFF059669) else Color(0xFF94A3B8)
                    )
                }
            }

            // 2. Personal Knowledge Graph (Graph-RAG Matrix)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0x1800E5FF),
                border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                modifier = Modifier.weight(1f),
                onClick = {
                    com.soltini.app.audio.JarvisSoundEngine.playTacticalBlip()
                    com.soltini.app.agent.AgentStatusBus.emit("🧠 Graph-RAG Active: $graphNodeCount Personal Entities Linked")
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Graph-RAG",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "GRAPH: $graphNodeCount NODES",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                }
            }

            // 3. Multi-Wake Word (Myra, Jarvis, Edith, Friday)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0x1800E5FF),
                border = BorderStroke(0.8.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                modifier = Modifier.weight(1f),
                onClick = {
                    com.soltini.app.audio.JarvisSoundEngine.playArcReactorHum()
                    com.soltini.app.agent.AgentStatusBus.emit("⚡ Wake Words: 'Hey Myra' | 'Jarvis' | 'Edith' Active")
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Multi-Wake",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "WAKE: JARVIS/MYRA",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                }
            }
        }

        // 🔮 Proactive Mind Engine Status Card
        val mindEngine = remember { com.soltini.app.cognitive.MyraProactiveMindEngine.getInstance(context) }
        val lastMindInsight by mindEngine.lastInsight.collectAsState()

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0x18A855F7),
            border = BorderStroke(0.8.dp, Color(0xFFA855F7).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            onClick = {
                com.soltini.app.audio.JarvisSoundEngine.playTacticalBlip()
                com.soltini.app.agent.AgentStatusBus.emit(lastMindInsight ?: "🔮 Proactive Cognitive Engine: Monitoring Intent & Context")
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Proactive Mind",
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (lastMindInsight != null) "MIND: ${lastMindInsight!!.take(32)}..." else "🔮 PROACTIVE MIND: HIGH-AGENCY ACTIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC084FC)
                    )
                }
                Text(
                    text = "INSIGHTS",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA855F7)
                )
            }
        }

        // 🏍️ Autonomous Co-Pilot Driving Mode Banner
        val coPilotEngine = remember { com.soltini.app.copilot.DrivingCoPilotEngine.getInstance(context) }
        val coPilotState by coPilotEngine.coPilotState.collectAsState()

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (coPilotState.isActive) Color(0x30F59E0B) else Color(0x1000E5FF),
            border = BorderStroke(0.8.dp, if (coPilotState.isActive) Color(0xFFF59E0B) else Color(0xFF00E5FF).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            onClick = {
                coPilotEngine.toggleCoPilot()
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Driving Co-Pilot",
                        tint = if (coPilotState.isActive) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (coPilotState.isActive) "🏍️ CO-PILOT DRIVING MODE: ACTIVE" else "🏍️ CO-PILOT DRIVING MODE: STANDBY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (coPilotState.isActive) Color(0xFFD97706) else Color(0xFF94A3B8)
                    )
                }
                Text(
                    text = if (coPilotState.isActive) "DISENGAGE" else "ENGAGE",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (coPilotState.isActive) Color(0xFFF59E0B) else Color(0xFF00E5FF)
                )
            }
        }

        // 📍 Real-Time Spatial GPS Location Intelligence Banner
        val locationEngine = remember { com.soltini.app.location.LocationIntelligenceEngine.getInstance(context) }
        val locSnapshot by locationEngine.currentLocation.collectAsState()

        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (locSnapshot.isAvailable) Color(0x2010B981) else Color(0x1064748B),
            border = BorderStroke(0.8.dp, if (locSnapshot.isAvailable) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF64748B).copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            onClick = {
                locationEngine.startLocationTracking()
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (locSnapshot.isAvailable) Color(0xFF10B981) else Color(0xFF94A3B8))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (locSnapshot.isAvailable) "📍 GPS LIVE: ${locSnapshot.getReadableSummary()}" else "📍 GPS: ACQUIRING REAL-TIME FIX",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (locSnapshot.isAvailable) Color(0xFF10B981) else Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (locSnapshot.isAvailable) "${"%.1f".format(locSnapshot.speedKmh)} km/h" else "STANDBY",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (locSnapshot.isAvailable) Color(0xFF10B981) else Color(0xFF64748B)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(AutoTaskProtocolManager.PRESETS) { preset ->
                ProtocolPresetChip(
                    preset = preset,
                    onClick = { onSelectPreset(preset) }
                )
            }
        }
    }
}

@Composable
private fun ProtocolPresetChip(
    preset: ProtocolPreset,
    onClick: () -> Unit
) {
    val t = LocalMyraTokens.current
    val icon: ImageVector = when (preset.iconName) {
        "wb_sunny" -> Icons.Default.WbSunny
        "headphones" -> Icons.Default.Headphones
        "shield", "shield_lock" -> Icons.Default.Security
        "radar" -> Icons.Default.GraphicEq
        "shopping_cart" -> Icons.Default.ShoppingCart
        "visibility" -> Icons.Default.Visibility
        "home" -> Icons.Default.Home
        else -> Icons.Default.Bolt
    }

    val gradient = when (preset.id) {
        "morning_sync" -> Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0284C7)))
        "deep_focus" -> Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF6366F1)))
        "privacy_shield" -> Brush.linearGradient(listOf(Color(0xFF00F5D4), Color(0xFF0D9488)))
        "executive_briefing" -> Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF1E40AF)))
        "sentry_lockdown" -> Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFF7F1D1D)))
        "market_scout" -> Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF047857)))
        "vision_scan" -> Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95)))
        "home_radar" -> Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFC2410C)))
        else -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEA580C)))
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = t.cardColor,
        border = t.cardBorder,
        modifier = Modifier.width(170.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x1800E5FF)
                ) {
                    Text(
                        text = preset.tag,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = preset.name.removePrefix("Protocol: "),
                color = t.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = preset.description,
                color = t.textSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 2
            )
        }
    }
}
