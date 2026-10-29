package com.soltini.app.copilot

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.audio.GeminiNeuralVoiceSynthesizer
import com.soltini.app.audio.JarvisSoundEngine
import com.soltini.app.notifications.NotificationRepository
import com.soltini.app.settings.AppSettings
import com.soltini.app.telephony.CallNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CoPilotState(
    val isActive: Boolean = false,
    val connectedDeviceName: String? = null,
    val isAutoReplyEnabled: Boolean = true,
    val autoReplyMessage: String = "Boss is currently driving. Will call you back once arrived safely.",
    val lastInterceptedCaller: String? = null
)

/**
 * DrivingCoPilotEngine — Autonomous Driving & Bike Co-Pilot Mode
 *
 * Automatically detects or manually engages when Boss is driving / riding:
 *  - Detects Bluetooth Car Infotainment, Helmet Intercom (Sena, Cardo), or A2DP connections.
 *  - Routes all speech through Car/Helmet audio.
 *  - Hands-Free Autonomous Secretary:
 *      Incoming call -> "Boss, [Name] ka call hai. Uthau ya bol du aap drive kar rahe ho?"
 *      Boss: "Drive kar raha hu bol do" -> Auto-rejects + dispatches driving SMS/WhatsApp!
 *      Boss: "Uthao" -> Hands-free answer.
 */
class DrivingCoPilotEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "DrivingCoPilot"

        @Volatile
        private var instance: DrivingCoPilotEngine? = null

        fun getInstance(context: Context): DrivingCoPilotEngine =
            instance ?: synchronized(this) {
                instance ?: DrivingCoPilotEngine(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val appSettings = AppSettings(appContext)
    private val geminiSpeaker = GeminiNeuralVoiceSynthesizer.getInstance(appContext)
    private val callManager = CallNotificationManager.getInstance(appContext)
    private val notifRepo = NotificationRepository.getInstance(appContext)

    private val _coPilotState = MutableStateFlow(CoPilotState())
    val coPilotState: StateFlow<CoPilotState> = _coPilotState.asStateFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val name = device?.name ?: "Bluetooth Device"
                checkAndEngageCoPilot(name)
            } else if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                if (_coPilotState.value.isActive) {
                    disengageCoPilot("Bluetooth disconnected")
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            appContext.registerReceiver(bluetoothReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth receiver registration note: ${e.message}")
        }
    }

    private fun checkAndEngageCoPilot(deviceName: String) {
        val lower = deviceName.lowercase()
        val isCarOrHelmet = lower.contains("car") ||
                            lower.contains("auto") ||
                            lower.contains("sync") ||
                            lower.contains("bmw") ||
                            lower.contains("audi") ||
                            lower.contains("honda") ||
                            lower.contains("suzuki") ||
                            lower.contains("hyundai") ||
                            lower.contains("infotainment") ||
                            lower.contains("helmet") ||
                            lower.contains("sena") ||
                            lower.contains("cardo") ||
                            lower.contains("intercom")

        if (isCarOrHelmet) {
            engageCoPilot("Connected to $deviceName")
        }
    }

    /**
     * Toggles Co-Pilot Driving Mode manually or via voice command.
     */
    fun toggleCoPilot(force: Boolean? = null): Boolean {
        val next = force ?: !_coPilotState.value.isActive
        if (next) {
            engageCoPilot("Manual Activation")
        } else {
            disengageCoPilot("Manual Deactivation")
        }
        return next
    }

    fun engageCoPilot(reason: String) {
        if (_coPilotState.value.isActive) return
        _coPilotState.value = _coPilotState.value.copy(
            isActive = true,
            connectedDeviceName = reason
        )

        // Automatically ensure Voice Announcer is active for driving safety
        appSettings.isNotificationAnnouncerEnabled = true

        JarvisSoundEngine.playArcReactorHum()
        val speech = "Driving Co-Pilot Mode engaged, Boss. Saare incoming calls aur messages hands-free handle honge."
        geminiSpeaker.speakWithGeminiVoice(speech)
        AgentStatusBus.emit("🏍️ Tactical Co-Pilot ENGAGED ($reason)")
    }

    fun disengageCoPilot(reason: String) {
        if (!_coPilotState.value.isActive) return
        _coPilotState.value = _coPilotState.value.copy(
            isActive = false,
            connectedDeviceName = null
        )

        JarvisSoundEngine.playTacticalBlip()
        val speech = "Co-Pilot mode stand down. All parameters nominal, Boss."
        geminiSpeaker.speakWithGeminiVoice(speech)
        AgentStatusBus.emit("🛡️ Co-Pilot Stand Down ($reason)")
    }

    /**
     * Intercepts incoming call specifically during Co-Pilot driving mode.
     */
    fun handleIncomingCallWhileDriving(callerName: String, phoneNumber: String) {
        if (!_coPilotState.value.isActive) return

        _coPilotState.value = _coPilotState.value.copy(lastInterceptedCaller = callerName)

        val prompt = "Boss, $callerName ka call aa raha hai. Uthau ya bol du aap drive kar rahe ho?"
        geminiSpeaker.speakWithGeminiVoice(prompt)
        AgentStatusBus.emit("🚗 Driving Call Alert: from $callerName")
    }

    /**
     * Dispatches driving auto-reply when user commands "Bol do drive kar raha hu" or cuts call.
     */
    fun dispatchDrivingAutoReply(recipient: String) {
        val replyText = _coPilotState.value.autoReplyMessage
        scope.launch {
            try {
                // 1. Try WhatsApp / Notification direct reply
                val notifRes = notifRepo.replyToNotification(recipient, replyText)
                if (notifRes is com.soltini.app.notifications.ReplyResult.Success) {
                    JarvisSoundEngine.playTaskSuccessChime()
                    geminiSpeaker.speakWithGeminiVoice("$recipient ko auto-reply bhej diya hai, Boss.")
                    AgentStatusBus.emit("✅ Co-Pilot Auto-Reply Sent to $recipient: \"$replyText\"")
                    return@launch
                }

                // 2. Try direct SMS if phone number available
                val cleanNumber = recipient.filter { it.isDigit() || it == '+' }
                if (cleanNumber.isNotBlank()) {
                    com.soltini.app.telephony.SmsSender(appContext).sendSms(cleanNumber, replyText)
                    JarvisSoundEngine.playTaskSuccessChime()
                    geminiSpeaker.speakWithGeminiVoice("$recipient ko SMS bhej diya hai, Boss.")
                    AgentStatusBus.emit("✅ Co-Pilot SMS Auto-Reply Sent to $recipient")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch driving auto-reply: ${e.message}", e)
            }
        }
    }
}
