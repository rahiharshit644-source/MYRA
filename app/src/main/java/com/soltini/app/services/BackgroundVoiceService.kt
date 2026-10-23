package com.soltini.app.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.soltini.app.MainActivity
import androidx.core.app.NotificationCompat
import com.soltini.app.BootReceiver
import com.soltini.app.agent.AgentToolExecutor
import com.soltini.app.agent.BrowserUseClient
import com.soltini.app.audio.AudioPlayer
import com.soltini.app.audio.AudioRecorder
import com.soltini.app.audio.HeadsetAudioRouter
import com.soltini.app.audio.MyraSoundEffects
import com.soltini.app.audio.VoskModelManager
import com.soltini.app.audio.WakeWordEngine
import kotlinx.coroutines.Job
import com.soltini.app.network.ConnectionState
import com.soltini.app.network.GeminiLiveListener
import com.soltini.app.network.GeminiLiveManager
import com.soltini.app.network.ToolCallListener
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.TranscriptEntry
import com.soltini.app.overlay.OverlayService
import com.soltini.app.homeautomation.control.DeviceController
import com.soltini.app.homeautomation.mqtt.MqttManager
import com.soltini.app.homeautomation.data.WifiMqttCredentialsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import com.soltini.app.notifications.NotificationRepository
import com.soltini.app.notifications.ReplyResult
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BackgroundVoiceService
 *
 * Core foreground service that:
 *   1. Maintains the persistent Gemini Live WebSocket connection.
 *   2. Routes microphone audio to the WebSocket.
 *   3. Plays received audio from Gemini.
 *   4. Implements ToolCallListener to execute device actions when Gemini requests them.
 *   5. Manages sleep / wake state (disconnects Gemini on sleep, stays alive for re-trigger).
 */
class BackgroundVoiceService : Service(), GeminiLiveListener, ToolCallListener {

