package com.indianservers.ruhi.model

data class MoodState(
    val happiness: Float = 0.5f,
    val energy: Float = 0.7f,
    val curiosity: Float = 0.6f
) {
    fun clamped(): MoodState = copy(
        happiness = happiness.coerceIn(0f, 1f),
        energy = energy.coerceIn(0f, 1f),
        curiosity = curiosity.coerceIn(0f, 1f)
    )
}
