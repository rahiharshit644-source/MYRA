package com.soltini.app.secretary

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.audio.GeminiNeuralVoiceSynthesizer
import com.soltini.app.audio.JarvisSoundEngine
import com.soltini.app.notifications.NotificationRepository
import com.soltini.app.settings.AppSettings
import com.soltini.app.telephony.CallNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class SecretaryPendingMessage(
    val sender: String,
    val text: String,
    val appName: String,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SecretaryPendingCall(
    val callerName: String,
    val phoneNumber: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SecretaryState(
    val isEnabled: Boolean = true,
    val isAnnouncerEnabled: Boolean = false,
    val pendingMessage: SecretaryPendingMessage? = null,
    val pendingCall: SecretaryPendingCall? = null,
    val isPromptActive: Boolean = false,
    val lastActionReport: String = "STANDBY"
)

/**
 * MyraVoiceSecretaryEngine — Autonomous WhatsApp & Call Voice Secretary
 *
 * Uses authentic Gemini Live neural voices (never robotic Android TTS):
 *  - Master Announce Toggle: STRICTLY OPT-IN. Stays completely silent unless user turns ON Voice Announcer!
 *  - Headset & WhatsApp Filters: User can restrict speech to Headphones-only or WhatsApp-only.
 *  - Pure Gemini Voice: All spoken notifications are vocalized via Gemini Live WebSocket or Gemini Neural Audio synthesis.
 *  - Direct Reply: Dispatches Android RemoteInput responses without opening WhatsApp or unlocking phone.
 */
class MyraVoiceSecretaryEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "VoiceSecretary"

        @Volatile
        private var instance: MyraVoiceSecretaryEngine? = null

        fun getInstance(context: Context): MyraVoiceSecretaryEngine =
            instance ?: synchronized(this) {
                instance ?: MyraVoiceSecretaryEngine(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val appSettings = AppSettings(appContext)
    private val geminiSpeaker = GeminiNeuralVoiceSynthesizer.getInstance(appContext)
    private val notifRepo = NotificationRepository.getInstance(appContext)
    private val callManager = CallNotificationManager.getInstance(appContext)

    private val _secretaryState = MutableStateFlow(
        SecretaryState(
            isEnabled = true,
            isAnnouncerEnabled = appSettings.isNotificationAnnouncerEnabled
        )
    )
    val secretaryState: StateFlow<SecretaryState> = _secretaryState.asStateFlow()

    private var promptTimeoutJob: Job? = null
    private var lastAnnouncedKey: String = ""
    private var lastAnnouncedTime: Long = 0L

    /**
     * Toggles the voice announcement feature on or off.
     */
    fun toggleVoiceAnnouncer(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !appSettings.isNotificationAnnouncerEnabled
        appSettings.isNotificationAnnouncerEnabled = next
        _secretaryState.value = _secretaryState.value.copy(isAnnouncerEnabled = next)
        JarvisSoundEngine.playTacticalBlip()
        val statusMsg = if (next) "🎙️ Gemini Voice Notification Announcer: ACTIVE" else "🔕 Notification Announcer: SILENT (OFF)"
        AgentStatusBus.emit(statusMsg)
        return next
    }

    fun toggleSecretary(enabled: Boolean? = null): Boolean {
        val next = enabled ?: !_secretaryState.value.isEnabled
        _secretaryState.value = _secretaryState.value.copy(isEnabled = next)
        JarvisSoundEngine.playTacticalBlip()
        AgentStatusBus.emit(if (next) "🎙️ Autonomous Voice Secretary: ACTIVE" else "🎙️ Voice Secretary: STANDBY")
        return next
    }

    /**
     * Intercepts incoming StatusBarNotification from NotificationListener.
     */
    fun onNotificationReceived(sbn: StatusBarNotification) {
        if (!_secretaryState.value.isEnabled) return
        if (appSettings.isBedtimeQuietHoursActive()) return

        val pkg = sbn.packageName ?: return
        if (pkg == appContext.packageName) return // Ignore self

        val isWhatsApp = pkg.contains("whatsapp", ignoreCase = true)
        val isTelegram = pkg.contains("telegram", ignoreCase = true)
        val isSms = pkg.contains("messaging", ignoreCase = true) || pkg.contains("mms", ignoreCase = true)

        if (!isWhatsApp && !isTelegram && !isSms) return

        val notif = sbn.notification ?: return
        val extras = notif.extras ?: Bundle()

        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
        val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val cleanTitle = rawTitle.replace(Regex("\\(\\d+\\s+messages?\\)"), "").trim()
        val cleanText = rawText.trim()

        if (cleanTitle.isBlank() || cleanText.isBlank()) return
        if (cleanText.contains("new messages", ignoreCase = true) || cleanText.contains("WhatsApp Web", ignoreCase = true)) return

        val notifKey = "${pkg}_${cleanTitle}_${cleanText.take(20)}"
        if (notifKey == lastAnnouncedKey && System.currentTimeMillis() - lastAnnouncedTime < 8000L) {
            return // Prevent duplicate speech prompts for same burst
        }
        lastAnnouncedKey = notifKey
        lastAnnouncedTime = System.currentTimeMillis()

        val appName = when {
            isWhatsApp -> "WhatsApp"
            isTelegram -> "Telegram"
            else -> "Message"
        }

        val pending = SecretaryPendingMessage(
            sender = cleanTitle,
            text = cleanText,
            appName = appName,
            packageName = pkg
        )

        // ─── STRICT USER PREFERENCE CHECKS ─────────────────────────────────────────
        // 1. If user has NOT enabled voice announcements, stay completely silent!
        if (!appSettings.isNotificationAnnouncerEnabled) {
            _secretaryState.value = _secretaryState.value.copy(
                pendingMessage = pending,
                isPromptActive = false,
                lastActionReport = "RECEIVED (SILENT): $appName from $cleanTitle"
            )
            AgentStatusBus.emit("💬 $cleanTitle ($appName): \"$cleanText\" (Silent Mode)")
            return
        }

        // 2. If WhatsApp-only filter is enabled and this is not WhatsApp, stay silent
        if (appSettings.isAnnounceWhatsAppOnly && !isWhatsApp) {
            Log.d(TAG, "Skipping non-WhatsApp message because announceWhatsAppOnly is enabled")
            return
        }

        // 3. If Headphones-only filter is enabled and headphones are not connected, stay silent
        if (appSettings.isAnnounceOnlyHeadphones && !geminiSpeaker.isHeadsetConnected()) {
            Log.d(TAG, "Suppressed voice announcement: Headphones required but not plugged in")
            return
        }

        // ─── GEMINI NEURAL VOICE ANNOUNCEMENT ──────────────────────────────────────
        _secretaryState.value = _secretaryState.value.copy(
            pendingMessage = pending,
            isPromptActive = true,
            lastActionReport = "ANNOUNCING: $appName from $cleanTitle"
        )

        JarvisSoundEngine.playTacticalBlip()

        val voiceNoteTranscriber = WhatsAppVoiceNoteTranscriber.getInstance(appContext)
        if (isWhatsApp && voiceNoteTranscriber.isVoiceNoteNotification(cleanText)) {
            // Autonomous AI Transcription & Summary
            voiceNoteTranscriber.processVoiceNote(cleanTitle, cleanText) { summary ->
                _secretaryState.value = _secretaryState.value.copy(
                    lastActionReport = "SUMMARIZED: ${summary.tacticalSummary}"
                )
            }
        } else {
            val promptSpeech = "Boss, ${cleanTitle} ka ${appName} message aaya hai: \"${cleanText}\". Kya reply bheju?"
            geminiSpeaker.speakWithGeminiVoice(promptSpeech)
            AgentStatusBus.emit("💬 Secretary (Gemini Voice): $cleanTitle -> \"$cleanText\"")
        }

        // Keep prompt active waiting for Boss's response
        promptTimeoutJob?.cancel()
        promptTimeoutJob = scope.launch {
            delay(18000L)
            if (_secretaryState.value.pendingMessage == pending) {
                _secretaryState.value = _secretaryState.value.copy(
                    isPromptActive = false,
                    pendingMessage = null
                )
            }
        }
    }

    /**
     * Intercepts incoming phone calls.
     */
    fun onIncomingCallDetected(callerName: String, phoneNumber: String) {
        if (!_secretaryState.value.isEnabled) return
        if (appSettings.isBedtimeQuietHoursActive()) return

        val pending = SecretaryPendingCall(
            callerName = callerName.ifBlank { "Unknown Caller" },
            phoneNumber = phoneNumber
        )

        _secretaryState.value = _secretaryState.value.copy(
            pendingCall = pending,
            isPromptActive = true,
            lastActionReport = "CALL: from ${pending.callerName}"
        )

        // Only announce via voice if announcer is enabled
        if (appSettings.isNotificationAnnouncerEnabled) {
            val promptSpeech = "Boss, ${pending.callerName} ka call aa raha hai. Uthau ya cut karu?"
            geminiSpeaker.speakWithGeminiVoice(promptSpeech)
        }
        AgentStatusBus.emit("📞 Secretary: Incoming Call from ${pending.callerName}")
    }

    /**
     * Handles verbal user response when secretary prompt is active.
     * e.g., "Bol do 10 minute me aa raha hoon", "Uthao", "Reject kar do".
     */
    fun handleVoiceInput(input: String): Boolean {
        val query = input.trim().lowercase(Locale.ROOT)
        val state = _secretaryState.value

        // 1. Handling incoming Call response
        if (state.pendingCall != null) {
            if (query.contains("uthalo") || query.contains("uthao") || query.contains("pick") || query.contains("answer") || query.contains("receive")) {
                callManager.answerCall()
                geminiSpeaker.speakWithGeminiVoice("Call pick up kar rahi hoon, Boss.")
                _secretaryState.value = state.copy(pendingCall = null, isPromptActive = false, lastActionReport = "ANSWERED CALL")
                return true
            }
            if (query.contains("drive") || query.contains("driving") || query.contains("bike") || query.contains("chala raha")) {
                callManager.rejectCall()
                com.soltini.app.copilot.DrivingCoPilotEngine.getInstance(appContext).dispatchDrivingAutoReply(state.pendingCall.callerName)
                _secretaryState.value = state.copy(pendingCall = null, isPromptActive = false, lastActionReport = "CO-PILOT AUTO-REPLY")
                return true
            }
            if (query.contains("cut") || query.contains("reject") || query.contains("kaat do") || query.contains("decline")) {
                callManager.rejectCall()
                geminiSpeaker.speakWithGeminiVoice("Call cut kar diya hai, Boss.")
                _secretaryState.value = state.copy(pendingCall = null, isPromptActive = false, lastActionReport = "REJECTED CALL")
                return true
            }
        }

        // 2. Handling incoming Message response
        val msg = state.pendingMessage
        if (msg != null) {
            if (query.contains("ignore") || query.contains("rehne do") || query.contains("cancel") || query.contains("kuch nahi")) {
                geminiSpeaker.speakWithGeminiVoice("Theek hai Boss, ignore kar diya.")
                _secretaryState.value = state.copy(pendingMessage = null, isPromptActive = false, lastActionReport = "IGNORED MESSAGE")
                return true
            }

            var replyContent = query
            val prefixes = listOf("bol do ", "bol de ", "bata do ", "bata de ", "reply karo ", "reply kar do ", "reply: ", "bhejo: ", "message karo ")
            for (p in prefixes) {
                if (replyContent.startsWith(p)) {
                    replyContent = replyContent.removePrefix(p).trim()
                    break
                }
            }

            if (replyContent.isNotBlank()) {
                val res = notifRepo.replyToNotification(msg.sender, replyContent)
                if (res is com.soltini.app.notifications.ReplyResult.Success) {
                    JarvisSoundEngine.playTaskSuccessChime()
                    geminiSpeaker.speakWithGeminiVoice("${msg.sender} ko reply bhej diya hai, Boss.")
                    AgentStatusBus.emit("✅ Secretary Sent: \"$replyContent\" to ${msg.sender}")
                    _secretaryState.value = state.copy(pendingMessage = null, isPromptActive = false, lastActionReport = "SENT: \"$replyContent\"")
                    return true
                } else {
                    Log.w(TAG, "Direct reply failed: ${(res as? com.soltini.app.notifications.ReplyResult.Failure)?.reason}")
                }
            }
        }

        return false
    }

    fun dismissPrompt() {
        promptTimeoutJob?.cancel()
        _secretaryState.value = _secretaryState.value.copy(
            isPromptActive = false,
            pendingMessage = null,
            pendingCall = null
        )
    }
}
