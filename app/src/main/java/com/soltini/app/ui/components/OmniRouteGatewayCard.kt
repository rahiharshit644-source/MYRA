package com.soltini.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.gateway.GatewayStatus
import com.soltini.app.gateway.JarvisOmniRouteGateway
import com.soltini.app.ui.theme.LocalMyraTokens

/**
 * OmniRouteGatewayCard — Blue Glass HUD Multi-Model Gateway & Failover Telemetry
 *
 * Inspired by diegosouzapw/OmniRoute. Displays live AI model routing, response latency,
 * token compression stats, and automatic failover circuit breaker status.
 */
@Composable
fun OmniRouteGatewayCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val gateway = remember { JarvisOmniRouteGateway.getInstance(context) }
    val telemetry by gateway.telemetry.collectAsState()
    val t = LocalMyraTokens.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val statusColor = when (telemetry.status) {
        GatewayStatus.HEALTHY -> Color(0xFF00E5FF)
        GatewayStatus.DEGRADED -> Color(0xFFF59E0B)
        GatewayStatus.FAILOVER_ACTIVE -> Color(0xFFEF4444)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = t.cardColor,
        border = t.cardBorder,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Title & Smart Routing Toggle
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
                            .background(statusColor.copy(alpha = pulseAlpha))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "OMNIROUTE AI GATEWAY",
                        color = t.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (telemetry.isDynamicRoutingEnabled) Color(0x1800E5FF) else Color(0x1894A3B8),
                    border = BorderStroke(
                        0.8.dp,
                        if (telemetry.isDynamicRoutingEnabled) Color(0xFF00E5FF).copy(alpha = 0.4f) else Color(0xFF94A3B8).copy(alpha = 0.2f)
                    ),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        gateway.toggleDynamicRouting()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Dynamic Router",
                            tint = if (telemetry.isDynamicRoutingEnabled) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (telemetry.isDynamicRoutingEnabled) "SMART ROUTING ON" else "STATIC TIER",
                            color = if (telemetry.isDynamicRoutingEnabled) Color(0xFF0284C7) else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Stats Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active Model Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1200E5FF),
                    border = BorderStroke(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = "ACTIVE MODEL",
                            color = t.textSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = telemetry.activeModel.removePrefix("gemini-").uppercase(),
                            color = t.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Latency Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1200E5FF),
                    border = BorderStroke(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.25f)),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = "LATENCY",
                            color = t.textSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (telemetry.lastLatencyMs > 0) "${telemetry.lastLatencyMs}ms" else "<50ms",
                            color = Color(0xFF0284C7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Tokens Saved Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1200E5FF),
                    border = BorderStroke(0.6.dp, Color(0xFF00E5FF).copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1.0f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = "TOKENS SAVED",
                            color = t.textSecondary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${telemetry.tokensSavedEstimate}",
                            color = Color(0xFF059669),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
