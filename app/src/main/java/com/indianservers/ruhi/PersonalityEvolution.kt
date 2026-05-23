package com.indianservers.ruhi

class PersonalityEvolution(
    private val repository: ConversationRepository
) {
    suspend fun evolveForConversation(text: String) {
        val profile = repository.personalityProfile()
        val normalized = text.lowercase()
        repository.updatePersonality(
            profile.copy(
                openness = shift(profile.openness, normalized.contains("?") || normalized.contains("why")),
                warmth = shift(profile.warmth, normalized.contains("love") || normalized.contains("thanks")),
                playfulness = shift(profile.playfulness, normalized.contains("joke") || normalized.contains("play")),
                seriousness = shift(profile.seriousness, normalized.contains("sad") || normalized.contains("feel")),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun evolveForTouch() {
        val profile = repository.personalityProfile()
        repository.updatePersonality(
            profile.copy(
                warmth = shift(profile.warmth, true),
                playfulness = shift(profile.playfulness, true),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun shift(value: Float, increase: Boolean): Float {
        val delta = if (increase) 0.001f else -0.0003f
        return (value + delta).coerceIn(0f, 1f)
    }
}
