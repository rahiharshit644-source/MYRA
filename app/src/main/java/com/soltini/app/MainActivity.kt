package com.soltini.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.soltini.app.homeautomation.ui.AddDeviceScreen
import com.soltini.app.homeautomation.ui.HomeAutomationScreen
import com.soltini.app.homeautomation.ui.HomeAutomationViewModel
import com.soltini.app.overlay.OverlayService
import com.soltini.app.ui.LogScreen
import com.soltini.app.ui.MainViewModel
import com.soltini.app.ui.MiuiOnboardingScreen
import com.soltini.app.ui.SetupWizardScreen
import com.soltini.app.ui.isSetupNeeded
import com.soltini.app.ui.PublicApisHubScreen
import com.soltini.app.ui.SettingsScreen
import com.soltini.app.ui.VoiceScreen
import com.soltini.app.ui.isMiuiOnboardingNeeded
import com.soltini.app.ui.theme.*

/**
 * MainActivity
 *
 * Routing logic:
 *   1. "permissions" — First launch wizard: microphone, call permissions, Gemini API key.
 *   2. "voice"       — Main screen with floating iridescent glass orb.
 *   3. "apis_hub"    — 1,750+ Public APIs Hub & Live Playground.
 *   4. "settings"    — Full permissions dashboard (overlay, accessibility, assistant role…).
 *   5. "logs"        — Debug log viewer.
 */
