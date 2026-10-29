package com.soltini.app.secretary

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.audio.GeminiNeuralVoiceSynthesizer
import com.soltini.app.audio.JarvisSoundEngine
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
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class VoiceNoteSummary(
    val sender: String,
    val transcription: String,
    val tacticalSummary: String,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * WhatsAppVoiceNoteTranscriber — AI Voice Note Transcriber & Summarizer
 *
 * Automatically intercepts WhatsApp incoming voice notes ("Voice message (0:45)"),
 * fetches the corresponding recent audio recording (.opus / .m4a) from storage / MediaStore,
 * streams it to Gemini Multimodal Audio API, and produces a crisp 1-sentence Hinglish summary.
 *
 * Myra then vocalizes with Gemini's voice:
 *  "Boss, Rahul ne 45-second ka voice note bheja hai: 'Kal subah 10 baje college me milte hain'. Kya reply bheju?"
 */
class WhatsAppVoiceNoteTranscriber private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "VoiceNoteTranscriber"

        @Volatile
        private var instance: WhatsAppVoiceNoteTranscriber? = null

        fun getInstance(context: Context): WhatsAppVoiceNoteTranscriber =
            instance ?: synchronized(this) {
                instance ?: WhatsAppVoiceNoteTranscriber(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val appSettings = AppSettings(appContext)
    private val geminiSpeaker = GeminiNeuralVoiceSynthesizer.getInstance(appContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * Checks if notification text represents an audio / voice message.
     */
    fun isVoiceNoteNotification(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("voice message") ||
               lower.contains("voice note") ||
               lower.contains("audio message") ||
               lower.contains("🎤") ||
               lower.contains("ptt-")
    }

    /**
     * Processes incoming voice note notification from WhatsApp.
     */
    fun processVoiceNote(sender: String, notifText: String, onSummaryReady: ((VoiceNoteSummary) -> Unit)? = null) {
        scope.launch {
            try {
                Log.i(TAG, "Intercepted WhatsApp voice note from $sender: '$notifText'")
                AgentStatusBus.emit("🎧 Transcribing WhatsApp Voice Note from $sender...")
                JarvisSoundEngine.playTacticalBlip()

                val audioFileBytes = findLatestWhatsAppAudio()
                if (audioFileBytes != null && audioFileBytes.isNotEmpty()) {
                    transcribeWithGemini(sender, audioFileBytes, onSummaryReady)
                } else {
                    // Fallback when storage permission or file not immediately readable:
                    // Provide a smart acknowledgment to Boss
                    val summary = VoiceNoteSummary(
                        sender = sender,
                        transcription = "Voice message received",
                        tacticalSummary = "$sender ne voice note bheja hai.",
                        durationSeconds = extractDurationSeconds(notifText)
                    )
                    announceVoiceNote(summary)
                    withContext(Dispatchers.Main) { onSummaryReady?.invoke(summary) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice note: ${e.message}", e)
            }
        }
    }

    /**
     * Extracts duration in seconds from text like "Voice message (0:45)".
     */
    private fun extractDurationSeconds(text: String): Int {
        val match = Regex("(\\d+):(\\d+)").find(text) ?: return 15
        val (mins, secs) = match.destructured
        return (mins.toIntOrNull() ?: 0) * 60 + (secs.toIntOrNull() ?: 15)
    }

    /**
     * Finds the most recently received WhatsApp audio / voice note file.
     */
    private fun findLatestWhatsAppAudio(): ByteArray? {
        return try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.MIME_TYPE
            )
            val selection = "${MediaStore.Audio.Media.DATA} LIKE ? OR ${MediaStore.Audio.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("%WhatsApp%", "%opus%")
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val id = cursor.getLong(idIdx)
                    val contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                    appContext.contentResolver.openInputStream(contentUri)?.use { it.readBytes() }
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query MediaStore for WhatsApp audio: ${e.message}")
            null
        }
    }

    /**
     * Sends the audio bytes to Gemini Multimodal Audio model to extract transcription and summary.
     */
    private suspend fun transcribeWithGemini(
        sender: String,
        audioBytes: ByteArray,
        onSummaryReady: ((VoiceNoteSummary) -> Unit)?
    ) {
        val apiKey = appSettings.effectiveApiKey()
        if (apiKey.isBlank()) {
            Log.w(TAG, "Gemini API key missing, cannot transcribe audio")
            return
        }

        try {
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

            val prompt = """You are Myra's Voice Secretary. 
The user '$sender' sent a WhatsApp voice message to Boss (Harshit).
Listen carefully to the audio and produce:
1. Exact transcription.
2. A 1-sentence tactical summary in crisp, respectful Hinglish (e.g., "$sender ne bola hai ki kal 10 baje college me milna hai aur assignment submit karna hai.").

Output valid JSON ONLY in this format:
{"transcription": "...", "summary": "...", "duration_seconds": 20}"""

            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "audio/ogg")
                                    put("data", base64Audio)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (resp.isSuccessful) {
                val json = JSONObject(body)
                val textCandidate = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text") ?: ""

                val cleanJsonStr = textCandidate.replace("```json", "").replace("```", "").trim()
                val parsed = try { JSONObject(cleanJsonStr) } catch (_: Exception) { JSONObject() }

                val summaryText = parsed.optString("summary", "$sender ne audio note bheja hai.")
                val transcript = parsed.optString("transcription", "Voice message")
                val duration = parsed.optInt("duration_seconds", 15)

                val summary = VoiceNoteSummary(
                    sender = sender,
                    transcription = transcript,
                    tacticalSummary = summaryText,
                    durationSeconds = duration
                )

                announceVoiceNote(summary)
                withContext(Dispatchers.Main) { onSummaryReady?.invoke(summary) }
            } else {
                Log.w(TAG, "Gemini transcription failed with code ${resp.code}: $body")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error: ${e.message}", e)
        }
    }

    private fun announceVoiceNote(summary: VoiceNoteSummary) {
        if (!appSettings.isNotificationAnnouncerEnabled) return
        val prompt = "Boss, ${summary.sender} ka voice note aaya hai: \"${summary.tacticalSummary}\". Kya reply bheju?"
        geminiSpeaker.speakWithGeminiVoice(prompt)
        AgentStatusBus.emit("🎙️ Voice Note Summary (${summary.sender}): \"${summary.tacticalSummary}\"")
    }
}
