package com.soltini.app.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.components.GlassCard
import com.soltini.app.ui.components.GlassStatusBadge
import com.soltini.app.ui.components.GradientPillButton
import com.soltini.app.ui.components.PastelIconBadge
import com.soltini.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val PREFS = "app_prefs"
private const val KEY_SETUP_DONE = "setup_wizard_done"
private const val AI_STUDIO_KEY_URL = "https://aistudio.google.com/apikey"
private val PLACEHOLDER_KEYS = setOf("MY_GEMINI_API_KEY", "your_api_key_here", "REPLACE_ME")

/** True when a usable Gemini key exists (user's own key, or one baked in at build time). */
fun hasUsableApiKey(context: Context): Boolean {
    val key = try { AppSettings(context).effectiveApiKey().trim() } catch (_: Exception) { "" }
    return key.isNotBlank() && key !in PLACEHOLDER_KEYS
}

/**
 * First-run gate: shown while the microphone is missing, or while no API key is usable
 * and the user has not finished/skipped the wizard yet.
 */
fun isSetupNeeded(context: Context): Boolean {
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasMic) return true
    val done = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SETUP_DONE, false)
    return !done && !hasUsableApiKey(context)
}

private fun markSetupDone(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SETUP_DONE, true).apply()
}

private enum class KeyCheck { IDLE, TESTING, OK, INVALID, NETWORK }

private suspend fun testGeminiKey(key: String): KeyCheck = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1&key=$key")
            .build()
        client.newCall(req).execute().use { r ->
            when {
                r.isSuccessful -> KeyCheck.OK
                r.code in listOf(400, 401, 403) -> KeyCheck.INVALID
                else -> KeyCheck.NETWORK
            }
        }
    } catch (_: Exception) {
        KeyCheck.NETWORK
    }
}

/** Runtime permissions that make calls / notifications work (all optional, can be skipped). */
private fun callPermissions(): Array<String> = buildList {
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.ANSWER_PHONE_CALLS)
    add(Manifest.permission.CALL_PHONE)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

/**
 * Guided first-run setup:
 *  1. Microphone (required)
 *  2. Calls & notifications (optional)
 *  3. Gemini API key (paste, get a free key, test it)
 *
 * [onMicGranted] lets the host tell the voice service the mic is ready immediately.
 * [onFinished] receives `true` when the user saved a new API key (host should reconnect).
 */
@Composable
fun SetupWizardScreen(
    onMicGranted: () -> Unit,
    onFinished: (apiKeyChanged: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = LocalMyraTokens.current

    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var step by remember { mutableIntStateOf(if (hasMic) 1 else 0) }
    var micDenied by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        micDenied = !granted
        if (granted) {
            onMicGranted()
            step = 1
        }
    }

    val callsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        step = 2
    }

    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var keyCheck by remember { mutableStateOf(KeyCheck.IDLE) }
    val keyLooksValid = apiKey.trim().length >= 30
    val alreadyHasKey = remember { hasUsableApiKey(context) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SoftBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Step ${step + 1} of 3",
                color = tokens.textSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))

            when (step) {
                0 -> {
                    StepHeader(
                        icon = Icons.Default.Mic,
                        title = "Myra ko sunne do",
                        body = "Live voice ke liye microphone zaroori hai. Audio sirf tab use hota hai jab Myra active ho."
                    )
                    Spacer(Modifier.height(24.dp))
                    GradientPillButton(
                        text = "Allow Microphone",
                        icon = Icons.Default.Mic,
                        onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 56.dp
                    )
                    if (micDenied) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Permission deny ho gayi. Agar popup dobara nahi aata to App Settings > Permissions > Microphone me allow karo.",
                            color = tokens.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", context.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("Open App Settings") }
                    }
                }

                1 -> {
                    StepHeader(
                        icon = Icons.Default.Call,
                        title = "Calls & Notifications",
                        body = "Incoming call announce karne, accept/reject karne aur contacts ka naam bolne ke liye. Chaho to skip kar do, baad me Settings se de sakte ho."
                    )
                    Spacer(Modifier.height(20.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevation = 6.dp) {
                        PermRow(Icons.Default.Call, "Phone & Call log", "Call aane par pehchanne ke liye")
                        Spacer(Modifier.height(10.dp))
                        PermRow(Icons.Default.Notifications, "Contacts & Notifications", "Caller ka naam aur alerts")
                    }
                    Spacer(Modifier.height(24.dp))
                    GradientPillButton(
                        text = "Allow",
                        onClick = { callsLauncher.launch(callPermissions()) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 56.dp
                    )
                    TextButton(onClick = { step = 2 }) { Text("Skip for now") }
                }

                else -> {
                    StepHeader(
                        icon = Icons.Default.Lock,
                        title = "Gemini API Key",
                        body = "Myra Gemini Live se bolti hai. Google AI Studio se free key banao aur yahan paste karo."
                    )
                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it.trim(); keyCheck = KeyCheck.IDLE },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        label = { Text("API key (AIza...)") },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val pasted = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
                                if (pasted.isNotBlank()) { apiKey = pasted; keyCheck = KeyCheck.IDLE }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Paste")
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_KEY_URL))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Get free key") }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        enabled = keyLooksValid && keyCheck != KeyCheck.TESTING,
                        onClick = {
                            keyCheck = KeyCheck.TESTING
                            scope.launch { keyCheck = testGeminiKey(apiKey.trim()) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (keyCheck == KeyCheck.TESTING) "Testing..." else "Test key") }

                    when (keyCheck) {
                        KeyCheck.OK -> StatusLine("Key sahi hai", true)
                        KeyCheck.INVALID -> StatusLine("Key galat ya disabled hai. Dobara check karo.", false)
                        KeyCheck.NETWORK -> StatusLine("Internet / server issue. Key save kar sakte ho, baad me retry hoga.", false)
                        else -> {}
                    }

                    Spacer(Modifier.height(18.dp))
                    GradientPillButton(
                        text = "Save & Start Myra",
                        icon = Icons.Default.Check,
                        enabled = keyLooksValid,
                        onClick = {
                            AppSettings(context).geminiApiKey = apiKey.trim()
                            markSetupDone(context)
                            onFinished(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        height = 56.dp
                    )
                    TextButton(onClick = {
                        markSetupDone(context)
                        onFinished(false)
                    }) {
                        Text(if (alreadyHasKey) "Use built-in key" else "Skip (Settings se baad me add karo)")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    val tokens = LocalMyraTokens.current
    PastelIconBadge(
        icon = icon,
        backgroundColor = BadgePurpleBg,
        iconTint = BadgePurpleTint,
        size = 72.dp,
        iconSize = 34.dp
    )
    Spacer(Modifier.height(18.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = tokens.textPrimary,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = tokens.textSecondary,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp
    )
}

@Composable
private fun PermRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    val tokens = LocalMyraTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        PastelIconBadge(icon = icon, backgroundColor = BadgePurpleBg, iconTint = BadgePurpleTint, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = tokens.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassStatusBadge(text = if (ok) "OK" else "Check", isActive = ok)
        Spacer(Modifier.width(8.dp))
        Text(text, color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
    }
}
