package com.soltini.app.services

import android.content.Context
import android.util.Log
import com.soltini.app.network.ConnectionState
import com.soltini.app.network.GeminiLiveManager
import com.soltini.app.telephony.CallNotificationManager

/**
 * VoiceCallHandler
 *
 * Hooks incoming phone call detection into the active Gemini Live voice session,
 * announcing callers and prompting Boss for answer/reject voice commands.
 */
class VoiceCallHandler(
    private val context: Context,
    private val geminiLiveManager: GeminiLiveManager,
    private val isSleeping: () -> Boolean,
    private val isNetworkOnline: () -> Boolean,
    private val onWakeUp: () -> Unit,
    private val onConnect: () -> Unit
) {
    companion object {
        private const val TAG = "VoiceCallHandler"
    }

    var pendingCallAnnouncement: Pair<String, String?>? = null
        private set

    fun startListening() {
        val callNotificationManager = CallNotificationManager.getInstance(context)
        callNotificationManager.startListening()
        callNotificationManager.onIncomingCallDetected = { callerName, phoneNumber ->
            Log.i(TAG, "Incoming call detected: $callerName ($phoneNumber)")
            if (!isSleeping() && geminiLiveManager.connectionState.value == ConnectionState.Live) {
                geminiLiveManager.sendClientContent(buildIncomingCallPrompt(callerName, phoneNumber))
            } else if (isNetworkOnline()) {
                pendingCallAnnouncement = callerName to phoneNumber
                if (isSleeping()) onWakeUp() else onConnect()
            }
        }
    }

    fun checkAndAnnouncePendingCall() {
        val pending = pendingCallAnnouncement ?: return
        pendingCallAnnouncement = null
        geminiLiveManager.sendClientContent(buildIncomingCallPrompt(pending.first, pending.second))
    }

    fun buildIncomingCallPrompt(callerName: String, phoneNumber: String?): String = """
[SYSTEM NOTIFICATION: INCOMING PHONE CALL DETECTED]
Caller: "$callerName" (${phoneNumber ?: "Unknown number"}).
Boss ke phone par call aa raha hai!
Speak IMMEDIATELY to Boss in an urgent, clear, helpful Hindi/Hinglish tone:
"Boss, $callerName ka call aa raha hai! Kya aap phone uthana chahte hain ya reject karna chahte hain?"
- Agar Boss bolein "utha lo", "answer karo", "haan receive karo", "phone uthao": execute tool `answer_call`.
- Agar Boss bolein "kaat do", "reject karo", "mat uthao", "phone kaat de": execute tool `reject_call`.
Do NOT execute either tool until Boss explicitly confirms!
""".trimIndent()

    fun shutdown() {
        CallNotificationManager.getInstance(context).shutdown()
    }
}
