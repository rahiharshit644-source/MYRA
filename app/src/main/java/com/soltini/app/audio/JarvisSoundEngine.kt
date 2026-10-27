package com.soltini.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * JarvisSoundEngine — Authentic Iron Man HUD Tactical Sound Synthesizer
 *
 * Synthesizes futuristic HUD audio cues procedurally in real-time using PCM sine
 * waves and envelopes over Android [AudioTrack].
 * Runs 100% offline with zero external audio assets, zero latency, and zero memory leaks.
 */
object JarvisSoundEngine {

    private const val TAG = "JarvisSoundEngine"
    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    var isEnabled: Boolean = true

    /**
     * Arc Reactor Spool-Up: Frequency sweep upwards (320Hz -> 880Hz -> 1760Hz shimmer)
     * Played when an Autonomous Protocol starts, or when voice live session connects.
     */
    fun playArcReactorHum() {
        if (!isEnabled) return
        scope.launch {
            try {
                val durationMs = 380
                val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    // Quadratic sweep from 320Hz to 1400Hz
                    val freq = 320.0 + (1080.0 * progress * progress)
                    val angle = 2.0 * PI * i * (freq / SAMPLE_RATE)
                    // Envelope: fast attack, smooth exponential decay
                    val envelope = sin(PI * progress) * (1.0 - progress * 0.3)
                    val sample = (sin(angle) * envelope * 24000.0).toInt().coerceIn(-32767, 32767)
                    buffer[i] = sample.toShort()
                }
                playBuffer(buffer)
            } catch (e: Exception) {
                Log.w(TAG, "playArcReactorHum error: ${e.message}")
            }
        }
    }

    /**
     * Task Success Chime: Ascending futuristic tri-tone harmonic chord (C6 -> E6 -> G6)
     * Played when a multi-step protocol or autonomous goal finishes flawlessly.
     */
    fun playTaskSuccessChime() {
        if (!isEnabled) return
        scope.launch {
            try {
                val durationMs = 320
                val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                val tones = listOf(1046.5, 1318.5, 1567.9) // C6, E6, G6
                val stepSamples = numSamples / 3

                for (i in 0 until numSamples) {
                    val toneIdx = (i / stepSamples).coerceIn(0, 2)
                    val freq = tones[toneIdx]
                    val stepProgress = (i % stepSamples).toDouble() / stepSamples
                    val envelope = sin(PI * stepProgress) * 0.9
                    val angle = 2.0 * PI * i * (freq / SAMPLE_RATE)
                    val sample = (sin(angle) * envelope * 26000.0).toInt().coerceIn(-32767, 32767)
                    buffer[i] = sample.toShort()
                }
                playBuffer(buffer)
            } catch (e: Exception) {
                Log.w(TAG, "playTaskSuccessChime error: ${e.message}")
            }
        }
    }

    /**
     * Tactical Blip: Crisp 25ms high-frequency chirping blip (2400Hz)
     * Played on step transitions, HUD taps, and element targeting.
     */
    fun playTacticalBlip() {
        if (!isEnabled) return
        scope.launch {
            try {
                val durationMs = 30
                val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val freq = 2400.0 - (400.0 * progress)
                    val envelope = (1.0 - progress) * (1.0 - progress)
                    val angle = 2.0 * PI * i * (freq / SAMPLE_RATE)
                    val sample = (sin(angle) * envelope * 22000.0).toInt().coerceIn(-32767, 32767)
                    buffer[i] = sample.toShort()
                }
                playBuffer(buffer)
            } catch (e: Exception) {
                Log.w(TAG, "playTacticalBlip error: ${e.message}")
            }
        }
    }

    /**
     * Warning Alert: Subdued tactical dual alert tone
     */
    fun playWarningAlert() {
        if (!isEnabled) return
        scope.launch {
            try {
                val durationMs = 180
                val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val freq = if (progress < 0.5) 440.0 else 370.0
                    val envelope = sin(PI * (progress * 2 % 1.0))
                    val angle = 2.0 * PI * i * (freq / SAMPLE_RATE)
                    val sample = (sin(angle) * envelope * 20000.0).toInt().coerceIn(-32767, 32767)
                    buffer[i] = sample.toShort()
                }
                playBuffer(buffer)
            } catch (e: Exception) {
                Log.w(TAG, "playWarningAlert error: ${e.message}")
            }
        }
    }

    /**
     * Sentry Intrusion Alarm: Urgent alternating high-tech siren (880Hz <-> 1320Hz)
     * Triggered when motion tripwire detects unauthorized movement.
     */
    fun playSentryIntrusionAlarm() {
        if (!isEnabled) return
        scope.launch {
            try {
                val durationMs = 600
                val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    // Alternates between 880Hz and 1320Hz every 100ms
                    val cycle = ((progress * 6.0).toInt() % 2)
                    val freq = if (cycle == 0) 880.0 else 1320.0
                    val envelope = sin(PI * ((progress * 6.0) % 1.0)) * 0.95
                    val angle = 2.0 * PI * i * (freq / SAMPLE_RATE)
                    val sample = (sin(angle) * envelope * 26000.0).toInt().coerceIn(-32767, 32767)
                    buffer[i] = sample.toShort()
                }
                playBuffer(buffer)
            } catch (e: Exception) {
                Log.w(TAG, "playSentryIntrusionAlarm error: ${e.message}")
            }
        }
    }

    private fun playBuffer(buffer: ShortArray) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
        audioTrack.setNotificationMarkerPosition(buffer.size)
        audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onPeriodicNotification(track: AudioTrack?) {}
            override fun onMarkerReached(track: AudioTrack?) {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        })
    }
}
