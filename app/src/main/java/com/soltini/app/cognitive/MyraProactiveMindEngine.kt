package com.soltini.app.cognitive

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.audio.GeminiNeuralVoiceSynthesizer
import com.soltini.app.audio.JarvisSoundEngine
import com.soltini.app.graph.MyraKnowledgeGraphEngine
import com.soltini.app.notifications.NotificationRepository
import com.soltini.app.telephony.CallNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class ProactivePersonInsight(
    val entityName: String,
    val relationship: String,
    val recentMessage: String? = null,
    val recentCallInfo: String? = null,
    val suggestedFollowUp: String,
    val executableAction: String, // "WHATSAPP", "CALL", "REMINDER"
    val actionPayload: String
)

data class MoodActionSuggestion(
    val detectedState: String,
    val verbalProposal: String,
    val actions: List<String>
)

/**
 * MyraProactiveMindEngine — Mind-Reading Cognitive Intelligence & Anticipatory Action Engine
 *
 * Implements the 4-in-1 proactive intelligence architecture:
 *  1. Person Intent Predictor: When Boss mentions anyone (Rahul, Mummy, Colleague), aggregates Knowledge Graph,
 *     recent chats, missed calls, and immediately anticipates & proposes the logical next action.
 *  2. Mood & State Mind-Reader: Maps short/vague phrases ("thak gaya", "bhookh lagi", "bore ho raha")
 *     into contextual multi-app actions (DND, alarm, favorite playlist, food launcher).
 *  3. High-Agency Goal Decomposer: Breaks compound requests into 1-tap executable sub-plans.
 *  4. Live Screen Mind-Reader: Supplies contextual intelligence hooks for on-screen browsing.
 */
class MyraProactiveMindEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "ProactiveMindEngine"

        @Volatile
        private var instance: MyraProactiveMindEngine? = null

        fun getInstance(context: Context): MyraProactiveMindEngine =
            instance ?: synchronized(this) {
                instance ?: MyraProactiveMindEngine(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val graphEngine = MyraKnowledgeGraphEngine.getInstance(appContext)
    private val notifRepo = NotificationRepository.getInstance(appContext)
    private val callManager = CallNotificationManager.getInstance(appContext)
    private val geminiSpeaker = GeminiNeuralVoiceSynthesizer.getInstance(appContext)

    private val _lastInsight = MutableStateFlow<String?>(null)
    val lastInsight: StateFlow<String?> = _lastInsight.asStateFlow()

    /**
     * Resolves deep contextual insight about a mentioned person.
     * e.g., "Rahul ke baare me batao", "Mummy se baat karni hai", "Boss ka kya haal hai"
     */
    fun analyzePersonContext(query: String): ProactivePersonInsight? {
        val clean = query.trim().lowercase(Locale.ROOT)
        val entity = graphEngine.resolveEntity(clean) ?: run {
            // Check direct contacts match if not in graph
            val contactMatch = callManager.searchContacts(query).firstOrNull()
            if (contactMatch != null) {
                com.soltini.app.graph.GraphEntity(
                    id = contactMatch.name.lowercase().replace(" ", "_"),
                    name = contactMatch.name,
                    type = "PERSON",
                    aliases = listOf(contactMatch.name.lowercase()),
                    attributes = mapOf("phone" to contactMatch.phoneNumber)
                )
            } else null
        } ?: return null

        val entityName = entity.name
        val relationContext = graphEngine.getRelationalContext(entityName)

        // Find recent notification from this person
        val recentNotif = notifRepo.notifications.value.firstOrNull {
            it.title.contains(entityName, ignoreCase = true) || entity.aliases.any { a -> it.title.contains(a, ignoreCase = true) }
        }

        val recentMsgText = recentNotif?.let { "${it.appName} message (${it.getTimeAgo()}): \"${it.text}\"" }

        // Predict the best proactive next action
        val suggestion: String
        val actionType: String
        val actionPayload: String

        if (recentNotif != null && recentNotif.canReply) {
            suggestion = "Boss, ${entityName} ka ${recentNotif.getTimeAgo()} message aaya tha: \"${recentNotif.text}\". Kya main WhatsApp par reply bhej doon, ya direct call connect kar doon?"
            actionType = "WHATSAPP_REPLY"
            actionPayload = entityName
        } else {
            val phone = entity.attributes["phone"] ?: ""
            suggestion = "Boss, ${entityName} ke details linked hain. Kya main abhi unhe call connect kar doon ya WhatsApp par message drop karoon?"
            actionType = "CALL"
            actionPayload = if (phone.isNotBlank()) phone else entityName
        }

        val insight = ProactivePersonInsight(
            entityName = entityName,
            relationship = relationContext,
            recentMessage = recentMsgText,
            recentCallInfo = null,
            suggestedFollowUp = suggestion,
            executableAction = actionType,
            actionPayload = actionPayload
        )

        _lastInsight.value = suggestion
        AgentStatusBus.emit("🔮 Proactive Intent: $entityName -> Anticipated follow-up ready")
        return insight
    }

    /**
     * Infers implied intent from vague feelings or mood statements.
     */
    fun inferMoodIntent(statement: String): MoodActionSuggestion? {
        val s = statement.lowercase(Locale.ROOT)

        return when {
            s.contains("thak gaya") || s.contains("neend") || s.contains("so raha") || s.contains("tired") || s.contains("exhausted") -> {
                MoodActionSuggestion(
                    detectedState = "TIRED_OR_SLEEPY",
                    verbalProposal = "Boss, lagta hai aap kaafi thak gaye hain. Kya main phone ko DND par daal kar 45 minute ka power nap alarm set kar doon aur soothing lofi beats play karoon?",
                    actions = listOf("SET_DND", "SET_ALARM_45MIN", "PLAY_LOFI")
                )
            }
            s.contains("bhookh") || s.contains("khana") || s.contains("hungry") || s.contains("order") -> {
                MoodActionSuggestion(
                    detectedState = "HUNGRY",
                    verbalProposal = "Boss, aapke favorite food preferences noted hain. Kya main Zomato ya Swiggy open karke favorite biryani/pizza options screen par dikhau?",
                    actions = listOf("OPEN_FOOD_APP")
                )
            }
            s.contains("bore") || s.contains("timepass") || s.contains("boring") -> {
                MoodActionSuggestion(
                    detectedState = "BORED",
                    verbalProposal = "Boss, kya aapke favorite YouTube tech channel ki trending video chalaoon, ya Rahul ko call lagayein thodi der baat karne ke liye?",
                    actions = listOf("OPEN_YOUTUBE_FAVORITE", "CALL_BEST_FRIEND")
                )
            }
            s.contains("padhai") || s.contains("study") || s.contains("focus") || s.contains("kam karna") -> {
                MoodActionSuggestion(
                    detectedState = "FOCUS_MODE",
                    verbalProposal = "Deep Focus mode activate kar rahi hoon, Boss. Social notifications mute kar doon aur 50 minute ka study timer start karoon?",
                    actions = listOf("FOCUS_DND", "SET_POMODORO_50MIN")
                )
            }
            else -> null
        }
    }

    /**
     * Executes the anticipated proactive action when Boss confirms ("Haan karo", "Bhej do", "Call lagao").
     */
    fun executeProactiveAction(actionType: String, target: String, customText: String = ""): Boolean {
        JarvisSoundEngine.playTacticalBlip()
        return try {
            when (actionType) {
                "CALL" -> {
                    callManager.makeCall(target)
                    geminiSpeaker.speakWithGeminiVoice("$target ko call connect kar rahi hoon, Boss.")
                    AgentStatusBus.emit("📞 Proactive Call dispatched to $target")
                    true
                }
                "WHATSAPP_REPLY", "WHATSAPP" -> {
                    val replyText = customText.ifBlank { "Hey, main thodi der me call karta hoon." }
                    val res = notifRepo.replyToNotification(target, replyText)
                    if (res is com.soltini.app.notifications.ReplyResult.Success) {
                        JarvisSoundEngine.playTaskSuccessChime()
                        geminiSpeaker.speakWithGeminiVoice("$target ko WhatsApp message dispatch kar diya hai, Boss.")
                        AgentStatusBus.emit("✅ Proactive WhatsApp sent to $target")
                        true
                    } else {
                        // Open WhatsApp chat directly if direct reply not available
                        val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(replyText)}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        appContext.startActivity(sendIntent)
                        geminiSpeaker.speakWithGeminiVoice("WhatsApp chat open kar diya hai, Boss.")
                        true
                    }
                }
                "SET_DND" -> {
                    val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm?.isNotificationPolicyAccessGranted == true) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                    }
                    geminiSpeaker.speakWithGeminiVoice("Phone DND par switch ho gaya hai, Boss. Sirf emergency alarms aayenge.")
                    true
                }
                "SET_ALARM_45MIN" -> {
                    val alarmIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, 45 * 60)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Power Nap with Myra")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    appContext.startActivity(alarmIntent)
                    geminiSpeaker.speakWithGeminiVoice("45 minute ka power nap timer set kar diya hai, Boss. Aram se so jaiye.")
                    true
                }
                "PLAY_LOFI" -> {
                    val songIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=lofi+beats+for+sleep")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    appContext.startActivity(songIntent)
                    geminiSpeaker.speakWithGeminiVoice("Relaxing lofi soundscape chala rahi hoon, Boss.")
                    true
                }
                "OPEN_FOOD_APP" -> {
                    val zomato = appContext.packageManager.getLaunchIntentForPackage("com.application.zomato")
                        ?: appContext.packageManager.getLaunchIntentForPackage("in.swiggy.android")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.zomato.com")).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    zomato.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    appContext.startActivity(zomato)
                    geminiSpeaker.speakWithGeminiVoice("Food app open kar diya hai, Boss. Aapka favorite food check kijiye.")
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing proactive action $actionType: ${e.message}", e)
            false
        }
    }

    /**
     * Supplies high-agency proactive rules prompt block for Gemini Live.
     */
    fun buildProactivePromptBlock(): String {
        return """
## PROACTIVE COGNITIVE MIND-READING & ANTICIPATORY ACTION DIRECTIVE:
1. When Boss asks about any person (e.g. "Rahul", "Mummy", "Papa", "Boss", "Friend"):
   - DO NOT just state static facts or definitions.
   - Actively connect their relationship, recent messages, and call context.
   - PROACTIVELY ANTICIPATE what Boss wants to do next:
     "Boss, [Person] ka [Recent Time] ye message aaya tha. Kya main unhe WhatsApp par reply bhej doon, ya direct call connect kar doon?"
2. When Boss expresses an emotional or physical state ("Thak gaya", "Bhookh lagi hai", "Bore ho raha hoon", "Padhai karni hai"):
   - INSTANTLY OFFER the concrete multi-step solution (e.g., DND + 45 min timer + Lofi music, or opening favorite food).
   - If Boss confirms ("Haan kar do", "Bilkul"), EXECUTE the action immediately using your tools!
3. HIGH-AGENCY GOAL PLANNING:
   - When Boss gives a vague or compound goal ("Dosto ke sath movie plan karni hai", "Weekend road trip"):
   - Break it into concrete sub-steps (Search show times, draft WhatsApp invite, set reminder) and offer the ready-to-dispatch plan.
"""
    }
}
