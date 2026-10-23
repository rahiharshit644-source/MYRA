package com.soltini.app.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.soltini.app.audio.AudioPlayer
import com.soltini.app.audio.AudioRecorder
import com.soltini.app.network.GeminiLiveManager
import com.soltini.app.overlay.OverlayService
import com.soltini.app.overlay.RingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VoicePauseModeManager
 *
 * Manages banking mode pause states:
 * - Lite Pause: silences accessibility service and mic, keeps service listed.
 * - Hard Pause: full force-stop / self-termination for strict banking apps.
 */
class VoicePauseModeManager(
    private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val geminiLiveManager: GeminiLiveManager,
    private val getWakeLock: () -> PowerManager.WakeLock?,
    private val onStopWakeListening: () -> Unit,
    private val onConnect: () -> Unit,
    private val onSetSleeping: (Boolean) -> Unit,
    private val onUpdateNotification: (String) -> Unit,
    private val onStopSelf: () -> Unit
) {
    companion object {
        private const val TAG = "VoicePauseModeManager"
    }

    private val _isLitePaused = MutableStateFlow(false)
    val isLitePaused: StateFlow<Boolean> = _isLitePaused.asStateFlow()

    private val _isHardPaused = MutableStateFlow(false)
    val isHardPaused: StateFlow<Boolean> = _isHardPaused.asStateFlow()

    fun enterLitePauseMode() {
        if (_isLitePaused.value || _isHardPaused.value) return
        Log.i(TAG, "Entering Lite Pause — silencing accessibility without disabling")
        onStopWakeListening()
        _isLitePaused.value = true
        onSetSleeping(true)

        audioRecorder.stopRecording()
        audioPlayer.stop()
        geminiLiveManager.disconnect()
        getWakeLock()?.let { if (it.isHeld) it.release() }

        // Silence the accessibility service without disabling it
        com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isPausedLite = true
        com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isToolCallActive = false

        // Completely hide the orb overlay while paused
        OverlayService.getInstance()?.setHiddenForBanking(true)
        onUpdateNotification("⏸ Lite Pause — tap to resume")
    }

    fun exitLitePauseMode() {
        if (!_isLitePaused.value) return
        Log.i(TAG, "Exiting Lite Pause")
        _isLitePaused.value = false
        onSetSleeping(false)

        // Un-silence the accessibility service
        com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isPausedLite = false

        val wl = getWakeLock()
        if (wl?.isHeld == false) {
            try {
                wl.acquire(10 * 60 * 1000L)
            } catch (_: Exception) {}
        }
        OverlayService.getInstance()?.setHiddenForBanking(false)
        OverlayService.getInstance()?.setRingState(RingState.IDLE)
        onUpdateNotification("Your AI Assistant is ready")
        onConnect()
    }

    fun toggleLitePauseMode() {
        if (_isLitePaused.value) exitLitePauseMode() else enterLitePauseMode()
    }

    fun enterHardPauseMode() {
        if (_isHardPaused.value || _isLitePaused.value) return
        Log.i(TAG, "Entering Hard Pause (Force Stop) — opening App Info to self-terminate")
        onStopWakeListening()
        _isHardPaused.value = true
        onSetSleeping(true)

        audioRecorder.stopRecording()
        audioPlayer.stop()
        geminiLiveManager.disconnect()
        getWakeLock()?.let { if (it.isHeld) it.release() }

        val a11yService = com.soltini.app.agent.SoltiniAccessibilityService.getInstance()
        if (a11yService != null) {
            a11yService.isAutomatingForceStop = true
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            onStopSelf()
        }

        OverlayService.getInstance()?.setRingState(RingState.SLEEPING)
        onUpdateNotification("🔴 Force Stopped — open app to resume")
    }

    fun exitHardPauseMode() {
        if (!_isHardPaused.value) return
        Log.i(TAG, "Exiting Hard Pause (Force Stop mode)")
        _isHardPaused.value = false
        onSetSleeping(false)

        val wl = getWakeLock()
        if (wl?.isHeld == false) {
            try {
                wl.acquire(10 * 60 * 1000L)
            } catch (_: Exception) {}
        }
        OverlayService.getInstance()?.setRingState(RingState.IDLE)
        onUpdateNotification("Your AI Assistant is ready")
        onConnect()
    }

    fun toggleHardPauseMode() {
        if (_isHardPaused.value) exitHardPauseMode() else enterHardPauseMode()
    }

    fun enterPauseMode() = enterLitePauseMode()
    fun exitPauseMode() = if (_isLitePaused.value) exitLitePauseMode() else exitHardPauseMode()
    fun togglePauseMode() = toggleLitePauseMode()
}
