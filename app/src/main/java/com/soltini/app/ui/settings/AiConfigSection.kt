package com.soltini.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassGradientButton
import com.soltini.app.ui.components.GlassSwitch
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.LocalMyraTokens
import com.soltini.app.ui.theme.MintAccentDark
import com.soltini.app.ui.theme.PastelMintBadge
import com.soltini.app.ui.theme.PastelPurpleBadge
import com.soltini.app.ui.theme.PurpleAccentStart
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiConfigSection(
    appSettings: AppSettings?,
    onSaveAndReconnect: () -> Unit
) {
    val devName = appSettings?.developerName ?: AppSettings.DEFAULT_DEVELOPER_NAME
    var username by remember { mutableStateOf(appSettings?.ownerUsername ?: "") }
    var appName by remember { mutableStateOf(appSettings?.appDisplayName ?: AppSettings.DEFAULT_APP_NAME) }
    var apiKey by remember { mutableStateOf(appSettings?.geminiApiKey ?: "") }
    var browserUseServerUrl by remember { mutableStateOf(appSettings?.browserUseServerUrl ?: "") }
    var persona by remember { mutableStateOf(appSettings?.aiPersona ?: AppSettings.DEFAULT_PERSONA) }
    var voice by remember { mutableStateOf(appSettings?.voiceName ?: AppSettings.DEFAULT_VOICE) }
    var isDataSaver by remember { mutableStateOf(appSettings?.isDataSaverEnabled ?: true) }
    var vadSens by remember { mutableStateOf(appSettings?.vadSensitivity ?: AppSettings.SENSITIVITY_BALANCED) }
    var micGain by remember { mutableFloatStateOf(appSettings?.micGain ?: 1.5f) }
    var autoSleep by remember { mutableIntStateOf(appSettings?.autoSleepMinutes ?: 3) }
    var isWakeWord by remember { mutableStateOf(appSettings?.isWakeWordEnabled ?: true) }
    var isSilentExec by remember { mutableStateOf(appSettings?.isSilentExecutionMode ?: false) }
    var isRiskConfirm by remember { mutableStateOf(appSettings?.isAgentRiskConfirmEnabled ?: true) }
    var isOtpReader by remember { mutableStateOf(appSettings?.isOtpReaderEnabled ?: false) }
    var isHabitLearning by remember { mutableStateOf(appSettings?.isHabitLearningEnabled ?: true) }
    var isScreenRecMode by remember { mutableStateOf(appSettings?.isScreenRecordingModeEnabled ?: false) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    SectionLabel("AI Configuration & Persona")
    Spacer(Modifier.height(12.dp))

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 26.dp,
        contentPadding = 20.dp
    ) {
        // ── Developer & Creator Identity (permanent — not editable) ──────
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Person,
                badgeColor = PastelPurpleBadge,
                iconTint = PurpleAccentStart,
                size = 36.dp,
                iconSize = 18.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Developer / Creator Name", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Kept strictly confidential. When asked \"Kisne banaya?\", \"Developer kaun hai?\" or \"Who made this app?\", the AI will reveal this name. Permanent — cannot be changed from this screen.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF0EDF8),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    devName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PurpleAccentStart,
                    modifier = Modifier.weight(1f)
                )
                Text("Permanent", fontSize = 10.sp, color = LocalMyraTokens.current.textSecondary)
            }
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Owner / Boss Username ─────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Person,
                badgeColor = PastelMintBadge,
                iconTint = MintAccentDark,
                size = 36.dp,
                iconSize = 18.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Your Name / Calling Name", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "What should Myra call you? If left empty, she defaults to \"Boss\".",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        GlassOutlinedInput(
            value = username,
            onValueChange = { username = it },
            placeholder = "Boss (default)",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── App Display Name ──────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.RecordVoiceOver,
                badgeColor = PastelPurpleBadge,
                iconTint = PurpleAccentStart,
                size = 36.dp,
                iconSize = 18.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("AI Name", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "The assistant will introduce itself with this name (default: \"Myra\").",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        GlassOutlinedInput(
            value = appName,
            onValueChange = { appName = it },
            placeholder = "Myra",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Gemini API Key ───────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Key,
                badgeColor = PastelPurpleBadge,
                iconTint = PurpleAccentStart,
                size = 36.dp,
                iconSize = 18.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Gemini API Key", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Leave empty to use the compile-time key. Overriding with your own key avoids rate limits.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        GlassOutlinedInput(
            value = apiKey,
            onValueChange = { apiKey = it },
            placeholder = "AIzaSy… (leave blank to use default)",
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Icon(
                    imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showKey) "Hide" else "Show",
                    tint = LocalMyraTokens.current.textSecondary,
                    modifier = Modifier.clickable { showKey = !showKey }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Browser-Use Bridge Server URL ──────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Web,
                badgeColor = PastelPurpleBadge,
                iconTint = PurpleAccentStart,
                size = 36.dp,
                iconSize = 18.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Browser-Use Bridge URL", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "IP/URL of your PC running soltini_bridge_server.py (e.g. http://192.168.1.23:8787). Leave blank if not using remote browser automation.",
            color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp, lineHeight = 16.sp
        )
        Spacer(Modifier.height(8.dp))
        GlassOutlinedInput(
            value = browserUseServerUrl,
            onValueChange = { browserUseServerUrl = it },
            placeholder = "http://192.168.x.x:8787 (optional)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Voice Model ─────────────────────────────────────────────────
        Text("Voice", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text("Select Gemini Live speaker voice tone:", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppSettings.AVAILABLE_VOICES.forEach { opt ->
                GlassSelectablePill(
                    label = opt,
                    selected = voice == opt,
                    onClick = { voice = opt }
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Microphone Hardware Gain ─────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.Mic,
                    badgeColor = PastelPurpleBadge,
                    iconTint = PurpleAccentStart,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Microphone Gain", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Digital software amplifier for quiet mics", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
                }
            }
            Text(
                "${String.format("%.1f", micGain)}x",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PurpleAccentStart
            )
        }
        Spacer(Modifier.height(8.dp))
        GlassSlider(
            value = micGain,
            onValueChange = { micGain = (it * 10).roundToInt() / 10f },
            valueRange = 1.0f..3.0f,
            steps = 19
        )

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Standby & Wake Word ("Myra") ────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                PastelIconBadge(
                    icon = Icons.Default.RecordVoiceOver,
                    badgeColor = PastelPurpleBadge,
                    iconTint = PurpleAccentStart,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text("Wake Word (\"Myra\")", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Saying \"Myra\" or \"Hey Myra\" wakes her from standby. Keeps offline mic ownership.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            GlassSwitch(
                checked = isWakeWord,
                onCheckedChange = { isWakeWord = it }
            )
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Silent Execution Mode ───────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                PastelIconBadge(
                    icon = Icons.Default.VolumeUp,
                    badgeColor = PastelPurpleBadge,
                    iconTint = PurpleAccentStart,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text("Silent Execution Mode", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Myra performs tasks silently without spoken narration; normal conversations remain audible.",
                        color = LocalMyraTokens.current.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            GlassSwitch(
                checked = isSilentExec,
                onCheckedChange = { isSilentExec = it }
            )
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Auto-Sleep Timeout ──────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.Timer,
                    badgeColor = PastelPurpleBadge,
                    iconTint = PurpleAccentStart,
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Auto-Sleep Idle Timeout", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Goes to standby after silence (0 = never)", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
                }
            }
            Text(
                if (autoSleep == 0) "Never" else "${autoSleep}m",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PurpleAccentStart
            )
        }
        Spacer(Modifier.height(8.dp))
        GlassSlider(
            value = autoSleep.toFloat(),
            onValueChange = { autoSleep = it.roundToInt() },
            valueRange = 0f..10f,
            steps = 9
        )

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Data Saver & Voice Activity Detection (VAD) ─────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Data Saver Mode", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Only streams mic audio when speech is detected. Saves up to 80% mobile data.",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp
                )
            }
            GlassSwitch(
                checked = isDataSaver,
                onCheckedChange = { isDataSaver = it }
            )
        }

        if (isDataSaver) {
            Spacer(Modifier.height(14.dp))
            Text("VAD Sensitivity", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("Adjust threshold for silence suppression:", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    AppSettings.SENSITIVITY_HIGH to "Sensitive (Quiet Room)",
                    AppSettings.SENSITIVITY_BALANCED to "Balanced (Recommended)",
                    AppSettings.SENSITIVITY_LOW to "Aggressive (Noisy)"
                ).forEach { (key, label) ->
                    GlassSelectablePill(
                        label = label,
                        selected = vadSens == key,
                        onClick = { vadSens = key }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color(0xFFEFEBF8), thickness = 1.dp)
        Spacer(Modifier.height(18.dp))

        // ── Screen Recording Mode ────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Screen Recording Mode", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Fixes echo loops when recording internal audio.",
                    color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp
                )
            }
            GlassSwitch(
                checked = isScreenRecMode,
                onCheckedChange = { isScreenRecMode = it }
            )
        }

        Spacer(Modifier.height(22.dp))

        // ── Save & Reconnect button ──────────────────────────────────────
        GlassGradientButton(
            text = if (saved) "Saved & Reconnecting…" else "Save & Reconnect",
            icon = if (saved) Icons.Default.Check else null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                appSettings?.ownerUsername = username
                appSettings?.appDisplayName = appName
                appSettings?.geminiApiKey = apiKey
                appSettings?.browserUseServerUrl = browserUseServerUrl
                val resolvedAddr = appSettings?.currentAddressName() ?: "Boss"
                appSettings?.aiPersona = AppSettings.generatePersona(devName, appName, resolvedAddr)
                appSettings?.voiceName = voice
                appSettings?.isDataSaverEnabled = isDataSaver
                appSettings?.vadSensitivity = vadSens
                appSettings?.micGain = micGain
                appSettings?.autoSleepMinutes = autoSleep
                appSettings?.isWakeWordEnabled = isWakeWord
                appSettings?.isSilentExecutionMode = isSilentExec
                appSettings?.isAgentRiskConfirmEnabled = isRiskConfirm
                appSettings?.isOtpReaderEnabled = isOtpReader
                appSettings?.isHabitLearningEnabled = isHabitLearning
                appSettings?.isScreenRecordingModeEnabled = isScreenRecMode
                username = appSettings?.ownerUsername ?: ""
                persona = appSettings?.aiPersona ?: persona
                saved = true
                onSaveAndReconnect()
            }
        )

        Text(
            "Settings saved to device. A new Gemini session will start.",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}
