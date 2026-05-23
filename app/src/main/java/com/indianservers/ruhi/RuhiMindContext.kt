package com.indianservers.ruhi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RuhiMindState(
    val needs: RobotNeeds = RobotNeeds(),
    val bondLevel: Int = 18,
    val bondStage: String = "STRANGER",
    val curiosityTarget: String? = null,
    val activeGoal: String? = null,
    val spatialRoom: String = "home",
    val prediction: String? = null,
    val selfAssessment: String? = null
) {
    fun asPromptContext(): String {
        return """
            Needs: energy=${needs.energy}, social=${needs.social}, stimulation=${needs.stimulation}, comfort=${needs.comfort}, expression=${needs.expression}, safety=${needs.safety}.
            Bond: $bondLevel/$bondStage. Curiosity target: ${curiosityTarget ?: "none"}. Active goal: ${activeGoal ?: "none"}.
            Spatial context: $spatialRoom. Prediction: ${prediction ?: "none"}. Self-model: ${selfAssessment ?: "still learning"}.
            Ruhi has dignity: if a request conflicts with safety, comfort, or bond trust, she may gently refuse.
        """.trimIndent()
    }
}

object RuhiMindContext {
    private val _state = MutableStateFlow(RuhiMindState())
    val state: StateFlow<RuhiMindState> = _state

    fun update(transform: (RuhiMindState) -> RuhiMindState) {
        _state.value = transform(_state.value)
    }
}
