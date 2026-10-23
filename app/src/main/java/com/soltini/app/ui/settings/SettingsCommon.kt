package com.soltini.app.ui.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.soltini.app.agent.SoltiniAccessibilityService
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassListItemCard
import com.soltini.app.ui.theme.GlassShadowAmbient
import com.soltini.app.ui.theme.GlassShadowSpot
import com.soltini.app.ui.theme.LocalMyraTokens
import com.soltini.app.ui.theme.PastelPurpleBadge
import com.soltini.app.ui.theme.PurpleAccentStart
import com.soltini.app.ui.theme.TextCharcoal

// ── Shared Colors ───────────────────────────────────────────────────────────
val AmberWarning = Color(0xFFD97706)
val MiuiOrange = Color(0xFFFF6900)
val RedError = Color(0xFFE53935)
val MintAccentDark = Color(0xFF0D9488)

// ── Permission Tiers ─────────────────────────────────────────────────────────
enum class PermTier { REQUIRED, AGENT, RECOMMENDED }

// ── Shared Quad Data Structure ──────────────────────────────────────────────
data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// ── Section Label ─────────────────────────────────────────────────────────────
@Composable
fun SectionLabel(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LocalMyraTokens.current.accentGradient)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text,
            color = LocalMyraTokens.current.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

// ── Glass Selectable Pill ───────────────────────────────────────────────────
@Composable
fun GlassSelectablePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .shadow(
                elevation = if (selected) 4.dp else 1.dp,
                shape = CircleShape,
                ambientColor = GlassShadowAmbient,
                spotColor = GlassShadowSpot
            )
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(LocalMyraTokens.current.accentGradient)
                else Modifier.background(Color(0xFFF6F3FC))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextCharcoal,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ── Reusable Glass Form Inputs ───────────────────────────────────────────────
@Composable
fun GlassOutlinedInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 13.sp, color = LocalMyraTokens.current.textSecondary) },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PurpleAccentStart,
            unfocusedBorderColor = Color(0xFFE4DCF5),
            focusedContainerColor = Color.White.copy(alpha = 0.8f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.5f),
            focusedTextColor = LocalMyraTokens.current.textPrimary,
            unfocusedTextColor = LocalMyraTokens.current.textPrimary
        ),
        modifier = modifier
    )
}

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = PurpleAccentStart,
            activeTrackColor = PurpleAccentStart,
            inactiveTrackColor = Color(0xFFE4DCF5)
        ),
        modifier = modifier
    )
}

// ── Settings Header ──────────────────────────────────────────────────────────
@Composable
fun SettingsHeader() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "scale"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        contentPadding = 22.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(scale)
                    .shadow(8.dp, CircleShape, ambientColor = GlassShadowAmbient, spotColor = GlassShadowSpot)
                    .clip(CircleShape)
                    .background(LocalMyraTokens.current.accentGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Myra Control Center",
                style = MaterialTheme.typography.headlineSmall,
                color = LocalMyraTokens.current.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Configure AI persona, voice biometrics, memory tiers,\nsmart home relays, and autonomous capabilities.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalMyraTokens.current.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// ── Settings Menu Card ───────────────────────────────────────────────────────
@Composable
fun SettingsMenuCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    badgeColor: Color = PastelPurpleBadge,
    iconTint: Color = PurpleAccentStart,
    onClick: () -> Unit
) {
    GlassListItemCard(
        title = title,
        subtitle = subtitle,
        icon = icon,
        badgeColor = badgeColor,
        iconTint = iconTint,
        showChevron = true,
        onClick = onClick
    )
}

// ── Centralized Permission Check Helpers ─────────────────────────────────────
object SettingsPermChecks {

    fun checkPhoneCallControl(context: Context): Boolean {
        val phoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val answerCalls = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
        return phoneState && contacts && answerCalls
    }

    fun checkNotificationAccess(context: Context): Boolean {
        return androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun checkMic(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun checkOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun checkBattery(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    fun checkAccessibility(context: Context): Boolean {
        if (SoltiniAccessibilityService.isActive()) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(context.packageName, ignoreCase = true)
    }

    fun checkAssistantRole(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        } else {
            val setting = Settings.Secure.getString(
                context.contentResolver,
                "assistant"
            )
            setting?.contains(context.packageName, ignoreCase = true) == true
        }
    }

    fun checkRestrictedSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkAccessibility(context)
        } else {
            true
        }
    }

    fun checkRequiresAutoStart(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = Intent()
        when (manufacturer) {
            "xiaomi" -> intent.component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            "oppo" -> intent.component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            "vivo" -> intent.component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            "huawei", "honor" -> intent.component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
            else -> return false
        }
        return context.packageManager.resolveActivity(intent, 0) != null
    }

    fun launchAutoStart(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = Intent()
        when (manufacturer) {
            "xiaomi" -> intent.component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            "oppo" -> intent.component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            "vivo" -> intent.component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            "huawei", "honor" -> intent.component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        }
        if (intent.component != null) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
