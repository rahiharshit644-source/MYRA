package com.soltini.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.services.BackgroundVoiceService
import com.soltini.app.settings.AppSettings

/**
 * HeadsetMediaButtonTrigger — Earphone & Earbud Button Activation Shortcut
 *
 * Intercepts physical / touch buttons on wired earphones, Bluetooth earbuds (AirPods,
 * OnePlus, Realme, Boat, Galaxy Buds, Neckbands) using Android MediaSession:
 *
 *  - Double-Tap Earphone Button (<450ms): Wakes Myra instantly!
 *  - Long-Press Earphone Button (>650ms): Wakes Myra instantly!
 *  - Earphone Connect Greeting: Speaks tactical greeting when earphones are plugged in.
 */
class HeadsetMediaButtonTrigger(private val context: Context) {

    companion object {
        private const val TAG = "HeadsetButtonTrigger"
        private const val DOUBLE_CLICK_TIMEOUT_MS = 450L
        private const val LONG_PRESS_THRESHOLD_MS = 650L
    }

    private val appSettings = AppSettings(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaSession: MediaSession? = null

    private var lastKeyDownTime = 0L
    private var clickCount = 0
    private var isLongPressTriggered = false

    private val pendingClickRunnable = Runnable {
        clickCount = 0
    }

    private val headsetPlugReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                if (state == 1 && appSettings.isEarphonePlugGreetingEnabled) {
                    mainHandler.postDelayed({
                        val voice = GeminiNeuralVoiceSynthesizer.getInstance(context)
                        voice.speakWithGeminiVoice("Earphones connected, Boss. Tactical systems online.")
                        AgentStatusBus.emit("🎧 Earphones Connected — Systems Online")
                    }, 500)
                }
            } else if (action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                AgentStatusBus.emit("🎧 Earphones Disconnected")
            }
        }
    }

    fun start() {
        if (!appSettings.isEarphoneShortcutEnabled) return

        try {
            if (mediaSession == null) {
                mediaSession = MediaSession(context, "MyraHeadsetMediaSession").apply {
                    setFlags(
                        MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                    )
                    setPlaybackState(
                        PlaybackState.Builder()
                            .setActions(
                                PlaybackState.ACTION_PLAY_PAUSE or
                                PlaybackState.ACTION_PLAY or
                                PlaybackState.ACTION_PAUSE or
                                PlaybackState.ACTION_SKIP_TO_NEXT or
                                PlaybackState.ACTION_SKIP_TO_PREVIOUS
                            )
                            .setState(PlaybackState.STATE_PAUSED, 0, 1.0f)
                            .build()
                    )
                    setCallback(object : MediaSession.Callback() {
                        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                            return handleMediaKeyEvent(mediaButtonIntent)
                        }
                    })
                    isActive = true
                }
                Log.i(TAG, "MediaSession active for earphone shortcuts.")
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }
            context.registerReceiver(headsetPlugReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing HeadsetMediaButtonTrigger: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
            context.unregisterReceiver(headsetPlugReceiver)
        } catch (_: Exception) {}
    }

    private fun handleMediaKeyEvent(intent: Intent): Boolean {
        if (!appSettings.isEarphoneShortcutEnabled) return false

        val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        } ?: return false

        val keyCode = event.keyCode
        val isHookOrPlay = keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                           keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                           keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                           keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE

        if (!isHookOrPlay) return false

        val now = System.currentTimeMillis()

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                lastKeyDownTime = now
                isLongPressTriggered = false
                clickCount++

                mainHandler.removeCallbacks(pendingClickRunnable)

                if (clickCount >= 2) {
                    // Double-click detected!
                    clickCount = 0
                    triggerMyraWake("Earphone Button Double-Tap")
                    return true
                } else {
                    mainHandler.postDelayed(pendingClickRunnable, DOUBLE_CLICK_TIMEOUT_MS)
                }
            } else if (!isLongPressTriggered && now - lastKeyDownTime >= LONG_PRESS_THRESHOLD_MS) {
                // Long-press detected!
                isLongPressTriggered = true
                clickCount = 0
                mainHandler.removeCallbacks(pendingClickRunnable)
                triggerMyraWake("Earphone Button Long-Press")
                return true
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (isLongPressTriggered) {
                return true // Consume release of long press
            }
        }

        return false
    }

    private fun triggerMyraWake(source: String) {
        Log.i(TAG, "Triggering Myra wake from $source")
        mainHandler.post {
            JarvisSoundEngine.playArcReactorHum()
            triggerHapticPulse()
            AgentStatusBus.emit("⚡ $source -> Myra Listening")

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

    private fun triggerHapticPulse() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (_: Exception) {}
    }
}
