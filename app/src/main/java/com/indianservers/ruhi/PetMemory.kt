package com.indianservers.ruhi

import android.content.Context

class PetMemory(context: Context) {
    private val prefs = context.getSharedPreferences("ruhi_pet_memory", Context.MODE_PRIVATE)

    fun load(): PetState {
        val personalityName = prefs.getString(KEY_PERSONALITY, PetPersonality.CURIOUS.name)
        val moodName = prefs.getString(KEY_MOOD, PetMood.CALM.name)
        val expressionName = prefs.getString(KEY_EXPRESSION, RobotFaceView.Expression.NEUTRAL.name)

        return PetState(
            mood = enumValueOrDefault(moodName, PetMood.CALM),
            expression = enumValueOrDefault(expressionName, RobotFaceView.Expression.NEUTRAL),
            energy = prefs.getInt(KEY_ENERGY, 72),
            affection = prefs.getInt(KEY_AFFECTION, 45),
            curiosity = prefs.getInt(KEY_CURIOSITY, 60),
            trust = prefs.getInt(KEY_TRUST, 35),
            boredom = prefs.getInt(KEY_BOREDOM, 20),
            comfort = prefs.getInt(KEY_COMFORT, 80),
            lastMessage = prefs.getString(KEY_LAST_MESSAGE, "Tap Ruhi to talk") ?: "Tap Ruhi to talk",
            bondScore = prefs.getInt(KEY_BOND_SCORE, 1),
            interactionCount = prefs.getInt(KEY_INTERACTION_COUNT, 0),
            lastSeenAt = prefs.getLong(KEY_LAST_SEEN_AT, System.currentTimeMillis()),
            personality = enumValueOrDefault(personalityName, PetPersonality.CURIOUS)
        )
    }

    fun save(state: PetState) {
        prefs.edit()
            .putString(KEY_MOOD, state.mood.name)
            .putString(KEY_EXPRESSION, state.expression.name)
            .putInt(KEY_ENERGY, state.energy)
            .putInt(KEY_AFFECTION, state.affection)
            .putInt(KEY_CURIOSITY, state.curiosity)
            .putInt(KEY_TRUST, state.trust)
            .putInt(KEY_BOREDOM, state.boredom)
            .putInt(KEY_COMFORT, state.comfort)
            .putString(KEY_LAST_MESSAGE, state.lastMessage)
            .putInt(KEY_BOND_SCORE, state.bondScore)
            .putInt(KEY_INTERACTION_COUNT, state.interactionCount)
            .putLong(KEY_LAST_SEEN_AT, state.lastSeenAt)
            .putString(KEY_PERSONALITY, state.personality.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T {
        return value?.let {
            runCatching { enumValueOf<T>(it) }.getOrNull()
        } ?: fallback
    }

    private companion object {
        const val KEY_MOOD = "mood"
        const val KEY_EXPRESSION = "expression"
        const val KEY_ENERGY = "energy"
        const val KEY_AFFECTION = "affection"
        const val KEY_CURIOSITY = "curiosity"
        const val KEY_TRUST = "trust"
        const val KEY_BOREDOM = "boredom"
        const val KEY_COMFORT = "comfort"
        const val KEY_LAST_MESSAGE = "last_message"
        const val KEY_BOND_SCORE = "bond_score"
        const val KEY_INTERACTION_COUNT = "interaction_count"
        const val KEY_LAST_SEEN_AT = "last_seen_at"
        const val KEY_PERSONALITY = "personality"
    }
}
