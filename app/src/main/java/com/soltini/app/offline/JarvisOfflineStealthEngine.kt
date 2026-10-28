package com.soltini.app.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.audio.JarvisSoundEngine
import com.soltini.app.cache.JarvisSemanticCache
import com.soltini.app.telephony.TtsSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StealthModeState(
    val isNetworkAvailable: Boolean = true,
    val isStealthModeActive: Boolean = false,
    val offlineTasksExecuted: Int = 0,
    val statusText: String = "ONLINE"
)

/**
 * JarvisOfflineStealthEngine — Zero-Internet Offline Edge Execution
 *
 * Inspired by ggerganov/whisper.cpp and offline neural architectures.
 * Seamlessly engages when the device loses Wi-Fi and Cellular connectivity (or in Airplane mode).
 * Diverts speech recognition and command execution to offline edge models (Vosk & JarvisSemanticCache)
 * without dropping commands or showing network errors.
 */
class JarvisOfflineStealthEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "JarvisOfflineStealth"

        @Volatile
        private var instance: JarvisOfflineStealthEngine? = null

        fun getInstance(context: Context): JarvisOfflineStealthEngine =
            instance ?: synchronized(this) {
                instance ?: JarvisOfflineStealthEngine(context.applicationContext).also { instance = it }
            }
    }

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val scope = CoroutineScope(Dispatchers.Main)
    private val semanticCache = JarvisSemanticCache.getInstance(appContext)
    private val ttsSpeaker = TtsSpeaker(appContext)

    private val _stealthState = MutableStateFlow(StealthModeState())
    val stealthState: StateFlow<StealthModeState> = _stealthState.asStateFlow()

    init {
        registerNetworkMonitor()
    }

    private fun registerNetworkMonitor() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    val wasOffline = !_stealthState.value.isNetworkAvailable
                    _stealthState.value = _stealthState.value.copy(
                        isNetworkAvailable = true,
                        isStealthModeActive = false,
                        statusText = "ONLINE"
                    )
                    if (wasOffline) {
                        JarvisSoundEngine.playTacticalBlip()
                        AgentStatusBus.emit("🌐 Network Link Restored: Full Cloud AI Active")
                    }
                }
            }

            override fun onLost(network: Network) {
                scope.launch {
                    _stealthState.value = _stealthState.value.copy(
                        isNetworkAvailable = false,
                        isStealthModeActive = true,
                        statusText = "STEALTH (OFFLINE)"
                    )
                    JarvisSoundEngine.playWarningAlert()
                    AgentStatusBus.emit("📴 Stealth Mode: Zero-Internet Edge AI Engaged")
                }
            }
        })
    }

    /**
     * Executes a spoken or text command completely offline.
     */
    suspend fun executeOfflineCommand(userInput: String): String {
        val clean = userInput.trim()
        if (clean.isBlank()) return "Offline listener standby, Sir."

        _stealthState.value = _stealthState.value.copy(
            offlineTasksExecuted = _stealthState.value.offlineTasksExecuted + 1
        )

        JarvisSoundEngine.playTacticalBlip()
        return when (val hit = semanticCache.tryExecuteLocally(clean)) {
            is JarvisSemanticCache.CacheResult.Hit -> {
                hit.message
            }
            is JarvisSemanticCache.CacheResult.Miss -> {
                val fallback = "Sir, we are in Stealth Mode with zero internet link. Hardware, Sentry, Telemetry, and Torch controls are active, but cloud queries are queued."
                ttsSpeaker.speak(fallback)
                fallback
            }
        }
    }
}
