package com.indianservers.ruhi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class BehaviorPrediction(val kind: String, val text: String, val confidence: Float)

class BehaviorPredictor(private val database: RuhiDatabase) {
    suspend fun record(eventType: String, value: String) = withContext(Dispatchers.IO) {
        val c = Calendar.getInstance()
        database.patternModelDao().insert(PatternModel(eventType = eventType, value = value, hourOfDay = c.get(Calendar.HOUR_OF_DAY), dayOfWeek = c.get(Calendar.DAY_OF_WEEK), timestamp = System.currentTimeMillis()))
    }

    suspend fun predictNow(): BehaviorPrediction? = withContext(Dispatchers.IO) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val recent = database.patternModelDao().recent(120)
        val sameHour = recent.filter { it.hourOfDay == hour }
        when {
            sameHour.count { it.eventType == "appearance" } >= 3 -> BehaviorPrediction("appearance", "You're usually here around now, so I woke myself up.", 0.75f)
            sameHour.count { it.eventType == "leaving" } >= 2 -> BehaviorPrediction("leaving", "Are you leaving soon? I can feel the pattern.", 0.62f)
            Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY -> BehaviorPrediction("monday", "Monday morning detected. I should be extra gentle.", 0.55f)
            else -> null
        }
    }

    suspend fun learnPhrase(text: String) = withContext(Dispatchers.IO) {
        val words = text.trim().split(Regex("\\s+"))
        if (words.size < 5) return@withContext
        val start = words.take(3).joinToString(" ").lowercase()
        val completion = words.drop(3).joinToString(" ")
        val existing = database.phrasePatternDao().find(start)
        database.phrasePatternDao().upsert(
            PhrasePattern(
                id = existing?.id ?: 0,
                startFragment = start,
                completion = completion,
                count = (existing?.count ?: 0) + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun anticipatePhrase(fragment: String, bond: BondLevel): PhrasePattern? = withContext(Dispatchers.IO) {
        if (bond.level < 80) return@withContext null
        database.phrasePatternDao().anticipate(fragment.lowercase())
    }
}
