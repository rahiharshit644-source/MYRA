package com.soltini.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Base64
import android.util.Log
import com.soltini.app.network.ConnectionState
import com.soltini.app.services.BackgroundVoiceService
import com.soltini.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GeminiNeuralVoiceSynthesizer — Authentic Gemini Live Voice Engine
 *
 * Replaces Android's robotic TextToSpeech synthesizer with pure, ultra-realistic
 * 24kHz Gemini neural voices (Aoede, Kore, Puck, Charon, Fenrir):
 *
 *  1. Priority 1 (Live Session): If BackgroundVoiceService is active, dispatches realtime prompt
 *     directly to Gemini Live WebSocket (`realtimeInput.text`), streaming audio at 24kHz.
 *
 *  2. Priority 2 (Standby/Off-Session): Synthesizes natural audio using Gemini 2.0/2.5 Audio Modality
 *     REST API and plays the 24kHz PCM buffer directly via native AudioTrack.
 */
class GeminiNeuralVoiceSynthesizer(private val context: Context) {

    companion object {
        private const val TAG = "GeminiNeuralVoice"
        private const val SAMPLE_RATE = 24000

        @Volatile
        private var instance: GeminiNeuralVoiceSynthesizer? = null

        fun getInstance(context: Context): GeminiNeuralVoiceSynthesizer =
            instance ?: synchronized(this) {
                instance ?: GeminiNeuralVoiceSynthesizer(context.applicationContext).also { instance = it }
            }
    }

    private val appSettings = AppSettings(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var activeTrack: AudioTrack? = null

    /**
     * Checks if headphones or Bluetooth headset is currently connected.
     */
    fun isHeadsetConnected(): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        } else {
            @Suppress("DEPRECATION")
            return am.isWiredHeadsetOn || am.isBluetoothA2dpOn || am.isBluetoothScoOn
        }
    }

    /**
     * Speaks the given announcement using the authentic Gemini Live Voice.
     *
     * @param announcementText The natural text to speak (e.g. "Boss, Rahul ka WhatsApp message aaya hai... Kya reply bheju?")
     * @param onComplete Callback invoked when speech finishes
     */
    fun speakWithGeminiVoice(announcementText: String, onComplete: (() -> Unit)? = null) {
        if (announcementText.isBlank()) return

        // 1. Check if user configured "Only when Headphones connected"
        if (appSettings.isAnnounceOnlyHeadphones && !isHeadsetConnected()) {
            Log.d(TAG, "Suppressed announcement: Headphones required but not connected.")
            return
        }

        // 2. Check if Gemini Live session is already running
        val voiceService = BackgroundVoiceService.getInstance()
        if (voiceService != null && voiceService.connectionState.value == ConnectionState.Live) {
            Log.i(TAG, "Speaking via active Gemini Live WebSocket session")
            voiceService.geminiLiveManager.sendClientContent(
                "[System: Read this announcement aloud to Boss in your warm, natural voice: \"$announcementText\"]"
            )
            onComplete?.invoke()
            return
        }

        // 3. Synthesize via Gemini Audio REST endpoint (identical voice persona, 24kHz PCM)
        scope.launch {
            val apiKey = appSettings.effectiveApiKey()
            if (apiKey.isBlank()) {
                Log.w(TAG, "No API key available for Gemini voice synthesis")
                return@launch
            }

            try {
                val voiceName = appSettings.voiceName.ifBlank { "Aoede" }
                val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "Speak the following text aloud with a warm, natural human Hinglish cadence. Do not explain, just speak: $announcementText")
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", voiceName)
                                })
                            })
                        })
                    })
                }

                val req = Request.Builder()
                    .url(requestUrl)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val resp = httpClient.newCall(req).execute()
                val respBody = resp.body?.string() ?: ""

                if (resp.isSuccessful) {
                    val json = JSONObject(respBody)
                    val candidates = json.optJSONArray("candidates")
                    val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")

                    var audioBase64: String? = null
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val inlineData = part.optJSONObject("inlineData")
                            if (inlineData != null) {
                                audioBase64 = inlineData.optString("data")
                                break
                            }
                        }
                    }

                    if (!audioBase64.isNullOrBlank()) {
                        val pcmBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                        playPcmBuffer(pcmBytes)
                        withContext(Dispatchers.Main) { onComplete?.invoke() }
                    } else {
                        Log.w(TAG, "No audio inlineData in Gemini response")
                    }
                } else {
                    Log.w(TAG, "Gemini Audio generation failed: ${resp.code} -> $respBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Gemini voice synthesis: ${e.message}", e)
            }
        }
    }

    /**
     * Plays raw 24kHz 16-bit mono PCM buffer directly through Android AudioTrack.
     */
    private fun playPcmBuffer(pcmData: ByteArray) {
        try {
            stopPlayback()
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuf, pcmData.size)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            activeTrack = track
            track.play()

            var offset = 0
            val chunkSize = 2048
            while (offset < pcmData.size) {
                val toWrite = minOf(chunkSize, pcmData.size - offset)
                track.write(pcmData, offset, toWrite)
                offset += toWrite
            }

            // Small delay to allow hardware audio buffer to flush
            Thread.sleep(150)
            track.stop()
            track.release()
            activeTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error: ${e.message}", e)
        }
    }

    fun stopPlayback() {
        try {
            activeTrack?.stop()
            activeTrack?.release()
        } catch (_: Exception) {}
        activeTrack = null
    }
}
