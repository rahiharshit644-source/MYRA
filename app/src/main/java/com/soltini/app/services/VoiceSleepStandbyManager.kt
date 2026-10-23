package com.soltini.app.services

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.soltini.app.audio.AudioPlayer
import com.soltini.app.audio.AudioRecorder
import com.soltini.app.audio.HeadsetAudioRouter
import com.soltini.app.audio.MyraSoundEffects
import com.soltini.app.audio.WakeWordEngine
import com.soltini.app.network.ConnectionState
import com.soltini.app.network.GeminiLiveManager
import com.soltini.app.overlay.OverlayService
import com.soltini.app.overlay.RingState
import com.soltini.app.settings.AppSettings
import com.soltini.app.voiceprint.gate.VoiceGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * VoiceSleepStandbyManager
 *
 * Encapsulates the sleep, standby, wake word listening, and inactivity timeout lifecycle
 * for BackgroundVoiceService.
 */
class VoiceSleepStandbyManager(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val appSettings: AppSettings,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val geminiLiveManager: GeminiLiveManager,
    private val hasMicPermission: () -> Boolean,
    private val isLitePaused: () -> Boolean,
    private val isHardPaused: () -> Boolean,
    private val getConnectionState: () -> ConnectionState,
    private val getActiveToolCallCount: () -> Int,
    private val getVoiceGate: () -> VoiceGate?,
    private val getWakeLock: () -> PowerManager.WakeLock?,
    private val onConnect: () -> Unit
) {
    companion object {
        private const val TAG = "VoiceSleepStandbyMgr"
    }

    private val _isSleeping = MutableStateFlow(false)
    val isSleeping: StateFlow<Boolean> = _isSleeping.asStateFlow()

    @Volatile var wakeGreetingPending = false

    var lastSpeechTimestamp = System.currentTimeMillis()
        private set

    private var wakeWordEngine: WakeWordEngine? = null
    private var standbyWakeLock: PowerManager.WakeLock? = null
    private var inactivityJob: Job? = null

    fun initWakeWord(headsetRouter: HeadsetAudioRouter?) {
        wakeWordEngine = WakeWordEngine(
            context = context,
            scope = serviceScope,
            onWake = { onWakeWordDetected() }
        )
    }

    fun updateSpeechTimestamp() {
        lastSpeechTimestamp = System.currentTimeMillis()
    }

    fun markAwake() {
        _isSleeping.value = false
    }

    /** Idle for appSettings.autoSleepMinutes (0 = off) -> standby, so Gemini uses no data. */
    fun startInactivityWatcher() {
        inactivityJob?.cancel()
        inactivityJob = serviceScope.launch {
            while (isActive) {
                delay(15_000)
                val mins = appSettings.autoSleepMinutes
                if (mins <= 0) continue
                if (_isSleeping.value || isLitePaused() || isHardPaused()) continue
                if (getConnectionState() != ConnectionState.Live) continue
                if (getActiveToolCallCount() > 0) continue
                if (System.currentTimeMillis() - lastSpeechTimestamp >= mins * 60_000L) {
                    Log.i(TAG, "Idle for $mins min — entering standby")
                    sleep()
                }
            }
        }
    }

    fun sleep() {
        Log.i(TAG, "Soltini going to sleep — full power-down")
        _isSleeping.value = true

        // Stop all audio hardware
        audioRecorder.stopRecording()
        audioPlayer.stop()

        // Cut the WebSocket and shut down the OkHttp thread pool
        geminiLiveManager.disconnect()

        // Release WakeLock — CPU can now truly sleep between orb taps
        getWakeLock()?.let { if (it.isHeld) it.release() }

        // Disable accessibility event processing — no overhead while idle
        com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isToolCallActive = false

        OverlayService.getInstance()?.setRingState(RingState.SLEEPING)

        // Standby: the offline wake-word listener now owns the mic.
        if (!isLitePaused() && !isHardPaused()) {
            MyraSoundEffects.playStandbySound()
            startWakeListening()
        }
    }

    fun wakeUp() {
        Log.i(TAG, "Soltini waking up from sleep")
        getVoiceGate()?.openWindow(8000L) // Boss just said the wake word: let the first short command through
        stopWakeListening()
        _isSleeping.value = false
        lastSpeechTimestamp = System.currentTimeMillis()

        // Reacquire WakeLock so the CPU stays on during voice session
        val wl = getWakeLock()
        if (wl?.isHeld == false) {
            try {
                wl.acquire(10 * 60 * 1000L)
            } catch (_: Exception) {}
        }

        onConnect()
    }

    fun toggleSleep() {
        if (_isSleeping.value) wakeUp() else sleep()
    }

    fun startWakeListening() {
        if (!appSettings.isWakeWordEnabled || !hasMicPermission()) return
        if (!_isSleeping.value || isLitePaused() || isHardPaused()) return
        acquireStandbyLock()
        serviceScope.launch {
            delay(600) // let the standby chime finish so it is not heard as speech
            if (_isSleeping.value && !isLitePaused() && !isHardPaused() && appSettings.isWakeWordEnabled) {
                wakeWordEngine?.start()
            }
        }
    }

    fun stopWakeListening() {
        wakeWordEngine?.stop()
        releaseStandbyLock()
    }

    private fun acquireStandbyLock() {
        try {
            if (standbyWakeLock == null) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                standbyWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Soltini::WakeWordLock")
                    .apply { setReferenceCounted(false) }
            }
            if (standbyWakeLock?.isHeld == false) standbyWakeLock?.acquire(10 * 60 * 1000L)
        } catch (e: Exception) {
            Log.w(TAG, "Standby wake lock failed: ${e.message}")
        }
    }

    private fun releaseStandbyLock() {
        try {
            standbyWakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
    }

    private fun onWakeWordDetected() {
        if (!_isSleeping.value || isLitePaused() || isHardPaused()) return
        stopWakeListening()
        vibrateBriefly()
        MyraSoundEffects.playWakeSound()
        wakeGreetingPending = true
        serviceScope.launch {
            delay(250)
            if (_isSleeping.value && !isLitePaused() && !isHardPaused()) wakeUp()
        }
    }

    private fun vibrateBriefly() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(
                VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {}
    }

    fun destroy() {
        inactivityJob?.cancel()
        wakeWordEngine?.stop()
        releaseStandbyLock()
    }
}
