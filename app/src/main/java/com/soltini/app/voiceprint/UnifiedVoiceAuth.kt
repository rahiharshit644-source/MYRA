package com.soltini.app.voiceprint

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.soltini.app.security.VoiceBiometricsManager
import com.soltini.app.util.AppLogger
import com.soltini.app.voiceprint.data.VoiceprintRepository
import com.soltini.app.voiceprint.domain.VoiceprintVerifier
import com.soltini.app.voiceprint.ml.OnnxSpeakerEmbedder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * UnifiedVoiceAuth
 *
 * Single unified authority for speaker verification, banking protection, and destructive
 * action confirmation.
 *
 * Consolidates:
 * 1. Primary: Neural speaker embedding verification (ONNX + AES-GCM encrypted VoiceprintRepository).
 * 2. Fallback: Legacy acoustic biometrics (VoiceBiometricsManager) to guarantee no loss of previously enrolled profiles.
 */
class UnifiedVoiceAuth private constructor(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedVoiceAuth"

        @Volatile
        private var instance: UnifiedVoiceAuth? = null

        fun getInstance(context: Context): UnifiedVoiceAuth =
            instance ?: synchronized(this) {
                instance ?: UnifiedVoiceAuth(context.applicationContext).also { instance = it }
            }
    }

    private val repository = VoiceprintRepository.getInstance(context)
    private val embedder = OnnxSpeakerEmbedder(context)
    private val verifier = VoiceprintVerifier(embedder, repository)
    private val legacyBiometrics = VoiceBiometricsManager.getInstance(context)

    @Volatile
    private var lastAuthTimestamp: Long = 0L

    init {
        // Initialize neural verifier state in background if available
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                verifier.reloadProfile()
            } catch (e: Exception) {
                Log.w(TAG, "Initial profile load error: ${e.message}")
            }
        }
    }

    fun getVerifier(): VoiceprintVerifier = verifier

    /**
     * Checks if the owner is enrolled in either the neural voiceprint system or legacy biometrics.
     */
    fun isOwnerEnrolled(): Boolean {
        return verifier.isReady || legacyBiometrics.isEnrolled()
    }

    /**
     * Whether voice lock protection (for banking mode / destructive actions) is active.
     */
    fun isVoiceLockEnabled(): Boolean {
        return legacyBiometrics.isVoiceLockEnabled()
    }

    fun setVoiceLockEnabled(enabled: Boolean) {
        legacyBiometrics.setVoiceLockEnabled(enabled)
    }

    /**
     * Checks if owner was verified recently within [windowMs].
     */
    fun isOwnerVerifiedRecently(windowMs: Long = 60_000L): Boolean {
        val now = System.currentTimeMillis()
        val localValid = (now - lastAuthTimestamp) < windowMs
        val verifierValid = verifier.isOwnerVerifiedWithin(windowMs)
        val legacyValid = legacyBiometrics.isRecentAuthValid(windowMs)
        return localValid || verifierValid || legacyValid
    }

    /**
     * Records a successful owner authentication across all subsystems.
     */
    fun recordSuccessfulAuth() {
        lastAuthTimestamp = System.currentTimeMillis()
        legacyBiometrics.recordSuccessfulAuth()
    }

    /**
     * Unified verification result
     */
    data class VoiceAuthResult(
        val isMatch: Boolean,
        val similarity: Float,
        val threshold: Float,
        val engine: String,
        val message: String
    )

    /**
     * Verifies raw 16kHz 16-bit mono PCM bytes using neural speaker embeddings if enrolled,
     * otherwise falling back to acoustic biometrics.
     */
    suspend fun verifyOwnerVoice(pcm16kBytes: ByteArray): VoiceAuthResult = withContext(Dispatchers.IO) {
        // 1. Try neural voiceprint verifier if ready
        if (verifier.isReady) {
            try {
                val shortCount = pcm16kBytes.size / 2
                if (shortCount >= 1600) { // at least 100ms
                    val shorts = ShortArray(shortCount)
                    ByteBuffer.wrap(pcm16kBytes)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(shorts)

                    val resultState = verifier.verifyUtterance(shorts)
                    val isMatch = resultState.state == VoiceprintVerifier.VerificationStateEnum.OWNER
                    if (isMatch) {
                        recordSuccessfulAuth()
                    }
                    return@withContext VoiceAuthResult(
                        isMatch = isMatch,
                        similarity = resultState.score,
                        threshold = resultState.effectiveThreshold,
                        engine = "Neural (ONNX)",
                        message = if (isMatch) "Neural voiceprint verified" else "Neural voiceprint mismatch"
                    )
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Neural verification error, trying fallback: ${e.message}")
            }
        }

        // 2. Fallback to legacy acoustic biometrics
        val legacyRes = legacyBiometrics.verifySample(pcm16kBytes)
        if (legacyRes.isMatch) {
            recordSuccessfulAuth()
        }
        VoiceAuthResult(
            isMatch = legacyRes.isMatch,
            similarity = legacyRes.similarity,
            threshold = legacyRes.threshold,
            engine = "Acoustic (Legacy)",
            message = legacyRes.reason
        )
    }

    /**
     * Records audio from microphone and verifies speaker against enrolled owner profile.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordAndVerifyOwnerVoice(
        durationMs: Long = 2500L,
        customThreshold: Float? = null
    ): VoiceAuthResult = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(sampleRate)

        val recorder: AudioRecord
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext VoiceAuthResult(false, 0f, 0.72f, "Error", "Microphone init failed")
            }
        } catch (e: Exception) {
            return@withContext VoiceAuthResult(false, 0f, 0.72f, "Error", "Mic access error: ${e.message}")
        }

        val totalBytesToRead = (sampleRate * 2 * (durationMs / 1000.0)).toInt()
        val pcmAccumulator = ByteArray(totalBytesToRead)
        var totalRead = 0

        try {
            recorder.startRecording()
            val chunk = ByteArray(2048)
            val startTime = System.currentTimeMillis()

            while (totalRead < totalBytesToRead && (System.currentTimeMillis() - startTime) < (durationMs + 1000)) {
                val read = recorder.read(chunk, 0, min(chunk.size, totalBytesToRead - totalRead))
                if (read > 0) {
                    System.arraycopy(chunk, 0, pcmAccumulator, totalRead, read)
                    totalRead += read
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error recording audio for UnifiedVoiceAuth: ${e.message}")
        } finally {
            try {
                recorder.stop()
                recorder.release()
            } catch (_: Exception) {}
        }

        if (totalRead < 3200) {
            return@withContext VoiceAuthResult(false, 0f, 0.72f, "Error", "Utterance too short for analysis")
        }

        val actualBytes = ByteArray(totalRead).also {
            System.arraycopy(pcmAccumulator, 0, it, 0, totalRead)
        }

        verifyOwnerVoice(actualBytes)
    }
}
