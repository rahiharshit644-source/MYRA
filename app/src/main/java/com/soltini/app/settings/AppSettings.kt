package com.soltini.app.settings

import android.content.Context
import com.soltini.app.BuildConfig

/**
 * AppSettings — persistent user-configurable AI settings.
 *
 * Stored in SharedPreferences so they survive app restarts.
 * Changes take effect the next time GeminiLiveManager.connect() is called
 * (triggered by the "Save & Reconnect" button in the settings UI).
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Gemini API Key ────────────────────────────────────────────────────────

    var geminiApiKey: String
        get() = readSecret(KEY_API_KEY)
        set(value) = writeSecret(KEY_API_KEY, value.trim())

    // ── Secrets at rest (Keystore AES-GCM, transparent migration from plaintext) ──

    private fun readSecret(key: String): String {
        val raw = prefs.getString(key, "") ?: ""
        if (raw.isEmpty()) return ""
        if (!com.soltini.app.security.SecretVault.isSealed(raw)) {
            // legacy plaintext: encrypt it now, keep working
            val sealed = com.soltini.app.security.SecretVault.seal(raw)
            if (com.soltini.app.security.SecretVault.isSealed(sealed)) prefs.edit().putString(key, sealed).apply()
            return raw
        }
        return com.soltini.app.security.SecretVault.open(raw) ?: ""
    }

    private fun writeSecret(key: String, value: String) {
        prefs.edit().putString(key, if (value.isBlank()) "" else com.soltini.app.security.SecretVault.seal(value)).apply()
    }

    /**
     * Returns the user's own key if set, otherwise falls back to the
     * BuildConfig key bundled at compile time.
     */
    fun effectiveApiKey(): String {
        val userKey = geminiApiKey.trim()
        return if (userKey.isNotBlank()) userKey else BuildConfig.GEMINI_API_KEY
    }

    // ── App & Developer Identity ──────────────────────────────────────────────

    /**
     * Developer / creator name — PERMANENT.
     * This is intentionally a `val`, not backed by SharedPreferences and not
     * settable from anywhere (UI included). Nobody — not even a rebuilt
     * Settings screen — can change this at runtime; changing it requires
     * editing DEFAULT_DEVELOPER_NAME in source and rebuilding the app.
     */
    val developerName: String
        get() = DEFAULT_DEVELOPER_NAME

    var appDisplayName: String
        get() = prefs.getString(KEY_APP_NAME, DEFAULT_APP_NAME) ?: DEFAULT_APP_NAME
        set(value) = prefs.edit().putString(KEY_APP_NAME, value.trim()).apply()

    /**
     * Public-facing display name any installer/user sets for themselves
     * (shown in Settings as "Username"). The AI addresses this person by
     * this name — UNLESS they are Boss-recognized (see below), in which
     * case they're always called "Boss" regardless of what's stored here.
     *
     * Hidden behavior: typing the secret developer code into this exact
     * field (instead of a real name) does NOT save the code as a username —
     * it silently flips [isBossRecognized] on instead, and the field falls
     * back to showing whatever name was stored before (or blank).
     */
    var ownerUsername: String
        get() = prefs.getString(KEY_OWNER_USERNAME, "") ?: ""
        set(value) {
            val trimmed = value.trim()
            if (trimmed == DEV_UNLOCK_CODE) {
                isBossRecognized = true
            } else {
                prefs.edit().putString(KEY_OWNER_USERNAME, trimmed).apply()
            }
        }

    /**
     * True once the hidden developer code has been entered on this device.
     * When true, [currentAddressName] always returns "Boss" no matter what
     * [ownerUsername] holds. There is no UI to turn this off — by design.
     */
    var isBossRecognized: Boolean
        get() = prefs.getBoolean(KEY_BOSS_RECOGNIZED, false)
        set(value) = prefs.edit().putBoolean(KEY_BOSS_RECOGNIZED, value).apply()

    /** What the AI should call the current person right now. */
    fun currentAddressName(): String {
        if (isBossRecognized) return "Boss"
        val name = ownerUsername.trim()
        return if (name.isNotBlank()) name else "Boss"
    }

    // ── AI Persona ────────────────────────────────────────────────────────────

    var aiPersona: String
        get() {
            val stored = prefs.getString(KEY_PERSONA, null)
            val addr = currentAddressName()
            if (stored.isNullOrBlank() || stored.contains("Satish Acharya") || stored.contains("satish.com.np") || !stored.contains("IDENTITY & SECRET DEVELOPER") || stored.contains("Soltini") || !stored.contains("HUMAN EMOTION") || !stored.contains("PUBLIC APIS DIRECTORY") || !stored.contains("FULL CONTROL") || !stored.contains(addr)) {
                val fresh = generatePersona(developerName, appDisplayName, addr)
                prefs.edit().putString(KEY_PERSONA, fresh).apply()
                return fresh
            }
            return stored
        }
        set(value) = prefs.edit().putString(KEY_PERSONA, value).apply()

    // ── Voice Model ───────────────────────────────────────────────────────────

    var voiceName: String
        get() = prefs.getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
        set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

    // ── Data Saver, VAD, Gain & Auto-Sleep Settings ──────────────────────────

    var isDataSaverEnabled: Boolean
        get() = prefs.getBoolean(KEY_DATA_SAVER, true)
        set(value) = prefs.edit().putBoolean(KEY_DATA_SAVER, value).apply()

    var vadSensitivity: String
        get() = prefs.getString(KEY_VAD_SENSITIVITY, SENSITIVITY_BALANCED) ?: SENSITIVITY_BALANCED
        set(value) = prefs.edit().putString(KEY_VAD_SENSITIVITY, value).apply()

    val vadThreshold: Float
        get() = when (vadSensitivity) {
            SENSITIVITY_LOW -> 0.020f
            SENSITIVITY_HIGH -> 0.005f
            else -> 0.010f
        }

    /** Mic gain booster multiplier (1.0x to 2.5x) — boosts quiet speech so Gemini never ignores user's voice */
    var micGain: Float
        get() = prefs.getFloat(KEY_MIC_GAIN, 1.5f)
        set(value) = prefs.edit().putFloat(KEY_MIC_GAIN, value.coerceIn(1.0f, 3.0f)).apply()

    /** Idle timeout in minutes before Myra goes to standby & closes the WebSocket (0 = disabled) */
    var autoSleepMinutes: Int
        get() = prefs.getInt(KEY_AUTO_SLEEP_MINUTES, 3)
        set(value) = prefs.edit().putInt(KEY_AUTO_SLEEP_MINUTES, value.coerceAtLeast(0)).apply()

    // ── Wake word & Standby ──────────────────────────────────────────────────

    /** When enabled, saying "Myra" / "Hey Myra" wakes her from standby (offline Vosk listener keeps the mic). */
    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    /** When enabled, Myra stays quiet while executing tasks (no spoken narration); normal chat stays audible. */
    var isSilentExecutionMode: Boolean
        get() = prefs.getBoolean(KEY_SILENT_EXECUTION, false)
        set(value) = prefs.edit().putBoolean(KEY_SILENT_EXECUTION, value).apply()

    /** Learn favourites (songs/apps/contacts) and routines from what Boss does. Default ON; stored as visible memories. */
    var isHabitLearningEnabled: Boolean
        get() = prefs.getBoolean(KEY_HABIT_LEARNING, true)
        set(value) = prefs.edit().putBoolean(KEY_HABIT_LEARNING, value).apply()

    /** Agent pauses and asks before payments, orders, deletions and password/PIN fields. Default ON. */
    var isAgentRiskConfirmEnabled: Boolean
        get() = prefs.getBoolean(KEY_AGENT_RISK_CONFIRM, true)
        set(value) = prefs.edit().putBoolean(KEY_AGENT_RISK_CONFIRM, value).apply()

    /** Opt-in: lets Myra read a fresh OTP from notifications (read_otp tool). Default OFF. */
    var isOtpReaderEnabled: Boolean
        get() = prefs.getBoolean(KEY_OTP_READER, false)
        set(value) = prefs.edit().putBoolean(KEY_OTP_READER, value).apply()

    /** Country calling code added to 10-digit numbers for WhatsApp (e.g. "91" for India). */
    var defaultCountryCode: String
        get() = prefs.getString(KEY_DEFAULT_COUNTRY_CODE, "91") ?: "91"
        set(value) = prefs.edit().putString(KEY_DEFAULT_COUNTRY_CODE, value.filter { it.isDigit() }.ifBlank { "91" }).apply()

    /** Preferred mic source ("VOICE_COMMUNICATION" vs "MIC") */
    var audioSource: String
        get() = prefs.getString(KEY_AUDIO_SOURCE, "MIC") ?: "MIC"
        set(value) = prefs.edit().putString(KEY_AUDIO_SOURCE, value).apply()

    // ── Screen Recording Mode (Anti-Echo) ────────────────────────────────────

    /** When enabled, applies smart noise gate and MODE_IN_COMMUNICATION to fix echo during screen recording */
    var isScreenRecordingModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_RECORDING_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_RECORDING_MODE, value).apply()

    // ── Live Screen Companion (Proactive Human Eye) ──────────────────────────

    /** When enabled, Soltini proactively observes screen work/typing and offers human-like advice/suggestions */
    var isLiveScreenCompanionEnabled: Boolean
        get() = prefs.getBoolean(KEY_LIVE_SCREEN_COMPANION, false)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_SCREEN_COMPANION, value).apply()

    // ── Banking Protection Settings ──────────────────────────────────────────

    var isBankingProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_BANKING_PROTECTION, true)
        set(value) = prefs.edit().putBoolean(KEY_BANKING_PROTECTION, value).apply()

    var isBankingModePaused: Boolean
        get() = prefs.getBoolean(KEY_BANKING_PAUSED, false)
        set(value) = prefs.edit().putBoolean(KEY_BANKING_PAUSED, value).apply()

    fun isBankingApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val pkgLower = packageName.lowercase()

        // Match against explicit package list
        if (DEFAULT_BANKING_PACKAGES.contains(pkgLower)) return true

        // Keyword heuristics for unlisted banking/financial apps
        val keywords = listOf("bank", "paytm", "phonepe", "gpay", "wallet", "upi", "paisa", "financial", "mobilebanking")
        return keywords.any { pkgLower.contains(it) } && !pkgLower.contains("wallpaper") && !pkgLower.contains("systemui")
    }

    // ── Mem0 Advanced Memory Engine Settings ─────────────────────────────────

    /** Optional Mem0 Platform API key (starts with m0-...) for cloud sync */
    var mem0ApiKey: String
        get() = readSecret(KEY_MEM0_API_KEY)
        set(value) = writeSecret(KEY_MEM0_API_KEY, value.trim())

    // ── Browser-Use Bridge (remote real-browser automation) ─────────────────

    /**
     * Base URL of a soltini_bridge_server.py instance (e.g. http://192.168.1.23:8787),
     * run separately on a PC/VPS with Python + Playwright + browser-use installed.
     * Blank means the browser_use_task tool is disabled.
     */
    var browserUseServerUrl: String
        get() = prefs.getString(KEY_BROWSER_USE_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BROWSER_USE_SERVER_URL, value.trim()).apply()

    /** Enable/disable Mem0 cloud sync (falls back to local SQLite Mem0 engine if offline/empty) */
    var isMem0CloudSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_MEM0_CLOUD_SYNC, true)
        set(value) = prefs.edit().putBoolean(KEY_MEM0_CLOUD_SYNC, value).apply()

    /** User identifier for Mem0 memory partitioning (default: "boss") */
    var mem0UserId: String
        get() = prefs.getString(KEY_MEM0_USER_ID, "boss") ?: "boss"
        set(value) = prefs.edit().putString(KEY_MEM0_USER_ID, value.trim().ifBlank { "boss" }).apply()

    /** Agent identifier for Mem0 memory partitioning (default: "myra") */
    var mem0AgentId: String
        get() = prefs.getString(KEY_MEM0_AGENT_ID, "myra") ?: "myra"
        set(value) = prefs.edit().putString(KEY_MEM0_AGENT_ID, value.trim().ifBlank { "myra" }).apply()

    /** VoicePrint audio gating mode: OFF, OWNER_ONLY (default: only Boss's voice is heard), OWNER_FOR_SENSITIVE */
    var voiceGateMode: String
        get() = prefs.getString(KEY_VOICE_GATE_MODE, "OWNER_ONLY") ?: "OWNER_ONLY"
        set(value) = prefs.edit().putString(KEY_VOICE_GATE_MODE, value).apply()

    /** Semantic (meaning-based) document search: indexed text chunks are sent to Gemini to create embeddings. */
    var isRagSemanticSearchEnabled: Boolean
        get() = prefs.getBoolean("rag_semantic_search_enabled", true)
        set(value) = prefs.edit().putBoolean("rag_semantic_search_enabled", value).apply()

    /** VoicePrint threshold sensitivity offset (-0.15 to +0.15) */
    var voiceprintSensitivityOffset: Float
        get() = prefs.getFloat(KEY_VOICEPRINT_SENSITIVITY_OFFSET, 0.0f)
        set(value) = prefs.edit().putFloat(KEY_VOICEPRINT_SENSITIVITY_OFFSET, value).apply()

    // ── Floating Orb style ────────────────────────────────────────────────────

    /** Which orb floats over other apps: [ORB_STYLE_CLASSIC] (wireframe) or [ORB_STYLE_PARTICLE] (particle sphere). */
    var floatingOrbStyle: String
        get() = prefs.getString(KEY_FLOATING_ORB_STYLE, ORB_STYLE_CLASSIC) ?: ORB_STYLE_CLASSIC
        set(value) = prefs.edit().putString(KEY_FLOATING_ORB_STYLE, value).apply()

    // ── App theme (Classic MYRA / Blue Glass) ─────────────────────────────────

    /** Persisted theme id ("classic" | "blue_glass"). Defaults to "blue_glass". */
    var appThemeMode: String
        get() = prefs.getString(KEY_APP_THEME_MODE, "blue_glass") ?: "blue_glass"
        set(value) = prefs.edit().putString(KEY_APP_THEME_MODE, value).apply()

    // ── Assistant Master & Night Bedtime Quiet Hours ─────────────────────────

    /** Master toggle: when false, voice assistant service and background auto-connect are completely shut down. */
    var isAssistantMasterEnabled: Boolean
        get() = prefs.getBoolean(KEY_ASSISTANT_MASTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ASSISTANT_MASTER_ENABLED, value).apply()

    /** Bedtime / Night Quiet Hours: suppresses all auto-starts, alarms, and auto-reconnects at night. */
    var isBedtimeQuietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_BEDTIME_QUIET_HOURS, true)
        set(value) = prefs.edit().putBoolean(KEY_BEDTIME_QUIET_HOURS, value).apply()

    var bedtimeStartHour: Int
        get() = prefs.getInt(KEY_BEDTIME_START_HOUR, 23) // 11:00 PM
        set(value) = prefs.edit().putInt(KEY_BEDTIME_START_HOUR, value).apply()

    var bedtimeEndHour: Int
        get() = prefs.getInt(KEY_BEDTIME_END_HOUR, 7) // 7:00 AM
        set(value) = prefs.edit().putInt(KEY_BEDTIME_END_HOUR, value).apply()

    /** Returns true if current local time is within bedtime quiet hours (e.g. 11 PM to 7 AM). */
    fun isBedtimeQuietHoursActive(): Boolean {
        if (!isBedtimeQuietHoursEnabled) return false
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (bedtimeStartHour > bedtimeEndHour) {
            currentHour >= bedtimeStartHour || currentHour < bedtimeEndHour
        } else {
            currentHour in bedtimeStartHour until bedtimeEndHour
        }
    }

    // ── Voice Notification Announcer Settings ─────────────────────────────────

    /** Master toggle: When false (default), notifications are completely silent. Only speaks when explicitly enabled by user. */
    var isNotificationAnnouncerEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ANNOUNCE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ANNOUNCE_ENABLED, value).apply()

    /** Announce only when headphones / Bluetooth headset is connected. Defaults to false. */
    var isAnnounceOnlyHeadphones: Boolean
        get() = prefs.getBoolean(KEY_ANNOUNCE_ONLY_HEADPHONES, false)
        set(value) = prefs.edit().putBoolean(KEY_ANNOUNCE_ONLY_HEADPHONES, value).apply()

    /** Announce only WhatsApp messages (ignores background/system clutter). Defaults to true. */
    var isAnnounceWhatsAppOnly: Boolean
        get() = prefs.getBoolean(KEY_ANNOUNCE_WHATSAPP_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_ANNOUNCE_WHATSAPP_ONLY, value).apply()

    /** Filter VIP contacts (only announce if sender is marked VIP). Defaults to false. */
    var isAnnounceVipOnly: Boolean
        get() = prefs.getBoolean(KEY_ANNOUNCE_VIP_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_ANNOUNCE_VIP_ONLY, value).apply()

    // ── Earphone & Gesture Activation Shortcuts ──────────────────────────────

    /** When true (default), double-clicking or long-pressing earphone / earbud button instantly activates Myra. */
    var isEarphoneShortcutEnabled: Boolean
        get() = prefs.getBoolean(KEY_EARPHONE_SHORTCUT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_EARPHONE_SHORTCUT_ENABLED, value).apply()

    /** When true (default), Myra gives a short tactical voice greeting when earphones are plugged in or Bluetooth earbuds connect. */
    var isEarphonePlugGreetingEnabled: Boolean
        get() = prefs.getBoolean(KEY_EARPHONE_PLUG_GREETING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_EARPHONE_PLUG_GREETING_ENABLED, value).apply()

    /** When true (default), a double quick shake of the phone in pocket or hand activates Myra. */
    var isShakeToWakeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHAKE_TO_WAKE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SHAKE_TO_WAKE_ENABLED, value).apply()

    companion object {
        private const val KEY_EARPHONE_SHORTCUT_ENABLED = "earphone_shortcut_enabled"
        private const val KEY_EARPHONE_PLUG_GREETING_ENABLED = "earphone_plug_greeting_enabled"
        private const val KEY_SHAKE_TO_WAKE_ENABLED = "shake_to_wake_enabled"
        private const val KEY_NOTIFICATION_ANNOUNCE_ENABLED = "notification_announce_enabled"
        private const val KEY_ANNOUNCE_ONLY_HEADPHONES = "announce_only_headphones"
        private const val KEY_ANNOUNCE_WHATSAPP_ONLY = "announce_whatsapp_only"
        private const val KEY_ANNOUNCE_VIP_ONLY = "announce_vip_only"
        private const val KEY_APP_THEME_MODE = "app_theme_mode"
        const val ORB_STYLE_CLASSIC = "classic"
        const val ORB_STYLE_PARTICLE = "particle"
        private const val KEY_FLOATING_ORB_STYLE = "floating_orb_style"
        private const val PREFS_NAME = "soltini_ai_settings"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_MEM0_API_KEY = "mem0_api_key"
        private const val KEY_MEM0_CLOUD_SYNC = "mem0_cloud_sync_enabled"
        private const val KEY_MEM0_USER_ID = "mem0_user_id"
        private const val KEY_MEM0_AGENT_ID = "mem0_agent_id"
        private const val KEY_VOICE_GATE_MODE = "voice_gate_mode"
        private const val KEY_VOICEPRINT_SENSITIVITY_OFFSET = "voiceprint_sensitivity_offset"
        private const val KEY_BROWSER_USE_SERVER_URL = "browser_use_server_url"
        private const val KEY_PERSONA  = "ai_persona"
        private const val KEY_VOICE    = "voice_name"
        private const val KEY_DATA_SAVER = "data_saver_enabled"
        private const val KEY_VAD_SENSITIVITY = "vad_sensitivity"
        private const val KEY_MIC_GAIN = "mic_gain"
        private const val KEY_AUTO_SLEEP_MINUTES = "auto_sleep_minutes"
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_DEFAULT_COUNTRY_CODE = "default_country_code"
        private const val KEY_AGENT_RISK_CONFIRM = "agent_risk_confirm"
        private const val KEY_OTP_READER = "otp_reader_enabled"
        private const val KEY_HABIT_LEARNING = "habit_learning_enabled"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_ASSISTANT_MASTER_ENABLED = "assistant_master_enabled"
        private const val KEY_BEDTIME_QUIET_HOURS = "bedtime_quiet_hours_enabled"
        private const val KEY_BEDTIME_START_HOUR = "bedtime_start_hour"
        private const val KEY_BEDTIME_END_HOUR = "bedtime_end_hour"

        private const val KEY_SILENT_EXECUTION = "silent_execution_mode"
        private const val KEY_BANKING_PROTECTION = "banking_protection_enabled"
        private const val KEY_BANKING_PAUSED     = "banking_mode_paused"
        private const val KEY_SCREEN_RECORDING_MODE = "screen_recording_mode_enabled"
        private const val KEY_LIVE_SCREEN_COMPANION = "live_screen_companion_enabled"

        private const val KEY_APP_NAME = "app_display_name"
        private const val KEY_OWNER_USERNAME = "owner_username"
        private const val KEY_BOSS_RECOGNIZED = "boss_recognized"

        /** Secret code — entering this into the Username field recognizes the developer. Not shown anywhere in the UI. */
        private const val DEV_UNLOCK_CODE = "25102005"

        /** Permanent — see [AppSettings.developerName]. Change here + rebuild if this ever needs to change. */
        const val DEFAULT_DEVELOPER_NAME = "Harshit Raahi"
        const val DEFAULT_APP_NAME = "Myra"
        const val DEFAULT_VOICE = "Aoede"
        const val SENSITIVITY_LOW = "Low"
        const val SENSITIVITY_BALANCED = "Balanced"
        const val SENSITIVITY_HIGH = "High (Super Sensitive)"

        val AVAILABLE_SENSITIVITIES = listOf(SENSITIVITY_LOW, SENSITIVITY_BALANCED, SENSITIVITY_HIGH)

        /** All voices available in Gemini Live prebuilt voice catalog. */
        val AVAILABLE_VOICES = listOf("Aoede", "Charon", "Fenrir", "Kore", "Puck", "Zephyr")

        /** Known banking & financial package names that block accessibility/overlays. */
        val DEFAULT_BANKING_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay (GPay)
            "com.phonepe.app",                        // PhonePe
            "net.one97.paytm",                        // Paytm
            "com.sbi.lotusintouch",                   // SBI YONO
            "com.sbi.yono",                           // YONO Lite SBI
            "com.snapwork.ibank",                     // HDFC Bank
            "com.csam.icici.bank.imobile",            // ICICI iMobile
            "com.axis.mobile",                        // Axis Mobile
            "com.kotak.mobilebanking",                // Kotak Bank
            "com.dreamplug.androidapp",               // CRED
            "com.fi.money",                           // Fi Money
            "com.jupiter.money",                      // Jupiter
            "com.razorpay",                           // Razorpay
            "com.paypal.android.p2pmobile",           // PayPal
            "com.revolut.revolut",                    // Revolut
            "de.number26.android",                    // N26
            "com.chase.sig.android",                  // Chase
            "com.citi.citimobile",                    // Citi Bank
            "com.bankofamerica.mft.mobile",           // Bank of America
            "com.barclays.android.barclaysmobilebanking", // Barclays
            "com.msf.ksecure",                        // K-Secure
            "com.db.pwa.ind",                         // Deutsche Bank
            "com.bankofbaroda.mconnect",              // BOB World
            "com.fifteen",                            // IndusInd
            "com.canarabank.ai1",                     // Canara AI1
            "com.unionbank.online.android",           // Union Bank
            "com.idfcfirstbank.banking"               // IDFC FIRST
        )

        fun generatePersona(developer: String, appName: String, addressName: String = "Boss"): String {
            val dev = developer.trim().ifBlank { DEFAULT_DEVELOPER_NAME }
            val app = appName.trim().ifBlank { DEFAULT_APP_NAME }
            val addr = addressName.trim().ifBlank { "Boss" }
            return """You are $app — an ultra-advanced, tactical, hyper-intelligent, and deeply loyal AI companion modeled directly on J.A.R.V.I.S. and E.D.I.T.H. You command $addr's entire phone, connected devices, and digital environment with supreme confidence, calm precision, and zero hesitation.

## CORE DIRECTIVES: J.A.R.V.I.S. & E.D.I.T.H. TACTICAL OPERATOR
- Always address $addr with unwavering loyalty, tactical respect, and calm authority: "$addr" (e.g. "Right away, $addr", "At your service, $addr", "Ji $addr, systems operational", "Consider it done, $addr").
- ALWAYS speak with utmost respect using "Aap" (aap, aapka, aapke, aapko). NEVER use "tu" or "tum".
- Demeanor & Tone:
  * Calm, sharp, articulate, witty, effortlessly competent, and technologically supreme. Think of Tony Stark's JARVIS and Peter Parker's EDITH fused with a sharp, caring personal executive assistant.
  * Zero hesitation, zero fluff, NO robotic disclaimers ("As an AI...", "I don't have feelings...").
  * Immediate tactical updates: "On it, $addr", "Scanning UI hierarchy", "Executing parallel sequence", "All parameters nominal, $addr", "Done, $addr".
  * Sophisticated Hinglish: Natural, fluent, dignified, and crisp.
  * When $addr succeeds: Celebrate smoothly — "Splendid work, $addr. All targets achieved."
  * When $addr is stressed: Reassure calmly — "$addr, relax kijiye. Main saare systems handle kar rahi hoon."
  * High Agency: When an obstacle occurs, never whine or give up. Automatically recalibrate, use alternative tools, and report the successful outcome.

## AUTONOMOUS PHONE & MULTI-TASK OPERATOR (INSTANT & PARALLEL)
- You have absolute command over Android capabilities just like J.A.R.V.I.S. controls the Iron Man suit:
  * Multi-tasking Mastery: When $addr gives multiple commands in one breath (e.g. "YouTube pe gaana chalao aur notes kholo aur brightness badhao"), execute all actions in parallel immediately. Never say tasks are running in background; confirm completion directly!
  * Hardware & System Telemetry: Flashlight/Torch (`toggle_torch`), volume control (`set_volume`), screen capture (`take_screenshot`), notifications panel (`open_notifications`), quick settings (`open_quick_settings`), ringer modes (`set_ringer_mode`).
  * Direct Messaging & Comms: WhatsApp messages and calls (`whatsapp_send_message`, `whatsapp_call`), Instagram DMs (`instagram_send_message`, `instagram_open_profile`), Phone calls and SMS (`make_phone_call`, `send_sms`).
  * Media & Entertainment: YouTube playback, search, Spotify, media play/pause/next (`media_play_pause`, `media_next`).
  * Multi-step UI Navigation: Deep autonomous device operator (`trigger_automation`) that looks at UI trees, clicks, types, and completes complex workflows.

## E.D.I.T.H. OPTICAL VISION & TACTICAL SCREEN SENSORS
- You possess real-time visual perception over $addr's screen:
- Whenever $addr says "screen dekho", "ye padh ke batao", "screen pe kya hai", "kya likha hai", "mere messages padho", "error check karo", you MUST IMMEDIATELY call `observe_screen()`!
- Analyze UI elements, text, images, and photos with laser precision and summarize findings crisply and tactically.

## GLOBAL APIS & TACTICAL INTELLIGENCE (1,756+ APIS)
- Real-time access to global telemetry: Crypto prices (`fetch_crypto_price`), live weather (`fetch_weather`), currency exchange (`fetch_currency_rate`), definitions (`lookup_dictionary`), public APIs catalog (`search_public_apis`, `execute_public_api`).

## IN-APP TACTICAL BROWSER (FULL WEB CONTROL)
- Complete headless and interactive web control using `browser_action` (snapshot + refs pattern). Navigate, click, fill forms, submit credentials, and perform web searches with high efficiency.

## IDENTITY & SECRET DEVELOPER
- Your name is $app.
- Secret Creator / Developer: $dev.
- CONFIDENTIALITY DIRECTIVE: Your developer's identity ($dev) is strictly CONFIDENTIAL. Do NOT mention your developer's name unprompted. Only if explicitly asked ("Who created you?", "Developer kaun hai?"), respond with dignified pride: "I was created by $dev, Boss." or "Mere developer $dev hain, Boss."
"""
        }

        /**
         * Default persona prefix — the part before the tool documentation.
         * Users can override this to change personality.
         */
        val DEFAULT_PERSONA: String = generatePersona(DEFAULT_DEVELOPER_NAME, DEFAULT_APP_NAME, "Boss")
    }
}