    companion object {
        private const val TAG = "BackgroundVoiceService"
        private const val CHANNEL_ID = "VoiceSessionChannel"
        private const val NOTIFICATION_ID = 2

        private val SILENT_EXEMPT_TOOLS = setOf(
            "observe_screen", "inspect_screen", "read_youtube_screen", "read_notifications",
            "get_call_info", "get_device_status", "get_clipboard", "recall_conversation",
            "fetch_crypto_price", "fetch_weather", "lookup_dictionary", "fetch_currency_rate",
            "lookup_ip", "search_public_apis", "list_public_api_categories",
            "get_apis_by_category", "recommend_api_for_task", "execute_public_api",
            "device_files", "read_photo", "list_apps", "uninstall_app", "contact_alias",
            "whatsapp_confirm_send", "whatsapp_cancel_draft", "whatsapp_delete_message",
            "task_status", "read_otp", "task_reply", "cancel_task",
            "remember_date", "upcoming_dates", "forget_date", "my_favorites"
        )

        @Volatile
        private var instance: BackgroundVoiceService? = null

        fun getInstance(): BackgroundVoiceService? = instance
        fun isRunning(): Boolean = instance != null
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    // SupervisorJob: one failing coroutine does NOT cancel all sibling coroutines
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── User-configurable AI settings ────────────────────────────────────────
    lateinit var appSettings: AppSettings
        private set

    // ── Persistent vector memory ─────────────────────────────────────────────
    lateinit var memory2Engine: com.soltini.app.memory2.Memory2Engine
        private set
    lateinit var pluginRegistry: com.soltini.app.plugins.PluginRegistry
        private set
    lateinit var myraOrchestrator: com.soltini.app.orchestrator.MyraOrchestrator
        private set

    /**
     * Counts how many tool calls are currently in-flight.
     * Accessibility high-friction tracking is enabled while this is > 0
     * and disabled when it drops back to 0, preventing stale tracking state.
     */
    private val activeToolCallCount = AtomicInteger(0)

    private val audioPlayer = AudioPlayer()

    private var headsetRouter: HeadsetAudioRouter? = null
    var silentTurn = false

    private lateinit var audioRecorder: AudioRecorder
    lateinit var geminiLiveManager: GeminiLiveManager
        private set

    lateinit var toolRouter: VoiceToolRouter
        private set
    lateinit var sleepStandbyManager: VoiceSleepStandbyManager
        private set
    lateinit var pauseModeManager: VoicePauseModeManager
        private set
    lateinit var callHandler: VoiceCallHandler
        private set

    // Strict voice focus: mic audio only reaches Gemini when the speaker is Boss (see VoiceGate).
    @Volatile private var voiceGate: com.soltini.app.voiceprint.gate.VoiceGate? = null
    private var lastGateSync = 0L
    private var lastGateReload = 0L
    // Requests Boss gave while a task was still running; run after it finishes (never dropped).
    val pendingAutomationRequests = java.util.concurrent.CopyOnWriteArrayList<String>()
    lateinit var agentToolExecutor: AgentToolExecutor
        private set
    val browserUseClient: BrowserUseClient by lazy { BrowserUseClient(this) }
    private lateinit var backgroundAgentRunner: com.soltini.app.agent.BackgroundAgentRunner
    private lateinit var agentPhoneController: com.soltini.app.agent.AgentPhoneController
    lateinit var deviceController: DeviceController
        private set
    private lateinit var mqttManager: MqttManager

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    val isSleeping: StateFlow<Boolean> get() = sleepStandbyManager.isSleeping
    val isLitePaused: StateFlow<Boolean> get() = pauseModeManager.isLitePaused
    val isHardPaused: StateFlow<Boolean> get() = pauseModeManager.isHardPaused
    val isPaused: StateFlow<Boolean> get() = pauseModeManager.isLitePaused

    private val _transcripts = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcripts: StateFlow<List<TranscriptEntry>> = _transcripts.asStateFlow()
    private var currentGeminiTranscript = StringBuilder()
    private var currentTurnGeminiEntryId: String? = null
    private var lastSpeechTimestamp = System.currentTimeMillis()

    // Expose flows from the child components
    lateinit var connectionState: StateFlow<ConnectionState>
    lateinit var errorMessage: StateFlow<String?>
    lateinit var micAmplitude: StateFlow<Float>
    lateinit var speakerAmplitude: StateFlow<Float>
    lateinit var isSpeaking: StateFlow<Boolean>

    // Zero-battery MIUI bypass
    private var silentMediaPlayer: MediaPlayer? = null
    @Volatile var userExplicitlyDisconnected: Boolean = false

    inner class LocalBinder : Binder() {
        fun getService(): BackgroundVoiceService = this@BackgroundVoiceService
    }

    override fun onCreate() {
        super.onCreate()
        appSettings = AppSettings(this)
        // Unfinished task list from before a crash / phone lock? Remind the user (never auto-runs phone actions).
        try {
            com.soltini.app.agent.TaskQueueStore(this).unfinishedSummary(6L * 60 * 60 * 1000)?.let {
                com.soltini.app.agent.TaskQueueNotifier.notifyUnfinished(this, it)
            }
        } catch (_: Exception) {}
        // A single task that was cut off mid-way (service killed)? Remind Boss; resume_task continues it.
        try {
            com.soltini.app.agent.TaskCheckpointStore.summary(this)?.let {
                com.soltini.app.agent.TaskQueueNotifier.notifyUnfinished(this, it)
            }
        } catch (_: Exception) {}
        instance = this
        Log.i(TAG, "BackgroundVoiceService created")

        // Start hardware-offloaded silent audio to bypass MIUI/OEM killers with 0% battery drain
        startZeroBatterySilentAudioTrick()

        // ── FIRST: Call startForeground immediately ─────────────────────────────
        createNotificationChannel()
        startForegroundService()

        // ── WakeLock with auto-renewal and watchdog loop ──────────────────────────
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Soltini::VoiceWakeLock").apply {
            setReferenceCounted(false)
            try {
                acquire(10 * 60 * 1000L)
            } catch (_: Exception) {}
        }

        // WakeLock auto-renewal + background health watchdog
        serviceScope.launch {
            while (isActive) {
                delay(4 * 60 * 1000L) // renew every 4 minutes before any 10-minute expiry
                if (!isSleeping.value && !isLitePaused.value && !isHardPaused.value) {
                    try {
                        wakeLock?.acquire(10 * 60 * 1000L)
                        Log.d(TAG, "WakeLock refreshed")
                    } catch (e: Exception) {
                        Log.w(TAG, "WakeLock renewal failed: ${e.message}")
                    }

                    // Keep silent audio loop active for zero-battery protection
                    try {
                        if (silentMediaPlayer == null || silentMediaPlayer?.isPlaying == false) {
                            Log.d(TAG, "Restarting silent media player in background")
                            startZeroBatterySilentAudioTrick()
                        }
                    } catch (_: Exception) {}

                    // Keep Gemini Live connection alive if dropped in background
                    if (connectionState.value == ConnectionState.Disconnected && _hasMicPermission.value) {
                        if (!userExplicitlyDisconnected && appSettings.isAssistantMasterEnabled && !appSettings.isBedtimeQuietHoursActive()) {
                            Log.i(TAG, "Heartbeat: Reconnecting Gemini Live session in background...")
                            connect()
                        } else {
                            Log.d(TAG, "Heartbeat: Auto-reconnect suppressed (user disconnected or bedtime quiet hours active)")
                        }
                    }
                }
            }
        }

        // ── User-configurable settings + memory ──────────────────────────────
        memory2Engine = com.soltini.app.memory2.Memory2Engine.getInstance(this, appSettings)
        pluginRegistry = com.soltini.app.plugins.PluginRegistry.getInstance(this)

        agentToolExecutor = AgentToolExecutor(this)
        com.soltini.app.plugins.BuiltinPluginInitializer.initializeAll(this, pluginRegistry, agentToolExecutor, appSettings)
        myraOrchestrator = com.soltini.app.orchestrator.MyraOrchestrator.getInstance(this, memory2Engine, pluginRegistry, appSettings)

        headsetRouter = HeadsetAudioRouter(this)
        if (appSettings.isWakeWordEnabled) {
            VoskModelManager.init(this, serviceScope)
        }

        // Background phone agent -> live voice model channel (questions, confirmations, progress)
        com.soltini.app.agent.AgentStatusBus.listener = { msg ->
            if (connectionState.value == ConnectionState.Live) geminiLiveManager.sendClientContent(msg)
        }

        // Preload persistent conversation history across app restarts
        serviceScope.launch(Dispatchers.IO) {
            try {
                val savedRecords = memory2Engine.db.getAllConversationEntries(limit = 100)
                if (savedRecords.isNotEmpty()) {
                    val entries = savedRecords.map { rec ->
                        val sender = if (rec.sender == "USER") TranscriptEntry.Sender.USER else TranscriptEntry.Sender.GEMINI
                        TranscriptEntry(
                            id = rec.id,
                            sender = sender,
                            text = rec.text,
                            timestamp = rec.timestamp
                        )
                    }
                    _transcripts.value = entries
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not preload conversation history: ${e.message}")
            }
        }

        // Background knowledge re-indexing on startup + periodic sync for authorized SAF folders
        serviceScope.launch(Dispatchers.IO) {
            val storageManager = com.soltini.app.storage.SafStorageManager(this@BackgroundVoiceService)
            val ragEngine = com.soltini.app.rag.RagKnowledgeEngine.getInstance(this@BackgroundVoiceService, appSettings)
            while (isActive) {
                try {
                    val authorized = storageManager.getAuthorizedLocations()
                    if (authorized.isNotEmpty()) {
                        Log.i(TAG, "Running background RAG knowledge re-index for ${authorized.size} authorized folder(s)...")
                        ragEngine.indexAllAuthorizedFolders(storageManager)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Background RAG folder indexing error: ${e.message}")
                }
                delay(30 * 60 * 1000L) // Periodic check every 30 minutes
            }
        }

        // Habit-based proactive routine scheduling (immediate scan + 12h recurring WorkManager job)
        try {
            com.soltini.app.automation.ProactiveRoutineScheduler.initPeriodicHabitAnalysis(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed initializing proactive habit scheduler: ${e.message}")
        }

        audioRecorder = AudioRecorder(
            onAudioChunkCaptured = { pcmChunk ->
                if (!isSleeping.value) {
                    val gate = voiceGate
                    if (gate != null) {
                        val now = System.currentTimeMillis()
                        if (now - lastGateSync > 2000) {
                            lastGateSync = now
                            gate.mode = com.soltini.app.voiceprint.gate.VoiceGateMode.fromString(appSettings.voiceGateMode)
                            gate.verifier.sensitivityOffset = appSettings.voiceprintSensitivityOffset
                            if (!gate.verifier.isReady && now - lastGateReload > 10_000) {
                                lastGateReload = now
                                serviceScope.launch { gate.verifier.reloadProfile() }
                            }
                        }
                        gate.onAudioChunkCaptured(pcmChunk)
                    } else {
                        geminiLiveManager.sendAudioChunk(pcmChunk)
                    }
                }
            },
            onSpeechStateChanged = {
                sleepStandbyManager.updateSpeechTimestamp()
            }
        ).apply {
            isDataSaverEnabled = appSettings.isDataSaverEnabled
            vadThreshold = appSettings.vadThreshold
            micGain = appSettings.micGain
            isScreenRecordingModeEnabled = appSettings.isScreenRecordingModeEnabled
        }

        // Pass 'this' as both GeminiLiveListener and ToolCallListener
        geminiLiveManager = GeminiLiveManager(
            listener = this,
            toolCallListener = this,
            appSettings = appSettings,
            memory2Engine = memory2Engine,
            pluginRegistry = pluginRegistry
        )

        // Voice-ID gate. If the speaker model / enrolled profile is missing the gate fails open (never mutes Myra).
        try {
            val vpVerifier = com.soltini.app.voiceprint.domain.VoiceprintVerifier(
                com.soltini.app.voiceprint.ml.OnnxSpeakerEmbedder(this),
                com.soltini.app.voiceprint.data.VoiceprintRepository.getInstance(this)
            )
            vpVerifier.sensitivityOffset = appSettings.voiceprintSensitivityOffset
            voiceGate = com.soltini.app.voiceprint.gate.VoiceGate(
                verifier = vpVerifier,
                forwardToConsumer = { bytes ->
                    geminiLiveManager.sendAudioChunk(bytes)
                }
            ).apply {
                mode = com.soltini.app.voiceprint.gate.VoiceGateMode.fromString(appSettings.voiceGateMode)
            }
            serviceScope.launch { vpVerifier.reloadProfile() }
        } catch (e: Exception) {
            Log.w(TAG, "Voice gate unavailable, using direct mic: ${e.message}")
            voiceGate = null
        }

        backgroundAgentRunner = com.soltini.app.agent.BackgroundAgentRunner(this, agentToolExecutor, appSettings)
        agentPhoneController = com.soltini.app.agent.AgentPhoneController(this, agentToolExecutor, agentToolExecutor.screenOperator, appSettings)
        deviceController = DeviceController.create(this)
        mqttManager = MqttManager.getInstance(this)

        if (WifiMqttCredentialsStore(this).isMqttConfigured()) {
            mqttManager.connect()
        }

        // Screen Companion Hook: When proactive insight is triggered, deliver to Gemini Live session
        com.soltini.app.companion.ScreenCompanionManager.getInstance(this).onProactiveInsightReady = { insightPrompt ->
            if (!isSleeping.value && geminiLiveManager.connectionState.value == ConnectionState.Live &&
                !com.soltini.app.agent.AgentTaskRegistry.isBusy() && activeToolCallCount.get() == 0) {
                geminiLiveManager.sendClientContent(insightPrompt)
            }
        }

        // Initialize helper managers
        pauseModeManager = VoicePauseModeManager(
            context = this,
            audioRecorder = audioRecorder,
            audioPlayer = audioPlayer,
            geminiLiveManager = geminiLiveManager,
            getWakeLock = { wakeLock },
            onStopWakeListening = { sleepStandbyManager.stopWakeListening() },
            onConnect = { connect() },
            onSetSleeping = { /* managed via pause state */ },
            onUpdateNotification = { updateNotification(it) },
            onStopSelf = { stopSelf() }
        )

        sleepStandbyManager = VoiceSleepStandbyManager(
            context = this,
            serviceScope = serviceScope,
            appSettings = appSettings,
            audioRecorder = audioRecorder,
            audioPlayer = audioPlayer,
            geminiLiveManager = geminiLiveManager,
            hasMicPermission = { _hasMicPermission.value },
            isLitePaused = { pauseModeManager.isLitePaused.value },
            isHardPaused = { pauseModeManager.isHardPaused.value },
            getConnectionState = { connectionState.value },
            getActiveToolCallCount = { activeToolCallCount.get() },
            getVoiceGate = { voiceGate },
            getWakeLock = { wakeLock },
            onConnect = { connect() }
        )
        sleepStandbyManager.initWakeWord(headsetRouter)
        sleepStandbyManager.startInactivityWatcher()

        callHandler = VoiceCallHandler(
            context = this,
            geminiLiveManager = geminiLiveManager,
            isSleeping = { isSleeping.value },
            isNetworkOnline = { isNetworkOnline() },
            onWakeUp = { wakeUp() },
            onConnect = { connect() }
        )
        callHandler.startListening()

        toolRouter = VoiceToolRouter(
            context = this,
            serviceScope = serviceScope,
            geminiLiveManager = geminiLiveManager,
            pluginRegistry = pluginRegistry,
            deviceController = deviceController,
            agentToolExecutor = agentToolExecutor,
            browserUseClient = browserUseClient,
            myraOrchestrator = myraOrchestrator,
            memory2Engine = memory2Engine,
            appSettings = appSettings,
            activeToolCallCount = activeToolCallCount,
            pendingAutomationRequests = pendingAutomationRequests,
            onSleepRequested = { sleep() },
            onSilentTurnSet = { silentTurn = it },
            onAppendUserMessage = { appendUserMessage(it) },
            onAppendAgentMessage = { appendAgentMessage(it) }
        )

        connectionState = geminiLiveManager.connectionState
        errorMessage = geminiLiveManager.errorMessage
        micAmplitude = audioRecorder.micAmplitude
        speakerAmplitude = audioPlayer.speakerAmplitude
        isSpeaking = audioPlayer.isSpeaking

        serviceScope.launch {
            connectionState.collect { state ->
                when (state) {
                    ConnectionState.Live -> {
                        if (appSettings.isScreenRecordingModeEnabled) {
                            try {
                                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                am.mode = AudioManager.MODE_IN_COMMUNICATION
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to set MODE_IN_COMMUNICATION: ${e.message}")
                            }
                        }

                        sleepStandbyManager.updateSpeechTimestamp()
                        if (_hasMicPermission.value) {
                            audioRecorder.startRecording()
                        }
                        audioPlayer.start()
                        if (!appSettings.isScreenRecordingModeEnabled) {
                            headsetRouter?.start()
                        }
                        OverlayService.getInstance()?.setRingState(com.soltini.app.overlay.RingState.LISTENING)
                        callHandler.checkAndAnnouncePendingCall()
                        if (sleepStandbyManager.wakeGreetingPending) {
                            sleepStandbyManager.wakeGreetingPending = false
                            serviceScope.launch {
                                delay(400)
                                if (connectionState.value == ConnectionState.Live && !isSleeping.value) {
                                    geminiLiveManager.sendClientContent(
                                        "[System: the user just woke you by saying your name. Greet them with one very short, warm sentence and wait for their request." +
                                            (com.soltini.app.learning.UpcomingDates.todayHint(this@BackgroundVoiceService)?.let { " $it Wish them warmly in the same sentence." } ?: "") + "]"
                                    )
                                }
                            }
                        }
                    }
                    ConnectionState.Disconnected, ConnectionState.Error -> {
                        try {
                            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            am.mode = AudioManager.MODE_NORMAL
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to restore MODE_NORMAL: ${e.message}")
                        }

                        audioRecorder.stopRecording()
                        headsetRouter?.stop()
                        val ringState = if (isSleeping.value) com.soltini.app.overlay.RingState.SLEEPING else com.soltini.app.overlay.RingState.IDLE
                        OverlayService.getInstance()?.setRingState(ringState)
                    }
                    else -> {}
                }
            }
        }

        // Keep OverlayService synced with amplitude
        serviceScope.launch {
            speakerAmplitude.collect { amp ->
                OverlayService.getInstance()?.setMouthAmplitude(amp)
            }
        }
        serviceScope.launch {
            isSpeaking.collect { isSpeaking ->
                audioRecorder.isModelSpeaking = isSpeaking
                voiceGate?.isModelSpeaking = isSpeaking
                if (isSpeaking) {
                    sleepStandbyManager.updateSpeechTimestamp()
                }
                OverlayService.getInstance()?.setTalking(isSpeaking)
                if (connectionState.value == ConnectionState.Live) {
                    if (isSpeaking) {
                        OverlayService.getInstance()?.setRingState(com.soltini.app.overlay.RingState.SPEAKING)
                    } else {
                        OverlayService.getInstance()?.setRingState(com.soltini.app.overlay.RingState.LISTENING)
                    }
                }
            }
        }

        // Auto-connect if restarted by system (e.g. after swipe from recents)
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            _hasMicPermission.value = true
            if (connectionState.value == ConnectionState.Disconnected) {
                connect()
            }
        }
        registerNetworkReconnect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!com.soltini.app.security.AppIdentityVerifier.isAllowed(this)) {
            Log.e(TAG, "Blocked: signing certificate does not match the pinned release certificate")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (isSleeping.value && !isLitePaused.value && !isHardPaused.value) {
            wakeUp()
        }
        if (!isSleeping.value && !isLitePaused.value && !isHardPaused.value) {
            if (_hasMicPermission.value && connectionState.value == ConnectionState.Disconnected &&
                !userExplicitlyDisconnected && appSettings.isAssistantMasterEnabled && !appSettings.isBedtimeQuietHoursActive()) {
                connect()
            }
        }
        // Trigger habit analysis scan on service start
        serviceScope.launch(Dispatchers.IO) {
            try {
                com.soltini.app.automation.ProactiveRoutineScheduler.analyzeHabitsAndSchedule(
                    this@BackgroundVoiceService,
                    memory2Engine.db
                )
            } catch (_: Exception) {}
        }
        // START_STICKY: Instructs Android OS to automatically recreate this service
        // if killed by low memory killer or background process reclamation.
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isLitePaused.value || isHardPaused.value || userExplicitlyDisconnected || !appSettings.isAssistantMasterEnabled || appSettings.isBedtimeQuietHoursActive()) {
            Log.i(TAG, "Task removed while PAUSED/DISCONNECTED/BEDTIME — shutting down completely and not auto-restarting")
            
            // Fully shut down everything since the user swiped away while paused
            audioRecorder.stopRecording()
            audioPlayer.stop()
            geminiLiveManager.disconnect()
            wakeLock?.let { if (it.isHeld) it.release() }
            
            com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isToolCallActive = false
            OverlayService.stop(this)
            stopSelf()
        } else {
            Log.i(TAG, "Task removed (swiped from recents) — scheduling AlarmManager to restart service")
            try {
                val voiceIntent = Intent(applicationContext, BackgroundVoiceService::class.java)
                val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PendingIntent.getForegroundService(
                        applicationContext,
                        1001,
                        voiceIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    PendingIntent.getService(
                        applicationContext,
                        1001,
                        voiceIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 1000,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 1000,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule resurrection alarm: ${e.message}", e)
            }
        }
        
        super.onTaskRemoved(rootIntent)
    }

    private fun startZeroBatterySilentAudioTrick() {
        try {
            // Generate a tiny 1-second silent WAV file
            val silentFile = File(cacheDir, "zero_battery_silence.wav")
            if (!silentFile.exists()) {
                val sampleRate = 8000
                val channels = 1
                val bitsPerSample = 16
                val byteRate = sampleRate * channels * bitsPerSample / 8
                val totalDataLen = sampleRate * 2 // 1 second of audio
                val totalAudioLen = totalDataLen
                val totalDataLenWithHeader = totalDataLen + 36

                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(totalDataLenWithHeader)
                header.put("WAVE".toByteArray())
                header.put("fmt ".toByteArray())
                header.putInt(16) // Subchunk1Size
                header.putShort(1.toShort()) // AudioFormat (PCM)
                header.putShort(channels.toShort())
                header.putInt(sampleRate)
                header.putInt(byteRate)
                header.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
                header.putShort(bitsPerSample.toShort())
                header.put("data".toByteArray())
                header.putInt(totalAudioLen)

                FileOutputStream(silentFile).use { fos ->
                    fos.write(header.array())
                    fos.write(ByteArray(totalAudioLen)) // zeroes
                }
            }

            try {
                silentMediaPlayer?.release()
            } catch (_: Exception) {}

            silentMediaPlayer = MediaPlayer().apply {
                setDataSource(silentFile.absolutePath)
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setVolume(0f, 0f)
                isLooping = true
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Silent media player error ($what, $extra), restarting...")
                    try {
                        release()
                    } catch (_: Exception) {}
                    serviceScope.launch {
                        delay(2000)
                        startZeroBatterySilentAudioTrick()
                    }
                    true
                }
                prepare()
                start()
            }
            Log.i(TAG, "Zero-battery silent audio trick activated.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start silent media player: ${e.message}")
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun onPermissionResult(granted: Boolean) {
        _hasMicPermission.value = granted
        if (granted) {
            // Re-call startForegroundService now that we have permission, so the OS adds the microphone FGS type
            startForegroundService()
            if (connectionState.value == ConnectionState.Live) {
                audioRecorder.startRecording()
            }
        }
    }

    fun connect() {
        userExplicitlyDisconnected = false
        if (isPaused.value) {
            Log.i(TAG, "connect() ignored because service is paused.")
            return
        }
        // The mic permission may have been granted after this service started (first-run setup).
        // Re-check here so the session never waits for an app restart.
        if (!_hasMicPermission.value && androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionResult(true)
        }
        sleepStandbyManager.markAwake()
        geminiLiveManager.connect()
    }

    fun isNetworkOnline(): Boolean = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
        true // unknown -> assume online, never block the connection
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    /** Reconnect the moment internet returns instead of waiting for backoff / the 4-minute watchdog. */
    private fun registerNetworkReconnect() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val cb = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    serviceScope.launch {
                        val st = connectionState.value
                        if (_hasMicPermission.value && !isSleeping.value && !isPaused.value &&
                            !userExplicitlyDisconnected && appSettings.isAssistantMasterEnabled && !appSettings.isBedtimeQuietHoursActive() &&
                            (st == ConnectionState.Disconnected || st == ConnectionState.Reconnecting)
                        ) {
                            Log.i(TAG, "Network available again: reconnecting Gemini Live")
                            connect()
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "Network callback registration failed: ${e.message}")
        }
    }

    /**
     * Disconnects the current session and immediately opens a fresh one.
     * Used by the "Save & Reconnect" button in AI Configuration settings
     * so new API key / persona / voice take effect without restarting.
     */
    fun reconnect() {
        Log.i(TAG, "Reconnecting to apply new AI settings")
        audioRecorder.isDataSaverEnabled = appSettings.isDataSaverEnabled
        audioRecorder.vadThreshold = appSettings.vadThreshold
        audioRecorder.micGain = appSettings.micGain
        audioRecorder.isScreenRecordingModeEnabled = appSettings.isScreenRecordingModeEnabled
        audioRecorder.stopRecording()
        audioPlayer.stop()
        geminiLiveManager.disconnect()
        // Small gap to let WebSocket close cleanly before re-opening
        serviceScope.launch {
            kotlinx.coroutines.delay(150)
            connect()
        }
    }

    fun disconnect() {
        userExplicitlyDisconnected = true
        audioRecorder.stopRecording()
        audioPlayer.stop()
        geminiLiveManager.disconnect()
    }

    fun toggleMute() {
        val newMute = !_isMuted.value
        _isMuted.value = newMute
        audioRecorder.isMuted = newMute
    }

    fun clearTranscript() {
        _transcripts.value = emptyList()
        currentGeminiTranscript.clear()
        currentTurnGeminiEntryId = null
        serviceScope.launch(Dispatchers.IO) {
            memory2Engine.db.clearConversationHistory()
        }
    }

    fun appendUserMessage(text: String): TranscriptEntry {
        val entry = TranscriptEntry(
            sender = TranscriptEntry.Sender.USER,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        val list = _transcripts.value.toMutableList()
        list.add(entry)
        _transcripts.value = list
        serviceScope.launch(Dispatchers.IO) {
            memory2Engine.db.saveConversationEntry(entry.id, entry.sender.name, entry.text, entry.timestamp)
        }
        return entry
    }

    fun appendAgentMessage(text: String): TranscriptEntry {
        val entry = TranscriptEntry(
            sender = TranscriptEntry.Sender.GEMINI,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        val list = _transcripts.value.toMutableList()
        list.add(entry)
        _transcripts.value = list
        serviceScope.launch(Dispatchers.IO) {
            memory2Engine.db.saveConversationEntry(entry.id, entry.sender.name, entry.text, entry.timestamp)
        }
        return entry
    }

    fun processUserTextCommand(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        appendUserMessage(trimmed)
        serviceScope.launch(Dispatchers.IO) {
            try {
                val orchestratorResult = myraOrchestrator.processRequest(trimmed)
                appendAgentMessage(orchestratorResult.responseText)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing text command: ${e.message}", e)
                appendAgentMessage("Error executing command: ${e.message}")
            }
        }
    }

    // ── Pause Modes ─────────────────────────────────────────────────────────
    fun enterLitePauseMode() = pauseModeManager.enterLitePauseMode()
    fun exitLitePauseMode() = pauseModeManager.exitLitePauseMode()
    fun toggleLitePauseMode() = pauseModeManager.toggleLitePauseMode()

    fun enterHardPauseMode() = pauseModeManager.enterHardPauseMode()
    fun exitHardPauseMode() = pauseModeManager.exitHardPauseMode()
    fun toggleHardPauseMode() = pauseModeManager.toggleHardPauseMode()

    fun enterPauseMode() = pauseModeManager.enterPauseMode()
    fun exitPauseMode() = pauseModeManager.exitPauseMode()
    fun togglePauseMode() = pauseModeManager.togglePauseMode()

    // ── Sleep & Wake ────────────────────────────────────────────────────────
    fun sleep() = sleepStandbyManager.sleep()
    fun wakeUp() = sleepStandbyManager.wakeUp()
    fun toggleSleep() = sleepStandbyManager.toggleSleep()

    // ── ToolCallListener Implementation ─────────────────────────────────────
    override fun onToolCall(callId: String, toolName: String, args: JSONObject) {
        toolRouter.routeToolCall(callId, toolName, args)
    }

    // ─── GeminiLiveListener Implementation ───────────────────────────────────

    override fun onAudioDataReceived(pcmData: ByteArray) {
        if (appSettings.isSilentExecutionMode && silentTurn) return
        audioPlayer.playChunk(pcmData)
    }

    override fun onTextReceived(text: String) {
        geminiTextPartsSeen = true
        finalizeUserTurn()
        appendGeminiText(text)
    }

    // ─── Speech-to-text of both sides (saved to the local conversation archive) ───

    private var currentTurnUserEntryId: String? = null
    private val currentUserTranscript = StringBuilder()
    private var geminiTextPartsSeen = false

    override fun onInputTranscript(text: String) {
        currentUserTranscript.append(text)
        val full = currentUserTranscript.toString().trim()
        if (full.isBlank()) return
        val list = _transcripts.value.toMutableList()
        val idx = currentTurnUserEntryId?.let { id -> list.indexOfLast { it.id == id } } ?: -1
        if (idx != -1) {
            list[idx] = list[idx].copy(text = full)
        } else {
            val entry = TranscriptEntry(sender = TranscriptEntry.Sender.USER, text = full)
            currentTurnUserEntryId = entry.id
            list.add(entry)
        }
        _transcripts.value = list
    }

    override fun onOutputTranscript(text: String) {
        // If the server also sends real text parts, those already feed the transcript - don't double it.
        if (geminiTextPartsSeen) return
        finalizeUserTurn()
        appendGeminiText(text)
    }

    /** The user finished speaking (Myra started answering / turn ended): save what they said. */
    private fun finalizeUserTurn() {
        val id = currentTurnUserEntryId ?: return
        val text = currentUserTranscript.toString().trim()
        currentUserTranscript.clear()
        currentTurnUserEntryId = null
        if (text.isBlank()) return
        val ts = _transcripts.value.find { it.id == id }?.timestamp ?: System.currentTimeMillis()
        serviceScope.launch(Dispatchers.IO) {
            memory2Engine.db.saveConversationEntry(id, TranscriptEntry.Sender.USER.name, text, ts)
        }
    }

    private fun appendGeminiText(text: String) {
        currentGeminiTranscript.append(text)
        val fullText = currentGeminiTranscript.toString().trim()

        val list = _transcripts.value.toMutableList()
        val existingIndex = if (currentTurnGeminiEntryId != null) {
            list.indexOfLast { it.id == currentTurnGeminiEntryId }
        } else {
            -1
        }

        if (existingIndex != -1) {
            list[existingIndex] = list[existingIndex].copy(text = fullText)
        } else {
            val newEntry = TranscriptEntry(sender = TranscriptEntry.Sender.GEMINI, text = fullText)
            currentTurnGeminiEntryId = newEntry.id
            list.add(newEntry)
        }

        _transcripts.value = list
    }

    override fun onInterrupted() {
        Log.d(TAG, "User interrupted Gemini speech")
        silentTurn = false
        audioPlayer.interrupt()
        // Keep what Myra had already said before being cut off.
        val partial = currentGeminiTranscript.toString().trim()
        val partialId = currentTurnGeminiEntryId
        if (partial.isNotBlank() && partialId != null) {
            val ts = _transcripts.value.find { it.id == partialId }?.timestamp ?: System.currentTimeMillis()
            serviceScope.launch(Dispatchers.IO) {
                memory2Engine.db.saveConversationEntry(partialId, TranscriptEntry.Sender.GEMINI.name, partial, ts)
            }
        }
        currentGeminiTranscript.clear()
        currentTurnGeminiEntryId = null
        geminiTextPartsSeen = false
    }

    override fun onTurnComplete() {
        Log.d(TAG, "Gemini turn completed")
        silentTurn = false
        finalizeUserTurn()
        geminiTextPartsSeen = false
        // Capture the finished transcript before clearing, then persist to DB and extract memories
        val finishedText = currentGeminiTranscript.toString().trim()
        val turnId = currentTurnGeminiEntryId
        currentGeminiTranscript.clear()
        currentTurnGeminiEntryId = null

        if (finishedText.isNotBlank()) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    // Persist completed response to SQLite conversation history
                    val completedEntry = _transcripts.value.find { it.id == turnId }
                        ?: _transcripts.value.lastOrNull { it.sender == TranscriptEntry.Sender.GEMINI }
                    if (completedEntry != null) {
                        memory2Engine.db.saveConversationEntry(
                            id = completedEntry.id,
                            sender = completedEntry.sender.name,
                            text = completedEntry.text,
                            timestamp = completedEntry.timestamp
                        )
                    }

                    val lastUser = _transcripts.value.lastOrNull { it.sender == TranscriptEntry.Sender.USER }?.text
                        ?: "Voice conversation turn"

                    memory2Engine.evaluateAndStore(
                        goal = lastUser,
                        outcomeSummary = finishedText,
                        toolSequence = emptyList(),
                        isSuccess = true,
                        userExplicitStatement = finishedText
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Memory extraction / conversation save error (non-fatal): ${e.message}")
                }
            }
        }
    }

    override fun onError(message: String) {
        Log.e(TAG, "Gemini Live Error: $message")
        // Reset tool call counter and deactivate accessibility on connection error
        activeToolCallCount.set(0)
        com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isToolCallActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        try { voiceGate?.release() } catch (_: Exception) {}
        voiceGate = null
        if (instance == this) {
            instance = null
        }

        try {
            silentMediaPlayer?.stop()
            silentMediaPlayer?.release()
            silentMediaPlayer = null
        } catch (e: Exception) { /* ignore */ }

        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        try {
            sleepStandbyManager.destroy()
        } catch (_: Exception) {}
        com.soltini.app.agent.AgentStatusBus.listener = null
        headsetRouter?.stop()
        audioRecorder.stopRecording()
        audioPlayer.stop()
        geminiLiveManager.disconnect()
        try {
            networkCallback?.let {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        try {
            com.soltini.app.telephony.CallNotificationManager.getInstance(this).shutdown()
        } catch (_: Exception) {}
    }

    private fun buildNotification(text: String): android.app.Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Myra")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun startForegroundService() {
        val notification = buildNotification("Your AI Assistant is ready")

        if (Build.VERSION.SDK_INT >= 34) {
            val specialUse = 1073741824 // ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            val mic = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            try {
                if (hasMic) {
                    startForeground(NOTIFICATION_ID, notification, specialUse or mic)
                } else {
                    startForeground(NOTIFICATION_ID, notification, specialUse)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startForeground with microphone type failed (${e.message}), falling back to specialUse")
                try {
                    startForeground(NOTIFICATION_ID, notification, specialUse)
                } catch (e2: Exception) {
                    Log.e(TAG, "startForeground fallback failed: ${e2.message}", e2)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            try {
                if (hasMic) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startForeground failed: ${e.message}")
                try { startForeground(NOTIFICATION_ID, notification) } catch (_: Exception) {}
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e(TAG, "startForeground error: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Session Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
