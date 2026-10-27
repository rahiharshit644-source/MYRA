package com.soltini.app.protocol

import android.util.Log

enum class GoalCategory {
    MESSAGING,
    TELEMETRY,
    HARDWARE,
    SECURITY,
    AUTOMATION,
    GENERAL
}

data class AtomicGoal(
    val id: String,
    val title: String,
    val category: GoalCategory,
    val command: String
)

/**
 * JarvisMultiGoalDecomposer — AutoGPT & Mobile-Agent Style Goal Decomposition Engine
 *
 * Breaks compound natural language instructions into a sequenced Directed Acyclic Graph (DAG)
 * of atomic executable sub-tasks, supporting English, Hindi, and Hinglish conjunctions.
 */
object JarvisMultiGoalDecomposer {

    private const val TAG = "JarvisGoalDecomposer"

    // Multi-lingual conjunction split pattern (Hindi + English)
    private val CONJUNCTION_REGEX = Regex(
        "(?i)\\s+(?:aur\\s+bhi|aur\\s+saath\\s+me|aur\\s+phir|aur\\s+fir|aur|and\\s+also|and\\s+then|and|phir|fir|ke\\s+baad|along\\s+with|then|plus)\\s+|\\s*[,;\\n]\\s+",
        RegexOption.IGNORE_CASE
    )

    /**
     * Decomposes compound prompt into atomic sub-goals.
     */
    fun decompose(userInput: String): List<AtomicGoal> {
        val trimmed = userInput.trim()
        if (trimmed.isBlank()) return emptyList()

        val rawChunks = trimmed.split(CONJUNCTION_REGEX)
            .map { it.trim().trim(',', '.', ';') }
            .filter { it.isNotBlank() && it.length >= 3 }

        val goals = mutableListOf<AtomicGoal>()
        rawChunks.forEachIndexed { index, chunk ->
            val category = classifyCategory(chunk)
            val title = formatGoalTitle(chunk, category)
            goals.add(
                AtomicGoal(
                    id = "goal_${System.currentTimeMillis()}_$index",
                    title = title,
                    category = category,
                    command = chunk
                )
            )
        }

        Log.d(TAG, "Decomposed '${trimmed}' into ${goals.size} atomic goals")
        return goals
    }

    private fun classifyCategory(chunk: String): GoalCategory {
        val lower = chunk.lowercase()
        return when {
            lower.contains("whatsapp") || lower.contains("message") || lower.contains("text") ||
                    lower.contains("sms") || lower.contains("bolo") || lower.contains("bhejo") -> GoalCategory.MESSAGING

            lower.contains("weather") || lower.contains("mausam") || lower.contains("battery") ||
                    lower.contains("briefing") || lower.contains("vitals") || lower.contains("telemetry") -> GoalCategory.TELEMETRY

            lower.contains("torch") || lower.contains("flashlight") || lower.contains("light") ||
                    lower.contains("volume") || lower.contains("sound") || lower.contains("bluetooth") ||
                    lower.contains("wifi") || lower.contains("dnd") -> GoalCategory.HARDWARE

            lower.contains("sentry") || lower.contains("lock") || lower.contains("security") ||
                    lower.contains("shield") || lower.contains("suraksha") -> GoalCategory.SECURITY

            lower.contains("spotify") || lower.contains("youtube") || lower.contains("play") ||
                    lower.contains("alarm") || lower.contains("timer") || lower.contains("open") ||
                    lower.contains("kholo") || lower.contains("chalao") -> GoalCategory.AUTOMATION

            else -> GoalCategory.GENERAL
        }
    }

    private fun formatGoalTitle(chunk: String, category: GoalCategory): String {
        val clean = chunk.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return when (category) {
            GoalCategory.MESSAGING -> "Message: $clean"
            GoalCategory.TELEMETRY -> "Telemetry: $clean"
            GoalCategory.HARDWARE -> "Hardware: $clean"
            GoalCategory.SECURITY -> "Security: $clean"
            GoalCategory.AUTOMATION -> "App: $clean"
            GoalCategory.GENERAL -> clean
        }
    }
}
