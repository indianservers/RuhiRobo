package com.indianservers.ruhi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class HypothesisEngine(private val database: RuhiDatabase) {
    suspend fun recordInteractionPattern(eventType: String, value: String) = withContext(Dispatchers.IO) {
        val now = Calendar.getInstance()
        database.patternModelDao().insert(
            PatternModel(
                eventType = eventType,
                value = value,
                hourOfDay = now.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = now.get(Calendar.DAY_OF_WEEK),
                timestamp = System.currentTimeMillis()
            )
        )
        val recent = database.patternModelDao().recentByType(eventType, 20)
        val groupedHour = recent.groupBy { it.hourOfDay }.maxByOrNull { it.value.size }
        if (groupedHour != null && groupedHour.value.size >= 3) {
            val theory = "I think you often $eventType around ${groupedHour.key}:00."
            database.hypothesisDao().upsert(
                Hypothesis(pattern = "$eventType@${groupedHour.key}", theory = theory, confidence = (groupedHour.value.size / 10f).coerceAtMost(0.9f), verified = false, updatedAt = System.currentTimeMillis())
            )
        }
    }

    suspend fun nextTheory(): Hypothesis? = withContext(Dispatchers.IO) {
        database.hypothesisDao().strongestUnverified()
    }

    suspend fun confirm(hypothesis: Hypothesis) = withContext(Dispatchers.IO) {
        database.hypothesisDao().upsert(hypothesis.copy(confidence = (hypothesis.confidence + 0.25f).coerceAtMost(1f), verified = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun reject(hypothesis: Hypothesis, correction: String) = withContext(Dispatchers.IO) {
        database.hypothesisDao().upsert(hypothesis.copy(theory = correction, confidence = 0.35f, verified = false, updatedAt = System.currentTimeMillis()))
    }
}

class CausalLearningEngine(private val database: RuhiDatabase) {
    suspend fun observe(trigger: String, outcome: String) = withContext(Dispatchers.IO) {
        val cleanTrigger = trigger.lowercase().take(80)
        val cleanOutcome = outcome.lowercase().take(80)
        val existing = database.causalPatternDao().find(cleanTrigger, cleanOutcome)
        val occurrences = (existing?.occurrences ?: 0) + 1
        database.causalPatternDao().upsert(
            CausalPattern(
                id = existing?.id ?: 0,
                triggerText = cleanTrigger,
                outcome = cleanOutcome,
                confidence = (0.18f + occurrences * 0.08f).coerceAtMost(1f),
                occurrences = occurrences,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun strongest(): List<CausalPattern> = withContext(Dispatchers.IO) {
        database.causalPatternDao().strongest(5)
    }
}
