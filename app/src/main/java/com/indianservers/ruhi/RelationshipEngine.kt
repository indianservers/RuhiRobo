package com.indianservers.ruhi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BondStage { STRANGER, ACQUAINTANCE, FRIEND, CLOSE_FRIEND, BONDED }

class RelationshipEngine(private val database: RuhiDatabase) {
    suspend fun currentBond(): BondLevel = withContext(Dispatchers.IO) {
        database.bondDao().get() ?: BondLevel(level = 18, updatedAt = System.currentTimeMillis(), lastSeenAt = System.currentTimeMillis())
            .also { database.bondDao().upsert(it) }
    }

    suspend fun stage(): BondStage {
        val level = currentBond().level
        return when {
            level <= 20 -> BondStage.STRANGER
            level <= 40 -> BondStage.ACQUAINTANCE
            level <= 60 -> BondStage.FRIEND
            level <= 80 -> BondStage.CLOSE_FRIEND
            else -> BondStage.BONDED
        }
    }

    suspend fun recordConversation(quality: Boolean = true) = adjust(if (quality) 2 else 1)
    suspend fun recordPetting() = adjust(1)
    suspend fun recordDailyReturn() = adjust(3)
    suspend fun recordGoalHelped() = adjust(5)
    suspend fun recordLaughter() = adjust(3)
    suspend fun recordHarshSpeech() = adjust(-3)
    suspend fun recordIgnoredInitiation() = adjust(-2)

    suspend fun recordAppOpenAndReturnMessage(): String? = withContext(Dispatchers.IO) {
        val bond = currentBond()
        val now = System.currentTimeMillis()
        val absentDays = ((now - bond.lastSeenAt) / 86_400_000L).toInt()
        val newLevel = (bond.level - absentDays.coerceAtMost(20)).coerceIn(0, 100)
        database.bondDao().upsert(bond.copy(level = newLevel, updatedAt = now, lastSeenAt = now))
        when {
            absentDays >= 7 -> "I miss you. Everything was quieter without you."
            absentDays >= 3 -> "I've been waiting... are you okay?"
            else -> null
        }
    }

    suspend fun maybeStoreInsideJoke(text: String, context: String) {
        val lower = text.lowercase()
        if ("haha" in lower || "lol" in lower || "funny" in lower || "joke" in lower) {
            withContext(Dispatchers.IO) {
                database.insideJokeDao().insert(InsideJoke(summary = text.take(120), context = context.take(160), timestamp = System.currentTimeMillis()))
            }
            recordLaughter()
        }
    }

    suspend fun insideJokeCallback(): String? = withContext(Dispatchers.IO) {
        if (currentBond().level <= 60) return@withContext null
        database.insideJokeDao().recent(8).randomOrNull()?.let { "Remember when ${it.summary}? That still makes me light up." }
    }

    private suspend fun adjust(delta: Int) = withContext(Dispatchers.IO) {
        val bond = currentBond()
        database.bondDao().upsert(
            bond.copy(
                level = (bond.level + delta).coerceIn(0, 100),
                updatedAt = System.currentTimeMillis(),
                lastSeenAt = System.currentTimeMillis()
            )
        )
    }
}
