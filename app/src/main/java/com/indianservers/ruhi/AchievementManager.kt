package com.indianservers.ruhi

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AchievementManager(
    private val context: Context,
    private val repository: ConversationRepository
) {
    private val prefs = context.getSharedPreferences("ruhi_achievements", Context.MODE_PRIVATE)
    private val definitions = listOf(
        Achievement(0, "first_words", "First Words", "Have your first conversation", null, 0, 1),
        Achievement(0, "chatterbox", "Chatterbox", "Have 50 conversations", null, 0, 50),
        Achievement(0, "mood_ring", "Mood Ring", "Trigger all basic moods in one session", null, 0, 8),
        Achievement(0, "night_owl", "Night Owl", "Interact after 11pm", null, 0, 1),
        Achievement(0, "early_bird", "Early Bird", "Interact before 7am", null, 0, 1),
        Achievement(0, "happy_together", "Happy Together", "Maintain happiness above 0.8 for 10 minutes", null, 0, 10),
        Achievement(0, "game_over", "Game Over", "Complete all 3 mini-games", null, 0, 3),
        Achievement(0, "high_scorer", "High Scorer", "Score 5/5 in any mini-game", null, 0, 1),
        Achievement(0, "best_friends", "Best Friends", "Visit 7 consecutive days", null, 0, 7),
        Achievement(0, "tickle_master", "Tickle Master", "Tap Ruhi's nose 10 times", null, 0, 10)
    )
    private val basicEmotions = setOf("HAPPY", "SAD", "ANGRY", "CURIOUS", "SLEEP", "SURPRISED", "LOVE", "CRYING")

    suspend fun ensureDefaults() {
        val existing = repository.achievements().map { it.key }.toSet()
        definitions.filterNot { it.key in existing }.forEach { repository.upsertAchievement(it) }
    }

    suspend fun progress(key: String, amount: Int = 1): Achievement? {
        ensureDefaults()
        val current = repository.achievements().firstOrNull { it.key == key } ?: return null
        if (current.unlockedAt != null) return null
        val nextProgress = (current.progress + amount).coerceAtMost(current.target)
        val unlocked = nextProgress >= current.target
        val updated = current.copy(
            progress = nextProgress,
            unlockedAt = if (unlocked) System.currentTimeMillis() else null
        )
        repository.upsertAchievement(updated)
        return updated.takeIf { unlocked }
    }

    suspend fun syncConversationAchievements(): List<Achievement> {
        ensureDefaults()
        val count = repository.conversationCount()
        return listOfNotNull(
            setProgress("first_words", count.coerceAtMost(1)),
            setProgress("chatterbox", count.coerceAtMost(50))
        )
    }

    suspend fun recordInteraction(now: Long = System.currentTimeMillis()): List<Achievement> {
        val hour = SimpleDateFormat("H", Locale.US).format(Date(now)).toInt()
        val unlocked = mutableListOf<Achievement>()
        if (hour >= 23) progress("night_owl")?.let(unlocked::add)
        if (hour < 7) progress("early_bird")?.let(unlocked::add)
        recordDailyStreak()?.let(unlocked::add)
        return unlocked
    }

    suspend fun recordExpression(expression: RobotFaceView.Expression): Achievement? {
        if (expression.name !in basicEmotions) return null
        val seen = prefs.getStringSet("session_emotions", emptySet()).orEmpty().toMutableSet()
        seen.add(expression.name)
        prefs.edit().putStringSet("session_emotions", seen).apply()
        return setProgress("mood_ring", seen.size.coerceAtMost(8))
    }

    suspend fun recordNosePoke(): Achievement? = progress("tickle_master")

    suspend fun recordHappyMinute(): Achievement? = progress("happy_together")

    suspend fun recordGameCompleted(gameKey: String): List<Achievement> {
        val completed = prefs.getStringSet("completed_games", emptySet()).orEmpty().toMutableSet()
        completed.add(gameKey)
        prefs.edit().putStringSet("completed_games", completed).apply()
        return listOfNotNull(setProgress("game_over", completed.size.coerceAtMost(3)))
    }

    suspend fun recordPerfectGame(): Achievement? = progress("high_scorer")

    private suspend fun recordDailyStreak(): Achievement? {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastDay = prefs.getString("last_interaction_day", null)
        if (lastDay == today) {
            return null
        }
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L))
        val nextStreak = if (lastDay == yesterday) prefs.getInt("daily_streak", 0) + 1 else 1
        prefs.edit()
            .putString("last_interaction_day", today)
            .putInt("daily_streak", nextStreak)
            .apply()
        return setProgress("best_friends", nextStreak.coerceAtMost(7))
    }

    private suspend fun setProgress(key: String, progress: Int): Achievement? {
        ensureDefaults()
        val current = repository.achievements().firstOrNull { it.key == key } ?: return null
        if (current.unlockedAt != null) return null
        val bounded = progress.coerceIn(0, current.target)
        val unlocked = bounded >= current.target
        val updated = current.copy(
            progress = bounded,
            unlockedAt = if (unlocked) System.currentTimeMillis() else null
        )
        repository.upsertAchievement(updated)
        return updated.takeIf { unlocked }
    }
}
