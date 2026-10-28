package com.soltini.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener

/**
 * VoskDspAudioPipeline — Custom DSP Audio Pipeline for Vosk Recognition
 *
 * Replaces the raw, unfiltered Vosk SpeechService with an intelligent DSP pipeline:
 *  - Captures 16kHz 16-bit Mono PCM directly from the microphone
 *  - Attaches hardware AcousticEchoCanceler and NoiseSuppressor if available on device
 *  - Routes audio through [VoskAudioDspFilter] (2nd-Order Butterworth HPF @ 90Hz + LPF @ 3800Hz)
 *  - Feeds pristine, noise-isolated speech samples into [Recognizer.acceptWaveForm]
 *  - Dispatches hypothesis callbacks (onPartialResult, onResult, onFinalResult) to [RecognitionListener]
 */
class VoskDspAudioPipeline(
    private val recognizer: Recognizer,
    private val sampleRate: Float = 16000.0f
) {
    companion object {
        private const val TAG = "VoskDspAudioPipeline"
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE = 2048 // 1024 samples = 64ms at 16kHz
    }

    private val dspFilter = VoskAudioDspFilter(sampleRate = sampleRate)
    private var audioRecord: AudioRecord? = null
    private var workerJob: Job? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    @Volatile private var isRunning = false

    /**
     * Starts listening with DSP filtering active.
     */
    @SuppressLint("MissingPermission")
    fun startListening(listener: RecognitionListener) {
        if (isRunning) return

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate.toInt(),
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val bufferSize = maxOf(minBuf * 2, CHUNK_SIZE * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate.toInt(),
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "VOICE_RECOGNITION source unavailable, falling back to MIC")
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate.toInt(),
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize for Vosk DSP pipeline")
                audioRecord?.release()
                audioRecord = null
                listener.onError(IllegalStateException("AudioRecord initialization failed"))
                return
            }

            val sessionId = audioRecord!!.audioSessionId
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                }
                if (NoiseSuppressor.isAvailable()) {
                    ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Hardware AudioFX attach error: ${e.message}")
            }

            audioRecord?.startRecording()
            isRunning = true
            dspFilter.reset()

            workerJob = CoroutineScope(Dispatchers.IO).launch {
                val rawBuffer = ByteArray(CHUNK_SIZE)
                try {
                    while (isActive && isRunning) {
                        val bytesRead = audioRecord?.read(rawBuffer, 0, rawBuffer.size) ?: -1
                        if (bytesRead > 0) {
                            // DSP STAGE: Filter sub-bass rumble (<90Hz) and high-frequency noise (>3800Hz)
                            val cleanBuffer = dspFilter.process(rawBuffer, bytesRead)

                            // Feed filtered PCM to Vosk Recognizer
                            val isFinal = recognizer.acceptWaveForm(cleanBuffer, bytesRead)
                            if (isFinal) {
                                val result = recognizer.result
                                listener.onResult(result)
                            } else {
                                val partial = recognizer.partialResult
                                listener.onPartialResult(partial)
                            }
                        } else if (bytesRead < 0) {
                            Log.w(TAG, "AudioRecord read error: $bytesRead")
                            break
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.e(TAG, "Error in DSP audio pipeline loop: ${e.message}", e)
                        listener.onError(e)
                    }
                } finally {
                    try {
                        val finalResult = recognizer.finalResult
                        listener.onFinalResult(finalResult)
                    } catch (_: Exception) {}
                }
            }

            Log.i(TAG, "Vosk DSP Audio Pipeline active with Low-Pass + High-Pass filtering")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Vosk DSP pipeline: ${e.message}", e)
            stop()
            listener.onError(e)
        }
    }

    /**
     * Stops recording and releases audio hardware.
     */
    fun stop() {
        isRunning = false
        workerJob?.cancel()
        workerJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord stop error: ${e.message}")
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord release error: ${e.message}")
        }
        audioRecord = null

        try { aec?.release() } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        aec = null
        ns = null
    }

    fun shutdown() {
        stop()
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer close error: ${e.message}")
        }
    }
}
