package com.soltini.app.agent

import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/** Where the Live voice model reaches the running task: status, pause, resume, cancel, reply. Supports concurrent multi-tasking. */
object AgentTaskRegistry {

    private val activeEngines = CopyOnWriteArraySet<AgentTaskEngine>()
    @Volatile private var current: AgentTaskEngine? = null
    @Volatile private var lastSnapshot: TaskSnapshot? = null

    @Synchronized
    fun tryStart(engine: AgentTaskEngine): Boolean {
        // Multi-tasking support: register engine without rejecting concurrent tasks
        activeEngines.add(engine)
        current = engine
        return true
    }

    @Synchronized
    fun finish(engine: AgentTaskEngine) {
        lastSnapshot = engine.snapshot()
        activeEngines.remove(engine)
        if (current === engine) {
            current = activeEngines.firstOrNull()
        }
    }

    /** True while an engine run or a task list is executing. */
    fun isBusy(): Boolean = activeEngines.any { it.isActive } || current?.isActive == true || TaskQueueRegistry.isActive()

    /** Snapshot of the running task, else of the last finished one, else null. Used by the floating HUD. */
    fun currentOrLast(): TaskSnapshot? = current?.snapshot() ?: lastSnapshot

    fun getActiveCount(): Int = activeEngines.size

    private fun none(): JSONObject = JSONObject().apply {
        put("status", "no_task")
        put("message", "No automation task is running right now.")
    }

    fun status(): JSONObject {
        val e = current ?: activeEngines.firstOrNull()
        val q = TaskQueueRegistry.status()
        if (e != null) return JSONObject().apply {
            put("status", "running")
            put("task", e.snapshot().toJson())
            put("active_count", activeEngines.size)
            if (q != null) put("task_list", q)
        }
        // between two sub-tasks of a task list there is briefly no engine
        if (q != null) return JSONObject().apply { put("status", "running"); put("task_list", q) }
        val last = lastSnapshot ?: return none()
        return JSONObject().apply { put("status", "finished"); put("task", last.toJson()) }
    }

    fun cancel(): JSONObject {
        val queueCancelled = TaskQueueRegistry.cancel()   // stops the remaining sub-tasks too
        var cancelledCount = 0
        for (engine in activeEngines) {
            try {
                engine.cancel()
                cancelledCount++
            } catch (_: Exception) {}
        }
        current?.cancel()
        activeEngines.clear()
        current = null
        if (cancelledCount == 0 && !queueCancelled) return none()
        return JSONObject().apply { put("status", "cancelling"); put("message", "The tasks are being stopped.") }
    }

    fun pause(): JSONObject {
        activeEngines.forEach { try { it.pause() } catch (_: Exception) {} }
        current?.pause()
        return JSONObject().apply { put("status", "paused") }
    }

    fun resume(): JSONObject {
        activeEngines.forEach { try { it.resume() } catch (_: Exception) {} }
        current?.resume()
        return JSONObject().apply { put("status", "resumed") }
    }

    fun reply(answer: String): JSONObject {
        val target = activeEngines.firstOrNull { it.reply(answer) } ?: current
        if (target != null && target.reply(answer)) {
            return JSONObject().apply { put("status", "delivered"); put("message", "The task continues with the user's answer.") }
        }
        return JSONObject().apply { put("status", "not_waiting"); put("message", "The task is not waiting for an answer.") }
    }
}

/**
 * One-way channel from background engines to the live voice model
 * (BackgroundVoiceService installs the listener while Gemini Live is connected).
 */
object AgentStatusBus {
    @Volatile var listener: ((String) -> Unit)? = null
    fun emit(message: String) {
        try { listener?.invoke(message) } catch (_: Exception) {}
    }
}
