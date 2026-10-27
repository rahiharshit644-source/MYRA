package com.soltini.app.cache

import android.content.Context
import android.util.Log
import com.soltini.app.agent.DeviceHardwareController
import com.soltini.app.audio.JarvisSoundEngine
import com.soltini.app.briefing.JarvisExecutiveBriefing
import com.soltini.app.security.JarvisSentryEngine
import com.soltini.app.telephony.TtsSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JarvisSemanticCache — Ultra-Fast (<50ms) Local Intent Execution Engine
 *
 * Inspired by GPTCache and local edge neural dispatch. Intercepts frequent high-priority
 * hardware, security, and telemetry commands locally in English and Hinglish without
 * waiting for cloud LLM roundtrips.
 */
class JarvisSemanticCache private constructor(private val context: Context) {

    companion object {
        private const val TAG = "JarvisSemanticCache"

        @Volatile
        private var instance: JarvisSemanticCache? = null

        fun getInstance(context: Context): JarvisSemanticCache =
            instance ?: synchronized(this) {
                instance ?: JarvisSemanticCache(context.applicationContext).also { instance = it }
            }
    }

    private val deviceHardware = DeviceHardwareController(context)
    private val sentryEngine = JarvisSentryEngine.getInstance(context)
    private val ttsSpeaker = TtsSpeaker(context)

    sealed class CacheResult {
        data class Hit(val intent: String, val message: String) : CacheResult()
        object Miss : CacheResult()
    }

    /**
     * Attempts to resolve and execute intent locally.
     * Returns Hit if handled in <50ms, or Miss if cloud LLM orchestration is required.
     */
    suspend fun tryExecuteLocally(userInput: String): CacheResult = withContext(Dispatchers.IO) {
        val q = userInput.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return@withContext CacheResult.Miss

        try {
            // 1. Torch / Flashlight
            if (matchesAny(q, listOf("torch on", "torch jalao", "flashlight on", "light on", "torch chalu", "light jalao", "turn on flashlight", "turn on torch"))) {
                JarvisSoundEngine.playTacticalBlip()
                deviceHardware.setTorch(true)
                val resp = "Flashlight turned on, Sir."
                ttsSpeaker.speak(resp)
                return@withContext CacheResult.Hit("TORCH_ON", resp)
            }
            if (matchesAny(q, listOf("torch off", "torch band", "flashlight off", "light off", "light band", "turn off flashlight", "turn off torch"))) {
                JarvisSoundEngine.playTacticalBlip()
                deviceHardware.setTorch(false)
                val resp = "Flashlight turned off, Sir."
                ttsSpeaker.speak(resp)
                return@withContext CacheResult.Hit("TORCH_OFF", resp)
            }

            // 2. Sentry Mode
            if (matchesAny(q, listOf("arm sentry", "sentry on", "sentry mode on", "activate sentry", "suraksha on", "sentry arm", "guard mode on"))) {
                val resp = sentryEngine.armSentry(vocalFeedback = true)
                return@withContext CacheResult.Hit("SENTRY_ARM", resp)
            }
            if (matchesAny(q, listOf("disarm sentry", "sentry off", "sentry mode off", "deactivate sentry", "suraksha band", "sentry disarm"))) {
                val resp = sentryEngine.disarmSentry(vocalFeedback = true)
                return@withContext CacheResult.Hit("SENTRY_DISARM", resp)
            }
            if (matchesAny(q, listOf("sentry status", "sentry report", "is sentry armed", "sentry ka status"))) {
                val st = sentryEngine.sentryStatus.value
                val resp = "Sentry status is ${st.statusText}, with ${st.breachCount} logged breaches, Sir."
                ttsSpeaker.speak(resp)
                return@withContext CacheResult.Hit("SENTRY_STATUS", resp)
            }

            // 3. Executive Telemetry Briefing
            if (matchesAny(q, listOf("executive briefing", "vitals briefing", "jarvis brief me", "telemetry report", "morning briefing", "system briefing", "status report jarvis"))) {
                JarvisSoundEngine.playArcReactorHum()
                val report = JarvisExecutiveBriefing.generateComprehensiveBriefing(context)
                ttsSpeaker.speak(report)
                return@withContext CacheResult.Hit("EXECUTIVE_BRIEFING", report)
            }

            // 4. Battery / Vitals quick check
            if (matchesAny(q, listOf("battery kitna hai", "battery kitni hai", "battery percent", "battery status", "phone battery", "battery level"))) {
                JarvisSoundEngine.playTacticalBlip()
                val vitals = JarvisExecutiveBriefing.generateDeviceVitalsReport(context)
                ttsSpeaker.speak(vitals)
                return@withContext CacheResult.Hit("BATTERY_CHECK", vitals)
            }

            // 5. Time & Date
            if (matchesAny(q, listOf("kya time ho raha hai", "time kya hai", "what time is it", "current time", "samay kya hua"))) {
                JarvisSoundEngine.playTacticalBlip()
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                val resp = "The time is currently $timeStr, Sir."
                ttsSpeaker.speak(resp)
                return@withContext CacheResult.Hit("CURRENT_TIME", resp)
            }
            if (matchesAny(q, listOf("aaj konsi date hai", "aaj ki date", "what is today's date", "today date", "aaj kaun sa din hai"))) {
                JarvisSoundEngine.playTacticalBlip()
                val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
                val resp = "Today is $dateStr, Sir."
                ttsSpeaker.speak(resp)
                return@withContext CacheResult.Hit("CURRENT_DATE", resp)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Semantic cache execution error: ${e.message}")
        }

        return@withContext CacheResult.Miss
    }

    private fun matchesAny(query: String, patterns: List<String>): Boolean {
        return patterns.any { pat -> query.contains(pat) }
    }
}
