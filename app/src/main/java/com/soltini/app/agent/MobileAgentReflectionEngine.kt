package com.soltini.app.agent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

/**
 * MobileAgentReflectionEngine — Inspired by X-PLUG/MobileAgent & Tencent AppAgent
 *
 * Implements the "Perceive -> Act -> Reflect" loop for autonomous mobile phone automation.
 * Verifies screen state changes before and after each action, detecting missed taps,
 * unpopulated form inputs, or stalled screens, and performing automatic self-recovery.
 */
class MobileAgentReflectionEngine(
    private val context: Context,
    private val executor: AgentToolExecutor
) {

    companion object {
        private const val TAG = "MobileAgentReflection"
        private const val SETTLE_DELAY_MS = 450L
    }

    data class UiState(
        val packageName: String,
        val textDigest: String,
        val elementCount: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ReflectionVerdict(
        val isSuccessful: Boolean,
        val stateChanged: Boolean,
        val retryAttempted: Boolean,
        val message: String
    )

    /**
     * Captures a lightweight fingerprint of the current interactive screen.
     */
    fun captureCurrentState(): UiState {
        val service = SoltiniAccessibilityService.getInstance()
        val root = service?.rootInActiveWindow
        if (root == null) {
            return UiState(
                packageName = "unknown",
                textDigest = "no_root_window",
                elementCount = 0
            )
        }

        val texts = mutableListOf<String>()
        var count = 0

        fun traverse(node: android.view.accessibility.AccessibilityNodeInfo?) {
            if (node == null) return
            count++
            val txt = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            if (!txt.isNullOrBlank()) texts.add(txt)
            if (!desc.isNullOrBlank() && desc != txt) texts.add(desc)

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        try {
            traverse(root)
        } catch (_: Exception) {}

        val digest = texts.take(30).joinToString(" | ")
        return UiState(
            packageName = root.packageName?.toString() ?: "unknown",
            textDigest = digest,
            elementCount = count
        )
    }

    /**
     * Executes an action and validates if the screen transitioned as expected.
     * If no screen state change occurred (e.g. missed click), applies self-recovery.
     */
    suspend fun executeWithReflection(
        actionName: String,
        actionParams: Map<String, Any>,
        expectedTargetText: String? = null
    ): ReflectionVerdict {
        val preState = captureCurrentState()
        Log.d(TAG, "Pre-action UI state: pkg=${preState.packageName}, items=${preState.elementCount}")

        // 1. Execute initial action
        val rawResult = executeActionDirectly(actionName, actionParams)
        delay(SETTLE_DELAY_MS)

        // 2. Capture post-action UI state
        val postState = captureCurrentState()
        Log.d(TAG, "Post-action UI state: pkg=${postState.packageName}, items=${postState.elementCount}")

        val stateChanged = (preState.textDigest != postState.textDigest) ||
                (preState.packageName != postState.packageName) ||
                (kotlin.math.abs(preState.elementCount - postState.elementCount) > 2)

        if (stateChanged) {
            return ReflectionVerdict(
                isSuccessful = true,
                stateChanged = true,
                retryAttempted = false,
                message = "Action '$actionName' executed successfully; screen updated."
            )
        }

        // 3. Self-Recovery Reflection: Action didn't change UI
        Log.w(TAG, "Screen unchanged after '$actionName'. Initiating MobileAgent self-correction...")
        var retryOk = false

        if (!expectedTargetText.isNullOrBlank()) {
            // Retry via smart click fallback or gentle scroll
            val retryJson = org.json.JSONObject().apply { put("text", expectedTargetText) }
            val retryResult = executor.executeSuspend("click_element", retryJson)
            delay(SETTLE_DELAY_MS)
            val retryPostState = captureCurrentState()
            retryOk = (preState.textDigest != retryPostState.textDigest)
        }

        return ReflectionVerdict(
            isSuccessful = retryOk,
            stateChanged = retryOk,
            retryAttempted = true,
            message = if (retryOk) "Initial tap missed; self-recovery succeeded via element targeting."
                      else "Action '$actionName' executed but screen state did not change."
        )
    }

    private suspend fun executeActionDirectly(actionName: String, params: Map<String, Any>): String {
        val json = org.json.JSONObject()
        for ((k, v) in params) {
            json.put(k, v)
        }
        val res = executor.executeSuspend(actionName, json)
        return res.optString("message", "ok")
    }
}
