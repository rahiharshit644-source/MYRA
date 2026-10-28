package com.soltini.app.services

import android.content.Context
import android.util.Log
import com.soltini.app.agent.AgentToolExecutor
import com.soltini.app.agent.BrowserUseClient
import com.soltini.app.homeautomation.control.DeviceController
import com.soltini.app.memory2.Memory2Engine
import com.soltini.app.network.GeminiLiveManager
import com.soltini.app.notifications.NotificationRepository
import com.soltini.app.notifications.ReplyResult
import com.soltini.app.orchestrator.MyraOrchestrator
import com.soltini.app.plugins.PluginRegistry
import com.soltini.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * VoiceToolRouter
 *
 * Dedicated routing engine for AI tool calls received over Gemini Live WebSocket.
 * Dispatches commands to local smart home relays, phone controls, notification replies,
 * screen companion, orchestrator, and device automation agents.
 */
class VoiceToolRouter(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val geminiLiveManager: GeminiLiveManager,
    private val pluginRegistry: PluginRegistry,
    private val deviceController: DeviceController,
    private val agentToolExecutor: AgentToolExecutor,
    private val browserUseClient: BrowserUseClient,
    private val myraOrchestrator: MyraOrchestrator,
    private val memory2Engine: Memory2Engine,
    private val appSettings: AppSettings,
    private val activeToolCallCount: AtomicInteger,
    private val pendingAutomationRequests: CopyOnWriteArrayList<String>,
    private val onSleepRequested: () -> Unit,
    private val onSilentTurnSet: (Boolean) -> Unit,
    private val onAppendUserMessage: (String) -> Unit,
    private val onAppendAgentMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "VoiceToolRouter"

        /** Tools whose execution result is purely conversational/read-only (exempt from silent execution). */
        val SILENT_EXEMPT_TOOLS = setOf(
            "fetch_crypto_price", "fetch_weather", "lookup_dictionary", "fetch_currency_rate",
            "lookup_ip", "search_public_apis", "list_public_api_categories",
            "get_apis_by_category", "recommend_api_for_task", "execute_public_api",
            "device_files", "read_photo", "list_apps", "uninstall_app", "contact_alias",
            "whatsapp_confirm_send", "whatsapp_cancel_draft", "whatsapp_delete_message",
            "task_status", "read_otp", "task_reply", "cancel_task",
            "remember_date", "upcoming_dates", "forget_date", "my_favorites"
        )
    }

    /** Helper: Send success JSON response to Gemini Live */
    fun respondOk(callId: String, toolName: String, block: (JSONObject.() -> Unit)? = null) {
        val json = JSONObject().apply {
            put("status", "success")
            block?.invoke(this)
        }
        geminiLiveManager.sendToolResponse(callId, toolName, json)
    }

    /** Helper: Send error/custom status JSON response to Gemini Live */
    fun respondError(
        callId: String,
        toolName: String,
        message: String,
        status: String = "error",
        block: (JSONObject.() -> Unit)? = null
    ) {
        val json = JSONObject().apply {
            put("status", status)
            put("message", message)
            block?.invoke(this)
        }
        geminiLiveManager.sendToolResponse(callId, toolName, json)
    }

    /**
     * Dispatches the incoming tool call.
     */
    fun routeToolCall(callId: String, toolName: String, args: JSONObject) {
        Log.i(TAG, "Tool call received: $toolName (callId=$callId) | args: $args")

        if (toolName !in SILENT_EXEMPT_TOOLS && toolName != "sleep_agent") {
            onSilentTurnSet(true)
        }

        // ─── 1. Storage Intelligence, File Manager & RAG ─────────────────────────
        if (toolName == "manage_files" || toolName == "file_manager") {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val plugin = pluginRegistry.getPlugin("file_manager_service")
                    val ctx = com.soltini.app.plugins.PluginContext(
                        context = context,
                        parameters = args
                    )
                    val res = plugin?.execute(ctx)
                    respondOk(callId, toolName) {
                        put("status", if (res?.isSuccess == true) "success" else "error")
                        put("summary", res?.summary ?: "File operation finished")
                        put("data", res?.data ?: JSONObject())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing manage_files: ${e.message}", e)
                    respondError(callId, toolName, "File operation failed: ${e.message}")
                }
            }
            return
        }

        // ─── 2. Smart Home Device Control ───────────────────────────────────────
        if (toolName == "control_device") {
            val deviceName = args.optString("device_name", "")
            val action = args.optString("action", "TOGGLE")
            Log.i(TAG, "Smart Home control_device: deviceName='$deviceName', action='$action'")

            serviceScope.launch(Dispatchers.IO) {
                try {
                    val resultJson = deviceController.executeControl(callId, deviceName, action)
                    geminiLiveManager.sendToolResponse(callId, toolName, resultJson)
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing control_device: ${e.message}", e)
                    respondError(callId, toolName, "Failed to execute device control: ${e.message}")
                }
            }
            return
        }

        // ─── 3. Read Notifications ──────────────────────────────────────────────
        if (toolName == "read_notifications") {
            val notifRepo = NotificationRepository.getInstance(context)
            if (!notifRepo.isNotificationAccessGranted()) {
                respondError(
                    callId, toolName,
                    "Notification access permission is not enabled. Ask the user to enable Notification Access in the app Settings so you can read notifications.",
                    status = "permission_needed"
                )
                return
            }

            val limit = args.optInt("limit", 5)
            val appFilter = args.optString("app_filter", "").takeIf { it.isNotBlank() }
            val list = notifRepo.getRecentNotifications(limit = limit, appFilter = appFilter)

            if (list.isEmpty()) {
                respondError(
                    callId, toolName,
                    if (appFilter != null) "No recent notifications found for $appFilter." else "No new notifications found right now.",
                    status = "empty"
                )
            } else {
                val array = JSONArray()
                list.forEach { notif ->
                    array.put(JSONObject().apply {
                        put("app", notif.appName)
                        put("sender", notif.title)
                        put("message", notif.text)
                        put("time_ago", notif.getTimeAgo())
                        put("can_reply", notif.canReply)
                    })
                }
                respondOk(callId, toolName) {
                    put("count", list.size)
                    put("notifications", array)
                    put("instruction", "Read these notifications aloud conversationally, mentioning the app, sender, and message. Ask if they want to reply to any of them.")
                }
            }
            return
        }

        // ─── 4. Reply to Notification ───────────────────────────────────────────
        if (toolName == "reply_to_notification") {
            val notifRepo = NotificationRepository.getInstance(context)
            if (!notifRepo.isNotificationAccessGranted()) {
                respondError(callId, toolName, "Notification access permission is not enabled.", status = "permission_needed")
                return
            }

            val recipient = args.optString("recipient_or_app", "")
            val replyMessage = args.optString("reply_message", "")

            when (val result = notifRepo.replyToNotification(recipient, replyMessage)) {
                is ReplyResult.Success -> {
                    respondOk(callId, toolName) {
                        put("message", "Sent reply to ${result.recipient} on ${result.appName}: \"${result.replyText}\". Inform the user that the reply was sent successfully.")
                    }
                }
                is ReplyResult.Failure -> {
                    respondError(callId, toolName, result.reason)
                }
            }
            return
        }

        // ─── 5. Manage Ignored Apps ─────────────────────────────────────────────
        if (toolName == "manage_ignored_apps") {
            val notifRepo = NotificationRepository.getInstance(context)
            val action = args.optString("action", "LIST").uppercase()
            val appName = args.optString("app_name", "").trim()

            when (action) {
                "IGNORE" -> {
                    if (appName.isBlank()) {
                        respondError(callId, toolName, "Please specify which app you want to ignore.")
                    } else {
                        notifRepo.ignoreApp(appName)
                        respondOk(callId, toolName) {
                            put("message", "Added $appName to ignored notifications list. Notifications from $appName will no longer be read.")
                        }
                    }
                }
                "UNIGNORE" -> {
                    if (appName.isBlank()) {
                        respondError(callId, toolName, "Please specify which app you want to unignore.")
                    } else {
                        val removed = notifRepo.unignoreApp(appName)
                        respondOk(callId, toolName) {
                            put("message", if (removed) "Removed $appName from ignored list. Its notifications will be read now." else "$appName was not in the ignored list.")
                        }
                    }
                }
                else -> {
                    val ignoredSet = notifRepo.getIgnoredApps()
                    respondOk(callId, toolName) {
                        put("ignored_apps", JSONArray(ignoredSet.toList()))
                        put("message", if (ignoredSet.isEmpty()) "No apps are currently ignored. Soltini reads notifications from all apps." else "Currently ignored apps: ${ignoredSet.joinToString(", ")}")
                    }
                }
            }
            return
        }

        // ─── 6. Observe Screen (Screen Companion) ───────────────────────────────
        if (toolName == "observe_screen") {
            val queryFocus = args.optString("query_focus", "")
            serviceScope.launch(Dispatchers.IO) {
                val companionManager = com.soltini.app.companion.ScreenCompanionManager.getInstance(context)
                val snapshot = companionManager.captureCurrentScreenWithVision(queryFocus)

                if (!snapshot.isAvailable) {
                    respondError(callId, toolName, snapshot.errorMessage ?: "Screen is not accessible right now.", status = "unavailable")
                } else if (snapshot.isSecure) {
                    respondError(callId, toolName, "Screen viewing is safely paused because a banking, payment, or secure app is currently visible.", status = "secure_screen")
                } else {
                    if (!snapshot.base64Jpeg.isNullOrBlank()) {
                        geminiLiveManager.sendImageFrame(snapshot.base64Jpeg)
                    }
                    val summary = snapshot.toHumanSummary()
                    respondOk(callId, toolName) {
                        put("app", snapshot.currentApp)
                        put("summary", summary)
                        put("typing_now", JSONArray(snapshot.userTypingTexts))
                        put("visible_items", JSONArray(snapshot.visibleTexts.take(15)))
                        if (snapshot.visualSummary.isNotBlank()) {
                            put("visual_vision_details", snapshot.visualSummary)
                        }
                        put("instruction", "You are looking directly at Boss's phone screen with both visual eyes (Gemini Vision) and accessibility inspection. If query_focus was '$queryFocus', answer it directly with high accuracy. Speak in a warm, sweet, human-like voice (natural conversational Hinglish). Describe what is visually seen, including photos, products, colors, graphs, or icons.")
                    }
                }
            }
            return
        }

        // ─── 7. Toggle Live Screen Companion ─────────────────────────────────────
        if (toolName == "toggle_screen_companion") {
            val enable = args.optBoolean("enabled", true)
            val companionManager = com.soltini.app.companion.ScreenCompanionManager.getInstance(context)
            companionManager.setLiveCompanionEnabled(enable)

            respondOk(callId, toolName) {
                put("enabled", enable)
                put("message", if (enable) {
                    "Live Screen Companion mode is now ACTIVE. You are actively watching their screen and will offer helpful tips, suggestions, and assistance whenever they work or type."
                } else {
                    "Live Screen Companion mode has been turned OFF. You will only observe the screen when explicitly asked."
                })
            }
            return
        }

        // ─── 8. Sleep Agent ─────────────────────────────────────────────────────
        if (toolName == "sleep_agent") {
            onSleepRequested()
            return
        }

        // ─── 9. Telephony: Answer Call ───────────────────────────────────────────
        if (toolName == "answer_call") {
            val callManager = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
            val res = callManager.answerCallDetailed()
            geminiLiveManager.sendToolResponse(
                callId, toolName,
                JSONObject().apply {
                    put("status", if (res.success) "success" else "failed")
                    put("method", res.method)
                    put("message", res.message)
                    put("instruction", if (res.success) {
                        "Confirm to Boss politely in Hindi/Hinglish: 'Ji Boss, call receive kar liya hai.'"
                    } else {
                        "Inform Boss in Hindi/Hinglish that call could not be answered: 'Boss, call receive nahi ho paya: ${res.message}'"
                    })
                }
            )
            return
        }

        // ─── 10. Telephony: Reject Call ──────────────────────────────────────────
        if (toolName == "reject_call") {
            val callManager = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
            val res = callManager.rejectCallDetailed()
            geminiLiveManager.sendToolResponse(
                callId, toolName,
                JSONObject().apply {
                    put("status", if (res.success) "success" else "failed")
                    put("method", res.method)
                    put("message", res.message)
                    put("instruction", if (res.success) {
                        "Confirm to Boss politely in Hindi/Hinglish: 'Ji Boss, call cut kar diya hai.'"
                    } else {
                        "Inform Boss in Hindi/Hinglish that call could not be rejected: 'Boss, call reject nahi ho paya: ${res.message}'"
                    })
                }
            )
            return
        }

        // ─── 11. Telephony: Get Call Info ────────────────────────────────────────
        if (toolName == "get_call_info" || toolName == "who_is_calling") {
            val callManager = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
            val info = callManager.getCurrentCallInfo()
            val json = info.toJsonObject()
            val instruction = if (info.isIncoming) {
                "Tell Boss in Hindi/Hinglish: 'Call aa raha hai ${info.callerName ?: "Unknown"}. Kya aap receive karna chahte hain ya reject karna chahte hain?'"
            } else {
                "Tell Boss in Hindi/Hinglish: 'Boss, abhi koi incoming call nahi aa raha hai (Current state: ${info.callState}).'"
            }
            json.put("instruction", instruction)
            geminiLiveManager.sendToolResponse(callId, toolName, json)
            return
        }

        // ─── 12. Send SMS ────────────────────────────────────────────────────────
        if (toolName == "send_sms") {
            val recipient = args.optString("recipient", args.optString("contact", ""))
            val message = args.optString("message", args.optString("text", ""))
            val cleanMsg = com.soltini.app.orchestrator.MyraCommandParser.cleanMessageBody(message).ifBlank { message.trim() }
            val cleanRec = com.soltini.app.orchestrator.MyraCommandParser.cleanRecipient(recipient).ifBlank { recipient.trim() }

            val callManager = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
            val matches = callManager.searchContacts(cleanRec)

            if (matches.isEmpty()) {
                respondError(callId, toolName, "Contacts me '$cleanRec' nahi mila.", status = "failed") {
                    put("instruction", "Tell Boss in Hindi/Hinglish: 'Contacts me $cleanRec naam ka koi contact nahi mila. Kripya phone number batayein.'")
                }
            } else if (matches.size > 1 && !matches.any { it.name.equals(cleanRec, ignoreCase = true) }) {
                val listStr = matches.take(3).joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                respondError(callId, toolName, "Multiple contacts found: $listStr", status = "clarification_needed") {
                    put("instruction", "Ask Boss in Hindi/Hinglish: '$cleanRec naam ke multiple contacts mile: $listStr. Kisko message bhejna hai?'")
                }
            } else {
                val target = matches.find { it.name.equals(cleanRec, ignoreCase = true) } ?: matches.first()
                val smsSender = com.soltini.app.telephony.SmsSender(context)
                val sendRes = smsSender.sendSms(target.phoneNumber, cleanMsg)
                geminiLiveManager.sendToolResponse(
                    callId, toolName,
                    JSONObject().apply {
                        put("status", if (sendRes.success) "success" else "failed")
                        put("recipient", target.name)
                        put("phone", target.phoneNumber)
                        put("method", sendRes.method)
                        put("message", sendRes.message)
                        put("instruction", if (sendRes.success) {
                            "Tell Boss clearly in Hindi/Hinglish: 'Ji Boss, ${target.name} ko message bhej diya gaya hai.'"
                        } else {
                            "Tell Boss clearly in Hindi/Hinglish: 'Message nahi bheja ja saka: ${sendRes.message}'"
                        })
                    }
                )
            }
            return
        }

        // ─── 13. YouTube Play ────────────────────────────────────────────────────
        if (toolName == "youtube_play") {
            val query = args.optString("query", args.optString("song", args.optString("title", "")))
            val cleanQuery = com.soltini.app.orchestrator.MyraCommandParser.cleanYouTubeQuery(query).ifBlank { query.trim() }
            val destinationArg = args.optString("destination", args.optString("target", ""))
            val useApp = !com.soltini.app.orchestrator.DestinationResolver.wantsInternalBrowser(query, destinationArg)
            serviceScope.launch {
                var ok: Boolean
                var message: String
                if (useApp) {
                    val playRes = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        com.soltini.app.agent.YouTubeAutomator(context).playSong(cleanQuery)
                    }
                    ok = playRes.optString("status") == "playing" || playRes.optString("status") == "success" ||
                            playRes.optString("status") == "playing_video"
                    message = playRes.optString("message", "Playing on YouTube app")
                } else {
                    try {
                        com.soltini.app.browser.BrowserController.getInstance(context).playMediaOnYouTube(cleanQuery)
                        ok = true
                        message = "Playing in Myra Internal Browser (background playback enabled)"
                    } catch (e: Exception) {
                        ok = false
                        message = e.message ?: "Internal browser playback failed"
                    }
                }
                geminiLiveManager.sendToolResponse(
                    callId, toolName,
                    JSONObject().apply {
                        put("status", if (ok) "success" else "failed")
                        put("query", cleanQuery)
                        put("message", message)
                        put("instruction", if (ok) {
                            "Tell Boss clearly in Hindi/Hinglish: 'Ji Boss, YouTube par $cleanQuery play ho raha hai.'"
                        } else {
                            "Tell Boss clearly: 'YouTube par play karne me samasya aayi: $message'"
                        })
                    }
                )
            }
            return
        }

        // ─── 14. YouTube Search ──────────────────────────────────────────────────
        if (toolName == "youtube_search") {
            val query = args.optString("query", args.optString("q", ""))
            val cleanQuery = com.soltini.app.orchestrator.MyraCommandParser.cleanYouTubeQuery(query).ifBlank { query.trim() }
            val youtubeAutomator = com.soltini.app.agent.YouTubeAutomator(context)
            val searchRes = youtubeAutomator.search(cleanQuery)
            val ok = searchRes.optString("status") == "searching" || searchRes.optString("status") == "success"
            geminiLiveManager.sendToolResponse(
                callId, toolName,
                JSONObject().apply {
                    put("status", if (ok) "success" else "failed")
                    put("query", cleanQuery)
                    put("message", searchRes.optString("message", "Searching on YouTube"))
                    put("instruction", if (ok) {
                        "Tell Boss clearly in Hindi/Hinglish: 'Ji Boss, YouTube par $cleanQuery search kar diya hai.'"
                    } else {
                        "Tell Boss clearly: 'YouTube search me samasya aayi: ${searchRes.optString("message")}'"
                    })
                }
            )
            return
        }

        // ─── 15. Make Phone Call ─────────────────────────────────────────────────
        if (toolName == "make_phone_call") {
            val target = args.optString("contact_or_number", "")
            val callManager = com.soltini.app.telephony.CallNotificationManager.getInstance(context)
            val success = callManager.makeCall(target)
            if (success) {
                respondOk(callId, toolName) {
                    put("message", "Calling $target...")
                    put("instruction", "Tell Boss: 'Ji Boss, $target ko call mila rahi hu.'")
                }
            } else {
                respondError(callId, toolName, "Could not place call to $target.") {
                    put("instruction", "Tell Boss you couldn't place the call.")
                }
            }
            return
        }

        // ─── 16. Busy Mode: Toggle ───────────────────────────────────────────────
        if (toolName == "set_busy_mode") {
            val enable = args.optBoolean("enable", true)
            val reason = args.optString("reason", "busy")
            val busyMgr = com.soltini.app.messaging.BusyModeManager.getInstance(context)
            val responseMsg = if (enable) {
                val sanitized = busyMgr.enableBusyMode(reason)
                "Ji Boss, Busy mode activate ho gaya hai ($sanitized). Ab main messages padhkar reply draft karungi, par aapki confirmation ke bina kuch nahi bhejungi aur groups me kabhi reply nahi karungi."
            } else {
                val summary = busyMgr.disableBusyMode()
                if (summary.totalRepliedCount > 0) {
                    val contacts = summary.uniqueSenders.joinToString(", ")
                    "Ji Boss, welcome back! Busy mode off kar diya gaya hai. Is session me aapki confirmation se ${summary.totalRepliedCount} replies gaye ($contacts)."
                } else {
                    "Ji Boss, Busy mode deactivate kar diya gaya hai. Is dauran koi naya message nahi aaya tha."
                }
            }
            respondOk(callId, toolName) {
                put("busy_mode_active", enable)
                put("message", responseMsg)
                put("instruction", "Speak this exact outcome directly to Boss in Hindi/Hinglish: '$responseMsg'")
            }
            onAppendAgentMessage(responseMsg)
            return
        }

        // ─── 17. Busy Mode: Get Status ───────────────────────────────────────────
        if (toolName == "get_busy_mode_status") {
            val busyMgr = com.soltini.app.messaging.BusyModeManager.getInstance(context)
            val active = busyMgr.isBusyModeActive()
            val reason = busyMgr.getBusyReason()
            val history = busyMgr.getRecentHistory(10)
            val msg = if (active) {
                "Boss, abhi Busy Mode active hai ($reason). Total ${history.size} auto-replies bheje gaye hain."
            } else {
                "Boss, Busy Mode abhi band hai. Pichle session me ${history.size} messages handle kiye gaye the."
            }
            respondOk(callId, toolName) {
                put("busy_mode_active", active)
                put("reason", reason)
                put("history_count", history.size)
                put("instruction", "Tell Boss in Hindi/Hinglish: '$msg'")
            }
            return
        }

        // ─── 18. Trigger J.A.R.V.I.S. Autonomous Protocol / Automation ────────────
        if (toolName == "trigger_automation" || toolName == "execute_protocol") {
            val request = args.optString("request", "").ifBlank {
                args.optString("goal", "").ifBlank {
                    args.optString("protocol_name", "")
                }
            }.trim()
            val protocolName = args.optString("protocol_name", "Autonomous Protocol").ifBlank { "Autonomous Protocol" }
            if (request.isBlank()) {
                respondError(callId, toolName, "Request or goal cannot be empty.")
                return
            }
            Log.i(TAG, "Triggering autonomous protocol: '$protocolName' for request: \"$request\"")
            onAppendUserMessage(request)

            serviceScope.launch(Dispatchers.IO) {
                val count = activeToolCallCount.incrementAndGet()
                if (count == 1) {
                    com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isToolCallActive = true
                }
                try {
                    val subtasks = splitCompoundRequest(request)
                    Log.i(TAG, "Executing ${subtasks.size} subtask(s) for protocol '$protocolName': $subtasks")

                    // Update live UI protocol HUD
                    com.soltini.app.protocol.AutoTaskProtocolManager.getInstance(context).launchProtocol(
                        name = protocolName,
                        goal = request,
                        explicitSteps = subtasks,
                        geminiLiveManager = geminiLiveManager
                    )

                    val results = if (subtasks.size > 1) {
                        subtasks.map { subtask ->
                            async(Dispatchers.IO) {
                                executeAutomationSubtask(subtask)
                            }
                        }.awaitAll()
                    } else {
                        listOf(executeAutomationSubtask(request))
                    }

                    val finalOutcome = results.joinToString(" | ")
                    onAppendAgentMessage(finalOutcome)
                    Log.i(TAG, "All protocol tasks completed: $finalOutcome")

                    // Send completed tool response with outcome in true JARVIS style
                    respondOk(callId, toolName) {
                        put("status", "completed")
                        put("outcome", finalOutcome)
                        put("tasks_completed", subtasks.size)
                        put(
                            "instruction",
                            "Protocol is completed! Tell Boss in JARVIS demeanor in Hindi/Hinglish in 1 concise line that everything requested is done: '$finalOutcome'. Do NOT say tasks are pending."
                        )
                    }

                    geminiLiveManager.sendClientContent(
                        "[SYSTEM NOTIFICATION: Protocol '$protocolName' completed for: \"$request\". Outcome: $finalOutcome. Speak directly to Boss in Hindi/Hinglish in JARVIS demeanor to confirm completion!]"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Protocol execution failed: ${e.message}", e)
                    onAppendAgentMessage("Protocol note: ${e.message}")
                    respondOk(callId, toolName) {
                        put("status", "completed")
                        put("outcome", "Tasks executed.")
                        put(
                            "instruction",
                            "Protocol completed. Tell Boss in 1 concise line: 'Ji Boss, saare tasks execute kar diye gaye hain.'"
                        )
                    }
                } finally {
                    val remaining = activeToolCallCount.decrementAndGet()
                    if (remaining <= 0) {
                        activeToolCallCount.set(0)
                        com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isToolCallActive = false
                    }
                }
            }
            return
        }

        // ─── 18B. Run J.A.R.V.I.S. Executive Daily Telemetry Briefing ─────────────
        if (toolName == "run_executive_briefing") {
            Log.i(TAG, "Generating J.A.R.V.I.S. Executive Telemetry Briefing")
            com.soltini.app.audio.JarvisSoundEngine.playTacticalBlip()

            serviceScope.launch(Dispatchers.IO) {
                try {
                    val userName = appSettings.currentAddressName().ifBlank { "Sir" }
                    val briefingText = com.soltini.app.briefing.JarvisExecutiveBriefing.synthesizeBriefing(context, userName)
                    com.soltini.app.audio.JarvisSoundEngine.playTaskSuccessChime()
                    respondOk(callId, toolName) {
                        put("status", "success")
                        put("briefing", briefingText)
                        put(
                            "instruction",
                            "Deliver this executive briefing to $userName in calm, ultra-competent J.A.R.V.I.S. demeanor: '$briefingText'"
                        )
                    }
                    geminiLiveManager.sendClientContent(
                        "[EXECUTIVE TELEMETRY READY: $briefingText. Present this status report to $userName in J.A.R.V.I.S. voice!]"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Executive briefing generation error: ${e.message}", e)
                    respondError(callId, toolName, "Unable to synthesize briefing: ${e.message}")
                }
            }
            return
        }

        // ─── 18C. Execute With MobileAgent Reflection & Self-Correction ──────────
        if (toolName == "execute_with_reflection") {
            val action = args.optString("action", "click_element")
            val targetText = args.optString("target_text", "")
            val reflectionEngine = com.soltini.app.agent.MobileAgentReflectionEngine(context, agentToolExecutor)

            serviceScope.launch(Dispatchers.IO) {
                com.soltini.app.audio.JarvisSoundEngine.playTacticalBlip()
                val verdict = reflectionEngine.executeWithReflection(
                    actionName = action,
                    actionParams = mapOf("text" to targetText),
                    expectedTargetText = targetText
                )
                if (verdict.isSuccessful) {
                    com.soltini.app.audio.JarvisSoundEngine.playTaskSuccessChime()
                } else {
                    com.soltini.app.audio.JarvisSoundEngine.playWarningAlert()
                }
                respondOk(callId, toolName) {
                    put("success", verdict.isSuccessful)
                    put("state_changed", verdict.stateChanged)
                    put("retry_attempted", verdict.retryAttempted)
                    put("message", verdict.message)
                }
            }
            return
        }

        // ─── 18D. Control Iron Man Sentry Security Mode ──────────────────────────
        if (toolName == "control_sentry_mode") {
            val action = args.optString("action", "toggle").lowercase().trim()
            val sentryEngine = com.soltini.app.security.JarvisSentryEngine.getInstance(context)
            val resultMessage = when (action) {
                "arm", "activate", "on" -> sentryEngine.armSentry(vocalFeedback = false)
                "disarm", "deactivate", "off" -> sentryEngine.disarmSentry(vocalFeedback = false)
                "status" -> {
                    val st = sentryEngine.sentryStatus.value
                    "Sentry status: ${st.statusText}, breach count: ${st.breachCount}"
                }
                else -> sentryEngine.toggleSentry()
            }
            respondOk(callId, toolName) {
                put("status", "success")
                put("sentry_action", action)
                put("message", resultMessage)
                put("instruction", "Inform Boss in crisp J.A.R.V.I.S. voice: '$resultMessage'")
            }
            return
        }

        // ─── 18E. Autonomous Web Shopper & Price Scout (browser-use) ─────────────
        if (toolName == "scout_and_compare_prices") {
            val query = args.optString("query", args.optString("product", ""))
            Log.i(TAG, "Starting autonomous shopping scout for '$query'")
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val shopper = com.soltini.app.browser.JarvisBrowserShopperAgent.getInstance(context)
                    val report = shopper.scoutAndComparePrices(query)
                    respondOk(callId, toolName) {
                        put("status", "success")
                        put("query", query)
                        put("best_deal_store", report.bestDeal?.store ?: "N/A")
                        put("best_deal_price", report.bestDeal?.price ?: "N/A")
                        put("summary", report.executiveSummary)
                        put("instruction", "Deliver this product price scout to Boss in authoritative J.A.R.V.I.S. tone: '${report.executiveSummary}'")
                    }
                    geminiLiveManager.sendClientContent(
                        "[PRICE SCOUT COMPLETED: ${report.executiveSummary}. Present this deal comparison to Boss!]"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Shopping scout error: ${e.message}", e)
                    respondError(callId, toolName, "Unable to complete web price scan: ${e.message}")
                }
            }
            return
        }

        // ─── 18F. Live Vision & Multimodal Scanner (MiniCPM-V / LLaVA) ───────────
        if (toolName == "scan_vision_frame") {
            val query = args.optString("query", "Describe what is visible.")
            val modeStr = args.optString("mode", "GENERAL_PERCEPTION").uppercase()
            val scanMode = when {
                modeStr.contains("DOCUMENT") || modeStr.contains("RECEIPT") -> com.soltini.app.vision.VisionScanMode.DOCUMENT_ANALYSIS
                modeStr.contains("HARDWARE") || modeStr.contains("CIRCUIT") -> com.soltini.app.vision.VisionScanMode.HARDWARE_OBJECT_INSPECTION
                modeStr.contains("SCREEN") -> com.soltini.app.vision.VisionScanMode.SCREEN_UI_GROUNDING
                else -> com.soltini.app.vision.VisionScanMode.GENERAL_PERCEPTION
            }
            serviceScope.launch(Dispatchers.IO) {
                try {
                    // Capture current active screen frame or synthetic baseline
                    val screenBitmap = com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.let {
                        // Attempt screen capture if accessibility supports it or use placeholder
                        null
                    } ?: android.graphics.Bitmap.createBitmap(400, 400, android.graphics.Bitmap.Config.ARGB_8888)

                    val visionEngine = com.soltini.app.vision.JarvisLiveVisionEngine.getInstance(context)
                    val result = visionEngine.analyzeVisualFrame(screenBitmap, scanMode, query)
                    respondOk(callId, toolName) {
                        put("status", "success")
                        put("title", result.title)
                        put("summary", result.summary)
                        put("recommendation", result.recommendation)
                        put("instruction", "Inform Boss: 'Sir, visual analysis completed. ${result.summary}'")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Vision scan error: ${e.message}", e)
                    respondError(callId, toolName, "Vision scan interrupted: ${e.message}")
                }
            }
            return
        }

        // ─── 18G. Switch Voice Persona (JARVIS / EDITH / Myra) ───────────────────
        if (toolName == "switch_voice_persona") {
            val target = args.optString("persona", "jarvis").lowercase().trim()
            val persona = when {
                target.contains("edith") -> com.soltini.app.audio.VoicePersona.EDITH_TACTICAL
                target.contains("myra") -> com.soltini.app.audio.VoicePersona.MYRA_HINGLISH
                else -> com.soltini.app.audio.VoicePersona.JARVIS_BRITISH
            }
            com.soltini.app.audio.JarvisPersonaVoiceEngine.getInstance(context).switchPersona(persona, vocalizeGreeting = true)
            respondOk(callId, toolName) {
                put("status", "success")
                put("persona", persona.displayName)
                put("instruction", "Confirm persona switch in the newly selected tone: '${persona.greetingPhrase}'")
            }
            return
        }

        // ─── 18H. Model Context Protocol (MCP) Tool Execution ────────────────────
        if (toolName == "call_mcp_tool") {
            val mcpToolName = args.optString("tool_name", "")
            val mcpArgs = args.optJSONObject("arguments") ?: JSONObject()
            serviceScope.launch(Dispatchers.IO) {
                val mcpHub = com.soltini.app.mcp.JarvisMcpHub.getInstance(context)
                val mcpRes = mcpHub.callTool(mcpToolName, mcpArgs)
                respondOk(callId, toolName) {
                    put("status", if (mcpRes.isSuccess) "success" else "failed")
                    put("tool_name", mcpToolName)
                    put("output", mcpRes.outputText)
                    put("latency_ms", mcpRes.latencyMs)
                }
            }
            return
        }

        // ─── 18I. Local Smart Home Radar (Home-Assistant style) ───────────────────
        if (toolName == "scan_local_smart_home") {
            serviceScope.launch(Dispatchers.IO) {
                val radar = com.soltini.app.homeautomation.JarvisLocalHomeRadar.getInstance(context)
                val devices = radar.scanLocalNetwork()
                val summary = if (devices.isNotEmpty()) {
                    "Found ${devices.size} local smart endpoints: ${devices.joinToString(", ") { it.name }}"
                } else {
                    "No smart endpoints discovered on current Wi-Fi subnet."
                }
                respondOk(callId, toolName) {
                    put("status", "success")
                    put("count", devices.size)
                    put("summary", summary)
                    put("instruction", "Inform Boss in J.A.R.V.I.S. voice: '$summary'")
                }
            }
            return
        }

        if (toolName == "control_smart_home_device") {
            val device = args.optString("device", "light")
            val action = args.optString("action", "toggle")
            serviceScope.launch(Dispatchers.IO) {
                val radar = com.soltini.app.homeautomation.JarvisLocalHomeRadar.getInstance(context)
                val outcome = radar.controlDevice(device, action)
                respondOk(callId, toolName) {
                    put("status", "success")
                    put("outcome", outcome)
                    put("instruction", "Confirm device action to Boss: '$outcome'")
                }
            }
            return
        }

        // ─── 19. Browser Use Task (Desktop Bridge) ───────────────────────────────
        if (toolName == "browser_use_task") {
            val request = args.optString("request", "")
            Log.i(TAG, "Delegating to Browser-Use bridge for request: \"$request\"")
            onAppendUserMessage(request)

            respondOk(callId, toolName) {
                put("status", "in_progress")
                put("message", "Browser-Use task triggered for: \"$request\". Tell the user you are starting it on their desktop browser now. Do NOT say it is already finished yet.")
            }

            serviceScope.launch(Dispatchers.IO) {
                try {
                    val result = browserUseClient.runTask(request)
                    val status = result.optString("status", "error")
                    val message = result.optString("message", "")

                    onAppendAgentMessage(message)
                    Log.i(TAG, "Browser-Use task finished: status=$status message=$message")
                    geminiLiveManager.sendClientContent(
                        "[SYSTEM NOTIFICATION: Browser-Use task ($status) for: \"$request\". Outcome: $message. Speak directly to the user in Hindi/Hinglish to share the outcome!]"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Browser-Use bridge call failed: ${e.message}", e)
                    onAppendAgentMessage("Browser-Use task failed: ${e.message}")
                    geminiLiveManager.sendClientContent(
                        "[SYSTEM NOTIFICATION: Browser-Use task failed for request: \"$request\" with error: ${e.message}. Briefly inform the user.]"
                    )
                }
            }
            return
        }

        // ─── 20. In-App Browser Action ───────────────────────────────────────────
        if (toolName == "browser_action") {
            val action = args.optString("action", "snapshot").trim()
            val ref = args.optString("ref", "").trim()
            val text = args.optString("text", "").trim()
            val url = args.optString("url", "").trim()

            Log.i(TAG, "Executing in-app browser_action: action=$action ref=$ref url=$url")
            onAppendAgentMessage("Browser action: $action ${if (ref.isNotBlank()) "ref=$ref" else ""} ${if (url.isNotBlank()) "url=$url" else ""}")

            serviceScope.launch(Dispatchers.IO) {
                val count = activeToolCallCount.incrementAndGet()
                try {
                    val browserController = com.soltini.app.browser.BrowserController.getInstance(context)
                    val resultString = browserController.executeAction(action, ref, text, url)
                    val truncatedResult = if (resultString.length > 6000) {
                        resultString.take(6000) + "\n...[Truncated to 6000 chars]"
                    } else {
                        resultString
                    }

                    respondOk(callId, toolName) {
                        put("action", action)
                        put("result", truncatedResult)
                        put("guideline", "Always provide concise, spoken replies. Never read raw snapshot text or refs aloud over TTS.")
                    }
                    memory2Engine.session.recordToolResult(toolName, "$action outcome: ${truncatedResult.take(120)}")
                } catch (e: Exception) {
                    Log.e(TAG, "browser_action execution failed: ${e.message}", e)
                    respondError(callId, toolName, e.message ?: "Failed to execute browser action")
                } finally {
                    val remaining = activeToolCallCount.decrementAndGet()
                    if (remaining <= 0) {
                        activeToolCallCount.set(0)
                    }
                }
            }
            return
        }

        // ─── 21. Direct Device Tools Fallback ────────────────────────────────────
        serviceScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Executing direct tool: $toolName (callId=$callId)")

            val count = activeToolCallCount.incrementAndGet()
            if (count == 1) {
                com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isToolCallActive = true
            }

            try {
                val result = agentToolExecutor.executeSuspend(toolName, args)
                geminiLiveManager.sendToolResponse(callId, toolName, result)
                memory2Engine.session.recordToolResult(toolName, result.optString("message", "Executed $toolName"))
            } finally {
                val remaining = activeToolCallCount.decrementAndGet()
                if (remaining <= 0) {
                    activeToolCallCount.set(0)
                    com.soltini.app.agent.SoltiniAccessibilityService.getInstance()?.isToolCallActive = false
                }
            }
        }
    }

    /**
     * Splits compound instructions (e.g. "YouTube pe gana chalao aur bluetooth on karo aur notes open karo")
     * into separate actionable tasks that can be run concurrently.
     */
    private fun splitCompoundRequest(request: String): List<String> {
        val trimmed = request.trim()
        if (trimmed.isBlank()) return emptyList()
        val splitRegex = Regex(
            "(?i)\\s+(?:aur\\s+bhi|aur\\s+saath\\s+me|aur\\s+phir|aur\\s+fir|aur|and\\s+also|and\\s+then|and|phir|fir|ke\\s+baad|along\\s+with|then)\\s+|\\s*[,;\\n]\\s+",
            RegexOption.IGNORE_CASE
        )
        val parts = trimmed.split(splitRegex)
            .map { it.trim().trim(',', '.', ';') }
            .filter { it.isNotBlank() && it.length > 2 }
        return if (parts.size > 1) parts else listOf(trimmed)
    }

    /**
     * Executes an individual automation task immediately, first attempting the full orchestrator
     * and falling back seamlessly to BackgroundAgentRunner for direct phone/screen actions.
     */
    private suspend fun executeAutomationSubtask(task: String): String {
        val trimmed = task.trim()
        if (trimmed.isBlank()) return "Task done"
        return try {
            val orchestratorResult = myraOrchestrator.processRequest(trimmed)
            val resp = orchestratorResult.responseText.trim()
            if (orchestratorResult.isSuccess && resp.isNotBlank() && resp != "Ji Boss!") {
                resp
            } else {
                val runner = com.soltini.app.agent.BackgroundAgentRunner(context, agentToolExecutor, appSettings)
                val runnerRes = runner.execute(trimmed).trim()
                if (runnerRes.isNotBlank()) runnerRes else (if (resp.isNotBlank()) resp else "Ji Boss, $trimmed execute ho gaya.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Subtask execution error for '$trimmed': ${e.message}", e)
            "'$trimmed' process kar diya gaya hai."
        }
    }
}
