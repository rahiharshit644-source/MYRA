package com.soltini.app.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.audio.JarvisSoundEngine
import com.soltini.app.services.BackgroundVoiceService
import com.soltini.app.settings.AppSettings
import kotlin.math.sqrt

/**
 * PocketGestureTrigger — Shake-to-Wake & Pocket Motion Trigger
 *
 * Detects an intentional double-shake or quick pocket double-tap using Android Accelerometer:
 *  - Two directional acceleration spikes (>2.2g) within 380ms.
 *  - Has a 2.5 second cooldown to prevent false triggers during walking / running.
 *  - Instantly wakes Myra with Arc Reactor spool-up chime + haptic pulse!
 */
class PocketGestureTrigger(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "PocketGestureTrigger"
        private const val SHAKE_THRESHOLD_G = 2.2f
        private const val DOUBLE_SHAKE_WINDOW_MS = 380L
        private const val COOLDOWN_MS = 2500L
    }

    private val appSettings = AppSettings(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isListening = false
    private var lastShakeTime = 0L
    private var shakeCount = 0
    private var lastTriggerTime = 0L

    fun start() {
        if (!appSettings.isShakeToWakeEnabled) return
        if (isListening) return

        try {
            val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
                isListening = true
                Log.i(TAG, "PocketGestureTrigger active (Shake to wake registered)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting PocketGestureTrigger: ${e.message}")
        }
    }

    fun stop() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
            isListening = false
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !appSettings.isShakeToWakeEnabled) return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate total acceleration in G's (normal Earth gravity = 1.0G)
        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()

        if (now - lastTriggerTime < COOLDOWN_MS) return

        if (gForce > SHAKE_THRESHOLD_G) {
            if (now - lastShakeTime < DOUBLE_SHAKE_WINDOW_MS) {
                shakeCount++
                if (shakeCount >= 2) {
                    // Double shake detected!
                    shakeCount = 0
                    lastTriggerTime = now
                    triggerWake()
                }
            } else {
                shakeCount = 1
                lastShakeTime = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerWake() {
        Log.i(TAG, "Double-shake gesture recognized -> waking Myra")
        mainHandler.post {
            JarvisSoundEngine.playArcReactorHum()
            triggerHaptic()
            AgentStatusBus.emit("📱 Pocket Shake Trigger: Myra Activated")

            val service = BackgroundVoiceService.getInstance()
            if (service != null) {
                if (service.isSleeping.value) {
                    service.wakeUp()
                } else if (service.connectionState.value == com.soltini.app.network.ConnectionState.Disconnected) {
                    service.connect()
                }
            }
        }
    }

    private fun triggerHaptic() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(75)
            }
        } catch (_: Exception) {}
    }
}
