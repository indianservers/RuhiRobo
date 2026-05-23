package com.indianservers.ruhi.repository

import com.indianservers.ruhi.ConversationManager
import com.indianservers.ruhi.EmotionalBlend
import com.indianservers.ruhi.RobotFaceView
import com.indianservers.ruhi.model.MoodState

class ConversationRepository(
    private val manager: ConversationManager
) {
    suspend fun reply(text: String, mood: MoodState, expression: RobotFaceView.Expression): String {
        val rootMood = com.indianservers.ruhi.MoodState(
            happiness = mood.happiness,
            energy = mood.energy,
            curiosity = mood.curiosity
        )
        return manager.reply(text, rootMood, EmotionalBlend(expression))
    }
}
