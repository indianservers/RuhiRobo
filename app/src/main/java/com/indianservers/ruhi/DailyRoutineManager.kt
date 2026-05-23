package com.indianservers.ruhi

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyRoutineManager(context: Context) {
    private val prefs = context.getSharedPreferences("ruhi_daily_routine", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var lateNightGreetingUsedThisSession = false

    fun routineForNow(now: Long = System.currentTimeMillis()): RoutineCue? {
        val block = timeBlockFor(now) ?: return null
        if (block == "late" && lateNightGreetingUsedThisSession) return null

        val today = dayFormat.format(Date(now))
        val key = "lastGreetingDate:$block"
        if (block != "late" && prefs.getString(key, null) == today) return null

        if (block == "late") {
            lateNightGreetingUsedThisSession = true
        } else {
            prefs.edit().putString(key, today).apply()
        }

        return when (block) {
            "morning" -> RoutineCue(RobotFaceView.Expression.HAPPY, "Good morning! Ready for today?", block)
            "lunch" -> RoutineCue(RobotFaceView.Expression.GRIN, foodJokes.random(), block)
            "evening" -> RoutineCue(RobotFaceView.Expression.CURIOUS, "How was your day?", block)
            "night" -> RoutineCue(RobotFaceView.Expression.SLEEP, "Getting sleepy...", block, dimBackground = true)
            else -> RoutineCue(RobotFaceView.Expression.SLEEP, "You should rest...", block, dimBackground = true)
        }
    }

    companion object {
        private val foodJokes = listOf(
            "Lunch alert: I tried to order byte-sized snacks.",
            "Food joke time: pasta always has great emotional sauce.",
            "I would share a sandwich, but I only have cache crumbs."
        )

        fun timeOfDay(now: Long = System.currentTimeMillis()): String {
            return when (timeBlockFor(now)) {
                "late" -> "late night"
                null -> "daytime"
                else -> timeBlockFor(now).orEmpty()
            }
        }

        private fun timeBlockFor(now: Long): String? {
            val hour = SimpleDateFormat("H", Locale.US).format(Date(now)).toInt()
            return when (hour) {
                in 5..9 -> "morning"
                in 12..13 -> "lunch"
                in 17..19 -> "evening"
                in 21..23 -> "night"
                in 0..4 -> "late"
                else -> null
            }
        }
    }
}

data class RoutineCue(
    val expression: RobotFaceView.Expression,
    val message: String,
    val timeOfDay: String,
    val dimBackground: Boolean = false
)
