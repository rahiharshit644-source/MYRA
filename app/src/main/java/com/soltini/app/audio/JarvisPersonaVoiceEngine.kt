package com.soltini.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.telephony.TtsSpeaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoicePersona(
    val id: String,
    val displayName: String,
    val pitch: Float,
    val speed: Float,
    val localeLanguage: String,
    val localeCountry: String,
    val greetingPhrase: String
) {
    JARVIS_BRITISH(
        id = "jarvis",
        displayName = "J.A.R.V.I.S. (Stark)",
        pitch = 0.86f,
        speed = 0.94f,
        localeLanguage = "en",
        localeCountry = "GB",
        greetingPhrase = "J.A.R.V.I.S. protocols initialized, Sir. All telemetry systems are operating at peak capacity."
    ),
    EDITH_TACTICAL(
        id = "edith",
        displayName = "E.D.I.T.H. (Tactical)",
        pitch = 1.18f,
        speed = 1.06f,
        localeLanguage = "en",
        localeCountry = "US",
        greetingPhrase = "E.D.I.T.H. armed and online. Tactical defense grid standing by."
    ),
    MYRA_HINGLISH(
        id = "myra",
        displayName = "Myra (Hinglish)",
        pitch = 1.05f,
        speed = 1.0f,
        localeLanguage = "hi",
        localeCountry = "IN",
        greetingPhrase = "Ji Boss! Myra ready hai, batayein kya madad karu!"
    )
}

/**
 * JarvisPersonaVoiceEngine — Neural Voice Persona Engine
 *
 * Inspired by coqui-ai/TTS & Iron Man audio synthesis.
 * Dynamically switches vocal timbre, pitch, cadence, and linguistic localization between
 * Paul Bettany J.A.R.V.I.S., Tactical E.D.I.T.H., and Warm Hinglish Myra.
 */
class JarvisPersonaVoiceEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "JarvisPersonaEngine"
        private const val PREFS_KEY = "jarvis_active_voice_persona"

        @Volatile
        private var instance: JarvisPersonaVoiceEngine? = null

        fun getInstance(context: Context): JarvisPersonaVoiceEngine =
            instance ?: synchronized(this) {
                instance ?: JarvisPersonaVoiceEngine(context.applicationContext).also { instance = it }
            }
    }

    private val prefs = appContext.getSharedPreferences("jarvis_persona_prefs", Context.MODE_PRIVATE)
    private val ttsSpeaker = TtsSpeaker(appContext)

    private val _activePersona = MutableStateFlow(loadSavedPersona())
    val activePersona: StateFlow<VoicePersona> = _activePersona.asStateFlow()

    init {
        applyPersonaToTts(_activePersona.value, vocalize = false)
    }

    fun switchPersona(persona: VoicePersona, vocalizeGreeting: Boolean = true) {
        _activePersona.value = persona
        prefs.edit().putString(PREFS_KEY, persona.id).apply()

        JarvisSoundEngine.playTacticalBlip()
        AgentStatusBus.emit("🎙️ Voice Persona Engaged: ${persona.displayName}")

        applyPersonaToTts(persona, vocalize = vocalizeGreeting)
    }

    private fun loadSavedPersona(): VoicePersona {
        val savedId = prefs.getString(PREFS_KEY, VoicePersona.JARVIS_BRITISH.id)
        return VoicePersona.values().firstOrNull { it.id == savedId } ?: VoicePersona.JARVIS_BRITISH
    }

    private fun applyPersonaToTts(persona: VoicePersona, vocalize: Boolean) {
        try {
            ttsSpeaker.applyPersonaConfiguration(
                locale = Locale(persona.localeLanguage, persona.localeCountry),
                pitch = persona.pitch,
                rate = persona.speed
            )
            if (vocalize) {
                ttsSpeaker.speak(persona.greetingPhrase)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying voice persona: ${e.message}")
        }
    }
}
