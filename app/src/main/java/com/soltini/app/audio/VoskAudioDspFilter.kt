package com.soltini.app.audio

import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * VoskAudioDspFilter — Digital Signal Processing Layer for Vosk Speech Recognition
 *
 * Implements a cascaded Biquad IIR filter topology specifically engineered to isolate
 * human voice commands and reject ambient background noise before feeding raw PCM to the Vosk model:
 *
 *  1. 2nd-Order Butterworth High-Pass Filter (HPF):
 *     - Cutoff: ~90 Hz (removes sub-bass rumble, wind buffeting, mechanical phone handling, 50/60Hz AC hum)
 *  2. 2nd-Order Butterworth Low-Pass Filter (LPF):
 *     - Cutoff: ~3800 Hz (removes high-frequency hiss, fan whir, fluorescent coil whine, ultrasonic artifacts)
 *  3. Vocal Pre-Emphasis (Boosts speech intelligibility and consonant distinction for Vosk phonemes)
 *  4. Adaptive Noise Gate & Soft Dynamic Range Compression (prevents clipping, boosts low-volume wake words)
 */
class VoskAudioDspFilter(
    private val sampleRate: Float = 16000.0f,
    private val hpfCutoff: Float = 90.0f,
    private val lpfCutoff: Float = 3800.0f,
    private val qFactor: Float = 0.70710678f // 1 / sqrt(2) for maximally flat Butterworth passband
) {
    companion object {
        private const val TAG = "VoskAudioDspFilter"
    }

    // --- Biquad State Structure ---
    private class BiquadState {
        var x1 = 0.0f
        var x2 = 0.0f
        var y1 = 0.0f
        var y2 = 0.0f

        fun reset() {
            x1 = 0.0f
            x2 = 0.0f
            y1 = 0.0f
            y2 = 0.0f
        }
    }

    // High-Pass Filter Coefficients
    private var hpB0 = 1.0f
    private var hpB1 = 0.0f
    private var hpB2 = 0.0f
    private var hpA1 = 0.0f
    private var hpA2 = 0.0f
    private val hpState = BiquadState()

    // Low-Pass Filter Coefficients
    private var lpB0 = 1.0f
    private var lpB1 = 0.0f
    private var lpB2 = 0.0f
    private var lpA1 = 0.0f
    private var lpA2 = 0.0f
    private val lpState = BiquadState()

    // Vocal Pre-Emphasis filter state (boosts consonant formants for Vosk)
    private var preEmphasisLastX = 0.0f
    private val preEmphasisAlpha = 0.60f

    // Dynamic Noise Gate & Tracking
    private var runningNoiseFloor = 0.012f
    private var speechEnvelope = 0.0f
    private var isGateOpen = false

    @Volatile
    var isEnabled: Boolean = true

    init {
        calculateHpCoefficients()
        calculateLpCoefficients()
        Log.i(TAG, "VoskAudioDspFilter initialized (HPF: ${hpfCutoff}Hz, LPF: ${lpfCutoff}Hz, SampleRate: ${sampleRate}Hz)")
    }

    /**
     * Computes 2nd-order Butterworth High-Pass Biquad Coefficients.
     */
    private fun calculateHpCoefficients() {
        val omega = 2.0 * PI * (hpfCutoff / sampleRate)
        val alpha = sin(omega) / (2.0 * qFactor)
        val cosOmega = cos(omega)

        val b0 = (1.0 + cosOmega) / 2.0
        val b1 = -(1.0 + cosOmega)
        val b2 = (1.0 + cosOmega) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosOmega
        val a2 = 1.0 - alpha

        hpB0 = (b0 / a0).toFloat()
        hpB1 = (b1 / a0).toFloat()
        hpB2 = (b2 / a0).toFloat()
        hpA1 = (a1 / a0).toFloat()
        hpA2 = (a2 / a0).toFloat()
    }

    /**
     * Computes 2nd-order Butterworth Low-Pass Biquad Coefficients.
     */
    private fun calculateLpCoefficients() {
        val omega = 2.0 * PI * (lpfCutoff / sampleRate)
        val alpha = sin(omega) / (2.0 * qFactor)
        val cosOmega = cos(omega)

        val b0 = (1.0 - cosOmega) / 2.0
        val b1 = 1.0 - cosOmega
        val b2 = (1.0 - cosOmega) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cosOmega
        val a2 = 1.0 - alpha

        lpB0 = (b0 / a0).toFloat()
        lpB1 = (b1 / a0).toFloat()
        lpB2 = (b2 / a0).toFloat()
        lpA1 = (a1 / a0).toFloat()
        lpA2 = (a2 / a0).toFloat()
    }

    /**
     * Resets filter memory states (called when stream restarts).
     */
    fun reset() {
        hpState.reset()
        lpState.reset()
        preEmphasisLastX = 0.0f
        runningNoiseFloor = 0.012f
        speechEnvelope = 0.0f
        isGateOpen = false
    }

    /**
     * Processes a 16-bit PCM byte array in-place or returns a clean byte array.
     *
     * @param pcmBytes Input raw PCM 16-bit mono bytes
     * @param length Number of valid bytes to process
     * @return Cleaned, bandpass-filtered PCM bytes ready for Vosk recognition
     */
    fun process(pcmBytes: ByteArray, length: Int = pcmBytes.size): ByteArray {
        if (!isEnabled || length < 2) return pcmBytes

        val sampleCount = length / 2
        val output = ByteArray(length)

        // 1. Convert to normalized float [-1.0f, 1.0f] and process through cascaded filters
        var frameEnergy = 0.0f
        for (i in 0 until sampleCount) {
            val idx = i * 2
            val low = pcmBytes[idx].toInt() and 0xFF
            val high = pcmBytes[idx + 1].toInt()
            val rawSample = ((high shl 8) or low).toShort()
            val x = rawSample / 32768.0f

            // Stage 1: 2nd-Order Butterworth High-Pass Filter (strips sub-rumble < 90Hz)
            val hpOut = hpB0 * x + hpB1 * hpState.x1 + hpB2 * hpState.x2 - hpA1 * hpState.y1 - hpA2 * hpState.y2
            hpState.x2 = hpState.x1
            hpState.x1 = x
            hpState.y2 = hpState.y1
            hpState.y1 = hpOut

            // Stage 2: 2nd-Order Butterworth Low-Pass Filter (strips noise hiss > 3800Hz)
            val lpOut = lpB0 * hpOut + lpB1 * lpState.x1 + lpB2 * lpState.x2 - lpA1 * lpState.y1 - lpA2 * lpState.y2
            lpState.x2 = lpState.x1
            lpState.x1 = hpOut
            lpState.y2 = lpState.y1
            lpState.y1 = lpOut

            // Stage 3: Vocal Formant Pre-Emphasis (accentuates speech acoustic transitions)
            val speechOut = lpOut - (preEmphasisAlpha * preEmphasisLastX)
            preEmphasisLastX = lpOut

            frameEnergy += abs(speechOut)
        }

        val avgEnergy = frameEnergy / max(1, sampleCount)

        // Stage 4: Adaptive Noise Floor Tracking
        if (avgEnergy < runningNoiseFloor * 1.5f) {
            // Background noise frame - slowly adapt noise floor upwards or downwards
            runningNoiseFloor = runningNoiseFloor * 0.96f + avgEnergy * 0.04f
        } else {
            // Speech frame detected
            runningNoiseFloor = runningNoiseFloor * 0.999f + avgEnergy * 0.001f
        }
        runningNoiseFloor = runningNoiseFloor.coerceIn(0.004f, 0.040f)

        // Stage 5: Voice Activity Gating & Dynamic Normalization
        val snr = avgEnergy / max(0.001f, runningNoiseFloor)
        val isVoiceActive = snr > 1.8f && avgEnergy > 0.008f

        // Smooth gate envelope (fast attack, gentle release)
        val targetGate = if (isVoiceActive) 1.35f else 0.20f
        val gateSmoothing = if (targetGate > speechEnvelope) 0.35f else 0.08f
        speechEnvelope = speechEnvelope * (1.0f - gateSmoothing) + targetGate * gateSmoothing

        // Repack into 16-bit PCM with soft hyperbolic tangent limiter
        // Re-read and apply envelope with soft-saturation
        for (i in 0 until sampleCount) {
            val idx = i * 2
            val low = pcmBytes[idx].toInt() and 0xFF
            val high = pcmBytes[idx + 1].toInt()
            val rawSample = ((high shl 8) or low).toShort()
            val x = rawSample / 32768.0f

            // High-pass + Low-pass
            val hpOut = hpB0 * x + hpB1 * hpState.x1 + hpB2 * hpState.x2 - hpA1 * hpState.y1 - hpA2 * hpState.y2
            val lpOut = lpB0 * hpOut + lpB1 * lpState.x1 + lpB2 * lpState.x2 - lpA1 * lpState.y1 - lpA2 * lpState.y2
            val speechOut = lpOut - (preEmphasisAlpha * preEmphasisLastX)

            // Dynamic gain application
            val boosted = speechOut * speechEnvelope

            // Soft-clipping hyperbolic tangent to prevent harsh digital distortion
            val limited = if (abs(boosted) > 0.85f) {
                kotlin.math.tanh(boosted.toDouble()).toFloat()
            } else {
                boosted
            }

            val intSample = (limited * 32767.0f).toInt().coerceIn(-32768, 32767)
            output[idx] = (intSample and 0xFF).toByte()
            output[idx + 1] = ((intSample shr 8) and 0xFF).toByte()
        }

        return output
    }
}
