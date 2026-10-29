package com.soltini.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.services.BackgroundVoiceService
import com.soltini.app.settings.AppSettings

/**
 * MyraMediaButtonReceiver — High-Priority Lock-Screen & Screen-Off Headset Hook Receiver
 *
 * Intercepts earphone button presses (Double-Tap or Long-Press) when the phone screen is
 * completely TURNED OFF and LOCKED in the user's pocket:
 *
 *  - Acquires an immediate partial WakeLock to wake the CPU from deep sleep.
 *  - Detects KEYCODE_HEADSETHOOK, KEYCODE_MEDIA_PLAY_PAUSE, or VOICE_COMMAND intents.
 *  - Plays Arc Reactor spool-up sound directly to the earphone.
 *  - Wakes BackgroundVoiceService and connects Gemini Live to the headset microphone.
 */
class MyraMediaButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MyraMediaReceiver"
        private const val DOUBLE_CLICK_WINDOW_MS = 500L
        private const val HOLD_THRESHOLD_MS = 400L

        @Volatile
        private var lastClickTime = 0L
        @Volatile
        private var clickCount = 0
        @Volatile
        private var lastKeyDownTime = 0L
        @Volatile
        private var isHoldTriggered = false

        private val handler = Handler(Looper.getMainLooper())
        private val resetClickRunnable = Runnable {
            clickCount = 0
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "onReceive broadcast action: $action")

        val appSettings = AppSettings(context)
        if (!appSettings.isEarphoneShortcutEnabled) return

        // 1. Acquire immediate WakeLock so Android CPU doesn't sleep mid-handling
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Myra:HeadsetOffScreenWakeLock")
        wakeLock?.acquire(12_000L) // 12 seconds max

        // Check if intent is direct system VOICE_COMMAND (sent when holding earphone button on lock screen)
        if (action == Intent.ACTION_VOICE_COMMAND ||
            action == "android.speech.action.VOICE_SEARCH_HANDS_FREE" ||
            action == "android.intent.action.VOICE_ASSIST") {
            Log.i(TAG, "Lock-screen voice command broadcast intercepted -> triggering Myra")
            triggerMyraWake(context, "Earphone Lock-Screen Hold")
            return
        }

        if (action != Intent.ACTION_MEDIA_BUTTON) return

        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        } ?: return

        val keyCode = keyEvent.keyCode
        val isTargetKey = keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                          keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                          keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                          keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                          keyCode == KeyEvent.KEYCODE_CALL

        if (!isTargetKey) return

        val now = System.currentTimeMillis()

        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            if (keyEvent.repeatCount == 0) {
                lastKeyDownTime = now
                isHoldTriggered = false
                clickCount++

                handler.removeCallbacks(resetClickRunnable)

                if (clickCount >= 2) {
                    // Double-tap while locked!
                    clickCount = 0
                    triggerMyraWake(context, "Earphone Double-Click (Screen OFF)")
                    abortBroadcastIfPossible()
                } else {
                    handler.postDelayed(resetClickRunnable, DOUBLE_CLICK_WINDOW_MS)
                }
            } else if (!isHoldTriggered && (now - lastKeyDownTime >= HOLD_THRESHOLD_MS || keyEvent.repeatCount >= 1)) {
                // Long press / hold while locked!
                isHoldTriggered = true
                clickCount = 0
                handler.removeCallbacks(resetClickRunnable)
                triggerMyraWake(context, "Earphone Hold (Screen OFF)")
                abortBroadcastIfPossible()
            }
        } else if (keyEvent.action == KeyEvent.ACTION_UP) {
            if (isHoldTriggered) {
                abortBroadcastIfPossible()
            }
        }
    }

    private fun abortBroadcastIfPossible() {
        try {
            if (isOrderedBroadcast) {
                abortBroadcast()
            }
        } catch (_: Exception) {}
    }

    private fun triggerMyraWake(context: Context, reason: String) {
        Log.i(TAG, "Triggering Myra wake ($reason)")
        handler.post {
            try {
                // 1. Play Arc Reactor Hum acoustic confirmation
                JarvisSoundEngine.playArcReactorHum()

                // 2. Trigger Haptic Pulse
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(90)
                }

                AgentStatusBus.emit("⚡ $reason -> Myra Active")

                // 3. Ensure BackgroundVoiceService is alive & wake it
                val serviceIntent = Intent(context, BackgroundVoiceService::class.java).apply {
                    action = "ACTION_HEADSET_WAKE"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                val activeService = BackgroundVoiceService.getInstance()
                if (activeService != null) {
                    if (activeService.isSleeping.value) {
                        activeService.wakeUp()
                    } else if (activeService.connectionState.value == com.soltini.app.network.ConnectionState.Disconnected) {
                        activeService.connect()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in triggerMyraWake: ${e.message}", e)
            }
        }
    }
}
