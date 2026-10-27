package com.soltini.app.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.audio.JarvisSoundEngine
import com.soltini.app.telephony.TtsSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class SentryStatus(
    val isArmed: Boolean = false,
    val isTriggered: Boolean = false,
    val breachCount: Int = 0,
    val lastBreachTime: Long = 0L,
    val statusText: String = "DISARMED"
)

/**
 * JarvisSentryEngine — Iron Man Sentry Security Protocol
 *
 * Real-time accelerometer & motion tripwire defense system.
 * When armed, monitors physical device movement. If unauthorized motion or pickup
 * is detected, triggers procedural intrusion sirens, tactical vibration alerts,
 * and vocal warnings.
 */
class JarvisSentryEngine private constructor(private val appContext: Context) : SensorEventListener {

    companion object {
        private const val TAG = "JarvisSentryEngine"
        private const val MOTION_THRESHOLD = 3.2f // Delta acceleration in m/s²
        private const val COOLDOWN_MS = 4000L // Min interval between alarms

        @Volatile
        private var instance: JarvisSentryEngine? = null

        fun getInstance(context: Context): JarvisSentryEngine =
            instance ?: synchronized(this) {
                instance ?: JarvisSentryEngine(context.applicationContext).also { instance = it }
            }
    }

    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val ttsSpeaker = TtsSpeaker(appContext)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _sentryStatus = MutableStateFlow(SentryStatus())
    val sentryStatus: StateFlow<SentryStatus> = _sentryStatus.asStateFlow()

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var hasBaseline = false
    private var lastBreachTimestamp = 0L

    /**
     * Arm Sentry Mode tripwire.
     */
    fun armSentry(vocalFeedback: Boolean = true): String {
        if (_sentryStatus.value.isArmed) {
            return "Sentry Mode is already active and monitoring physical perimeter, Sir."
        }

        hasBaseline = false
        val registered = accelerometer != null && sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        ) == true

        if (!registered) {
            Log.w(TAG, "Hardware accelerometer unavailable for Sentry Mode")
            return "Hardware sensor unavailable. Could not arm Sentry Mode."
        }

        _sentryStatus.value = SentryStatus(
            isArmed = true,
            isTriggered = false,
            breachCount = 0,
            statusText = "ARMED & MONITORING"
        )

        JarvisSoundEngine.playArcReactorHum()
        AgentStatusBus.emit("🛡️ SENTRY ARMED: Motion Tripwire Active")

        if (vocalFeedback) {
            ttsSpeaker.speak("Sentry mode armed. Motion tripwire active, Sir.")
        }
        return "Sentry Mode activated. Motion tripwire is now guarding the device, Sir."
    }

    /**
     * Disarm Sentry Mode.
     */
    fun disarmSentry(vocalFeedback: Boolean = true): String {
        if (!_sentryStatus.value.isArmed) {
            return "Sentry Mode is currently disarmed, Sir."
        }

        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering sensor: ${e.message}")
        }

        _sentryStatus.value = SentryStatus(
            isArmed = false,
            isTriggered = false,
            statusText = "DISARMED"
        )

        JarvisSoundEngine.playTacticalBlip()
        AgentStatusBus.emit("SENTRY DISARMED: Standby Mode")

        if (vocalFeedback) {
            ttsSpeaker.speak("Sentry mode disarmed. All perimeter sensors clear, Sir.")
        }
        return "Sentry Mode disarmed, Sir. Normal operations resumed."
    }

    /**
     * Toggle current Sentry Mode state.
     */
    fun toggleSentry(): String {
        return if (_sentryStatus.value.isArmed) {
            disarmSentry(vocalFeedback = true)
        } else {
            armSentry(vocalFeedback = true)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_sentryStatus.value.isArmed) return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (!hasBaseline) {
            lastX = x
            lastY = y
            lastZ = z
            hasBaseline = true
            return
        }

        val deltaX = x - lastX
        val deltaY = y - lastY
        val deltaZ = z - lastZ
        val delta = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

        lastX = x
        lastY = y
        lastZ = z

        val now = System.currentTimeMillis()
        if (delta > MOTION_THRESHOLD && (now - lastBreachTimestamp > COOLDOWN_MS)) {
            lastBreachTimestamp = now
            triggerSentryBreach(delta)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerSentryBreach(delta: Float) {
        val newBreachCount = _sentryStatus.value.breachCount + 1
        Log.w(TAG, "SENTRY BREACH #$newBreachCount! Motion delta: $delta")

        _sentryStatus.value = _sentryStatus.value.copy(
            isTriggered = true,
            breachCount = newBreachCount,
            lastBreachTime = System.currentTimeMillis(),
            statusText = "BREACH DETECTED!"
        )

        // 1. Procedural High-Tech Siren
        JarvisSoundEngine.playSentryIntrusionAlarm()

        // 2. Tactical Haptic Pulse
        triggerTacticalVibration()

        // 3. Visual HUD Bus Alert
        AgentStatusBus.emit("🚨 SENTRY ALERT: Unauthorized motion detected! (Delta: ${String.format("%.1f", delta)} m/s²)")

        // 4. Voice Warning
        scope.launch {
            delay(200)
            ttsSpeaker.speak("Security alert! Unauthorized device movement detected. Sentry protocol breached, Sir.")
        }
    }

    private fun triggerTacticalVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 150, 100, 150, 100, 300)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(500)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }
}