class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        com.soltini.app.browser.BrowserController.getInstance(applicationContext).setAppInBackground(false)
    }

    override fun onStop() {
        super.onStop()
        com.soltini.app.browser.BrowserController.getInstance(applicationContext).setAppInBackground(true)
        if (com.soltini.app.media.MyraPlaybackContext.isPlaying()) {
            com.soltini.app.browser.BrowserController.getInstance(applicationContext).keepWebViewAlive()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!com.soltini.app.security.AppIdentityVerifier.isAllowed(this)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("App integrity check failed")
                .setMessage("This copy of Myra is not signed with the official release certificate, so it may have been modified. Sensitive features are blocked. Please install the official build.")
                .setCancelable(false)
                .setPositiveButton("Close") { _, _ -> finish() }
                .show()
            return
        }
        com.soltini.app.util.CrashReporter.install(this)
        val pendingCrashReport: String? = com.soltini.app.util.CrashReporter.consumeLastCrash(this)
        enableEdgeToEdge()
        ThemeController.init(this)
        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        val homeViewModel = ViewModelProvider(this)[HomeAutomationViewModel::class.java]

        setContent {
            GeminiVoiceTheme {
                var crashReport by remember { mutableStateOf(pendingCrashReport) }
                if (crashReport != null) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    AlertDialog(
                        onDismissRequest = { crashReport = null },
                        containerColor = GlassCardSolid,
                        shape = RoundedCornerShape(24.dp),
                        title = {
                            Text(
                                "App crashed last time",
                                color = LocalMyraTokens.current.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Surface(
                                color = CodeBlockDarkBg,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 340.dp)
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = crashReport ?: "",
                                            color = CodeBlockTextLight,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp,
                                            fontFamily = JetBrainsMonoFamily
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                try {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Myra crash report", crashReport ?: ""))
                                    android.widget.Toast.makeText(ctx, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {}
                            }) {
                                Text("Copy", color = BadgePurpleTint, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { crashReport = null }) {
                                Text("Close", color = LocalMyraTokens.current.textSecondary)
                            }
                        }
                    )
                }

                val hasMic = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                var currentScreen by remember {
                    mutableStateOf(
                        when {
                            !hasMic || isSetupNeeded(this@MainActivity) -> "permissions"
                            isMiuiOnboardingNeeded(this@MainActivity) -> "miui_onboarding"
                            else -> "voice"
                        }
                    )
                }

                LaunchedEffect(currentScreen) {
                    if (currentScreen in listOf("voice", "chat", "settings", "logs", "home_automation", "apis_hub")) {
                        if (Settings.canDrawOverlays(this@MainActivity)) {
                            OverlayService.start(this@MainActivity)
                        }
                    }
                }

                val showBottomNav = currentScreen in listOf(
                    "voice", "chat", "browser", "home_automation", "apis_hub", "logs", "settings", "add_device", "weather", "memory"
                )

                val screenOrder = remember {
                    mapOf(
                        "voice" to 0,
                        "browser" to 1,
                        "chat" to 1,
                        "home_automation" to 2,
                        "add_device" to 2,
                        "apis_hub" to 3,
                        "logs" to 4,
                        "settings" to 5,
                        "voiceprint_enrollment" to 6
                    )
                }

                val navConnection by viewModel.connectionState.collectAsState()
                val navMuted by viewModel.isMuted.collectAsState()
                val navHasMic by viewModel.hasMicPermission.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .myraBackground()
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        bottomBar = {
                            if (showBottomNav) {
                                FloatingGlassBottomNavBar(
                                    currentScreen = currentScreen,
                                    onSelectScreen = { currentScreen = it },
                                    micMuted = navMuted,
                                    onMicClick = {
                                        // Same real actions as the Voice screen: no mic permission -> permission flow;
                                        // live -> mute/unmute; otherwise (re)connect live voice.
                                        when {
                                            !navHasMic -> currentScreen = "permissions"
                                            navConnection == com.soltini.app.network.ConnectionState.Live -> viewModel.toggleMute()
                                            else -> viewModel.connect()
                                        }
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = innerPadding.calculateBottomPadding())
                        ) {
                            AnimatedContent(
                                targetState = currentScreen,
                                transitionSpec = {
                                    val initialIdx = screenOrder[initialState] ?: 0
                                    val targetIdx = screenOrder[targetState] ?: 0
                                    val direction = if (targetIdx >= initialIdx) 1 else -1

                                    (fadeIn(animationSpec = tween(240)) +
                                        slideInHorizontally(
                                            animationSpec = tween(260),
                                            initialOffsetX = { fullWidth -> (fullWidth * 0.08f * direction).toInt() }
                                        ) +
                                        scaleIn(
                                            initialScale = 0.98f,
                                            animationSpec = tween(240)
                                        )).togetherWith(
                                        fadeOut(animationSpec = tween(190)) +
                                            slideOutHorizontally(
                                                animationSpec = tween(220),
                                                targetOffsetX = { fullWidth -> (-fullWidth * 0.06f * direction).toInt() }
                                            )
                                    ).using(SizeTransform(clip = false))
                                },
                                label = "ScreenTransition"
                            ) { targetScreen ->
                                when (targetScreen) {
                                    "permissions" -> {
                                        SetupWizardScreen(
                                            onMicGranted = { viewModel.onPermissionResult(true) },
                                            onFinished = { keyChanged ->
                                                // Start (or restart with the new key) the live session right away.
                                                if (keyChanged) viewModel.reconnect() else viewModel.connect()
                                                currentScreen = if (isMiuiOnboardingNeeded(this@MainActivity)) {
                                                    "miui_onboarding"
                                                } else {
                                                    "voice"
                                                }
                                            },
                                            modifier = Modifier
                                        )
                                    }
                                    "miui_onboarding" -> {
                                        MiuiOnboardingScreen(
                                            onComplete = { currentScreen = "voice" }
                                        )
                                    }
                                    "logs" -> {
                                        LogScreen(
                                            onBack = { currentScreen = "voice" },
                                            modifier = Modifier
                                        )
                                    }
                                    "apis_hub" -> {
                                        PublicApisHubScreen(
                                            onBack = { currentScreen = "voice" },
                                            onAskMyra = { prompt ->
                                                viewModel.sendUserMessage(prompt)
                                                currentScreen = "voice"
                                            },
                                            modifier = Modifier
                                        )
                                    }
                                    "settings" -> {
                                        SettingsScreen(
                                            onBack = { currentScreen = "voice" },
                                            viewModel = viewModel,
                                            onOpenHomeAutomation = { currentScreen = "home_automation" },
                                            onOpenVoiceEnrollment = { currentScreen = "voiceprint_enrollment" },
                                            modifier = Modifier
                                        )
                                    }
                                    "voiceprint_enrollment" -> {
                                        val voiceprintViewModel: com.soltini.app.voiceprint.ui.VoiceprintViewModel =
                                            ViewModelProvider(this@MainActivity)[com.soltini.app.voiceprint.ui.VoiceprintViewModel::class.java]
                                        com.soltini.app.voiceprint.ui.EnrollmentScreen(
                                            viewModel = voiceprintViewModel,
                                            onNavigateBack = { currentScreen = "settings" },
                                            modifier = Modifier
                                        )
                                    }
                                    "home_automation" -> {
                                        HomeAutomationScreen(
                                            viewModel = homeViewModel,
                                            onNavigateToAddDevice = { currentScreen = "add_device" },
                                            onBack = { currentScreen = "voice" },
                                            modifier = Modifier
                                        )
                                    }
                                    "add_device" -> {
                                        AddDeviceScreen(
                                            viewModel = homeViewModel,
                                            onNavigateBack = { currentScreen = "home_automation" },
                                            modifier = Modifier
                                        )
                                    }
                                    "weather" -> {
                                        com.soltini.app.ui.WeatherScreen(onBack = { currentScreen = "voice" }, modifier = Modifier)
                                    }
                                    "memory" -> {
                                        com.soltini.app.ui.MemoryScreen(onBack = { currentScreen = "voice" }, modifier = Modifier)
                                    }
                                    "chat" -> {
                                        com.soltini.app.ui.ChatScreen(
                                            viewModel = viewModel,
                                            onMicClick = {
                                                when {
                                                    !navHasMic -> currentScreen = "permissions"
                                                    navConnection == com.soltini.app.network.ConnectionState.Live -> viewModel.toggleMute()
                                                    else -> viewModel.connect()
                                                }
                                            },
                                            modifier = Modifier
                                        )
                                    }
                                    "browser" -> {
                                        com.soltini.app.browser.MyraBrowserScreen(
                                            onOpenVoiceAi = { currentScreen = "voice" },
                                            modifier = Modifier
                                        )
                                    }
                                    else -> {
                                        VoiceScreen(
                                            viewModel = viewModel,
                                            onOpenLogs = { currentScreen = "logs" },
                                            onOpenSettings = { currentScreen = "settings" },
                                            onOpenHomeAutomation = { currentScreen = "home_automation" },
                                            onOpenApisHub = { currentScreen = "apis_hub" },
                                            onOpenBrowser = { currentScreen = "browser" },
                                            onOpenWeather = { currentScreen = "weather" },
                                            onOpenMemory = { currentScreen = "memory" },
                                            modifier = Modifier
                                        )
                                    }
                                }
                            }

                            if (currentScreen != "browser") {
                                com.soltini.app.browser.BrowserOverlayPanel()
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun FloatingGlassBottomNavBar(
    currentScreen: String,
    onSelectScreen: (String) -> Unit,
    micMuted: Boolean = false,
    onMicClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val tokens = LocalMyraTokens.current
    val isBlue = tokens.mode == MyraThemeMode.BLUE_GLASS
    if (isBlue) {
        BlueGlassNavBar(currentScreen, onSelectScreen, micMuted, onMicClick)
        return
    }

    // Classic: the original six destinations, untouched.
    // Blue Glass: Home, Chat, Browser | mic | Devices, Settings. APIs Hub and Logs stay reachable from the
    // Home dashboard shortcuts (and Settings), so no feature is removed.
    val items = remember(isBlue) {
        if (isBlue) listOf(
            NavDestination("voice", "Home", Icons.Default.Home),
            NavDestination("chat", "Chat", Icons.Default.ChatBubble),
            NavDestination("browser", "Browser", Icons.Default.Language),
            NavDestination("home_automation", "Devices", Icons.Default.ElectricBolt),
            NavDestination("settings", "Settings", Icons.Default.Settings)
        ) else listOf(
            NavDestination("voice", "Voice AI", Icons.Default.Mic),
            NavDestination("browser", "Browser", Icons.Default.Language),
            NavDestination("home_automation", "Home", Icons.Default.ElectricBolt),
            NavDestination("apis_hub", "APIs Hub", Icons.Default.Public),
            NavDestination("logs", "Logs", Icons.AutoMirrored.Filled.Article),
            NavDestination("settings", "Settings", Icons.Default.Settings)
        )
    }
    val micSlot = if (isBlue) 3 else -1 // mic sits between Browser and Devices

    @Composable
    fun MicButton() {
        val micInteraction = remember { MutableInteractionSource() }
        val micPressed by micInteraction.collectIsPressedAsState()
        val micScale by animateFloatAsState(
            targetValue = if (micPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            label = "navMicScale"
        )
        Box(
            modifier = Modifier
                .size(52.dp)
                .scale(micScale)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = tokens.shadowColor.copy(alpha = 0.30f),
                    spotColor = tokens.accentSecondary.copy(alpha = 0.35f)
                )
                .clip(CircleShape)
                .background(if (micMuted) Brush.linearGradient(listOf(Color(0xFFFDE8E8), Color(0xFFFCD2D2))) else tokens.accentGradient)
                .clickable(
                    interactionSource = micInteraction,
                    indication = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMicClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (micMuted) "Unmute microphone" else "Microphone",
                tint = if (micMuted) Color(0xFFD64545) else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = tokens.cardColor,
            border = tokens.cardBorder,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = tokens.shadowColor.copy(alpha = 0.18f),
                    spotColor = tokens.shadowColor.copy(alpha = 0.24f)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    if (index == micSlot) MicButton()
                    val isSelected = if (item.route == "home_automation") {
                        currentScreen == "home_automation" || currentScreen == "add_device"
                    } else {
                        currentScreen == item.route
                    }

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.90f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "navScale"
                    )

                    val horizontalPadding by animateDpAsState(
                        targetValue = if (isSelected) 13.dp else 9.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "navPad"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else tokens.textSecondary,
                        animationSpec = tween(220),
                        label = "navTint"
                    )

                    Box(
                        modifier = Modifier
                            .scale(pressScale)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .shadow(
                                            elevation = 6.dp,
                                            shape = CircleShape,
                                            ambientColor = tokens.shadowColor.copy(alpha = 0.3f),
                                            spotColor = tokens.accentSecondary.copy(alpha = 0.35f)
                                        )
                                        .background(tokens.accentHorizontalGradient, CircleShape)
                                } else {
                                    Modifier.background(Color.Transparent, CircleShape)
                                }
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    if (!isSelected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectScreen(item.route)
                                    }
                                }
                            )
                            .padding(horizontal = horizontalPadding, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = iconTint,
                                modifier = Modifier.size(19.dp)
                            )
                            // Blue Glass has fewer items, so only the selected one carries a label (as before)
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = item.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/** Reference-style bottom bar: frosted white pill, icon over label, selected item in a white bubble, large glowing mic. */
@Composable
private fun BlueGlassNavBar(
    currentScreen: String,
    onSelectScreen: (String) -> Unit,
    micMuted: Boolean,
    onMicClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val t = LocalMyraTokens.current
    val items = listOf(
        NavDestination("voice", "Home", Icons.Default.Home),
        NavDestination("chat", "Chat", Icons.Default.ChatBubble),
        NavDestination("browser", "Browser", Icons.Default.Language),
        null, // mic slot
        NavDestination("home_automation", "Devices", Icons.Default.ElectricBolt),
        NavDestination("settings", "Settings", Icons.Default.Settings)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color(0xD9FFFFFF),
            border = t.cardBorder,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(14.dp, RoundedCornerShape(30.dp), ambientColor = t.shadowColor.copy(alpha = 0.20f), spotColor = t.shadowColor.copy(alpha = 0.25f))
        ) {}
        Row(
            modifier = Modifier.fillMaxWidth().height(66.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                if (item == null) {
                    Box(modifier = Modifier.weight(1.15f), contentAlignment = Alignment.Center) {
                        val ringBrush = Brush.linearGradient(listOf(Color(0xFF7FE0FF), Color(0xFF35B6FD)))
                        Box(
                            modifier = Modifier
                                .offset(y = (-14).dp)
                                .size(68.dp)
                                .shadow(12.dp, CircleShape, ambientColor = Color(0xFF35B6FD), spotColor = Color(0xFF2C7FFC))
                                .clip(CircleShape)
                                .background(Color(0xE6FFFFFF))
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(ringBrush)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(
                                    if (micMuted) Brush.linearGradient(listOf(Color(0xFFFDE8E8), Color(0xFFFCD2D2)))
                                    else Brush.radialGradient(listOf(Color(0xFF3FA3FF), Color(0xFF2C7FFC), Color(0xFF1F5FE6)))
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onMicClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (micMuted) "Unmute microphone" else "Microphone",
                                tint = if (micMuted) Color(0xFFD64545) else Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                } else {
                    val selected = if (item.route == "home_automation") {
                        currentScreen == "home_automation" || currentScreen == "add_device"
                    } else currentScreen == item.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectScreen(item.route)
                                }
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .then(
                                    if (selected) Modifier
                                        .shadow(4.dp, CircleShape, ambientColor = t.shadowColor.copy(alpha = 0.25f), spotColor = t.shadowColor.copy(alpha = 0.30f))
                                        .clip(CircleShape)
                                        .background(Color.White)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) t.accent else t.navUnselected,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = item.label,
                            color = if (selected) t.accent else t.navUnselected,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
