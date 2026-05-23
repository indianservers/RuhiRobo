package com.indianservers.ruhi

enum class PetMood {
    CALM,
    HAPPY,
    CURIOUS,
    AFFECTIONATE,
    PLAYFUL,
    SLEEPY,
    WORRIED,
    STARTLED,
    LISTENING,
    THINKING
}

enum class PetPersonality {
    PLAYFUL,
    SHY,
    CURIOUS,
    CALM
}

enum class PetTouchZone {
    FOREHEAD,
    LEFT_CHEEK,
    RIGHT_CHEEK,
    NOSE,
    CHIN,
    BODY
}

enum class PetTouchGesture {
    TAP,
    DOUBLE_TAP,
    HOLD,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP,
    SWIPE_DOWN,
    PETTING
}

data class PetState(
    val mood: PetMood = PetMood.CALM,
    val expression: RobotFaceView.Expression = RobotFaceView.Expression.NEUTRAL,
    val energy: Int = 72,
    val affection: Int = 45,
    val curiosity: Int = 60,
    val trust: Int = 35,
    val boredom: Int = 20,
    val comfort: Int = 80,
    val lastMessage: String = "Tap Ruhi to talk",
    val bondScore: Int = 1,
    val interactionCount: Int = 0,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val personality: PetPersonality = PetPersonality.CURIOUS
) {
    val statusLine: String
        get() = "Mood: ${mood.name.lowercase().replaceFirstChar { it.uppercase() }}  Bond: $bondScore"
}

sealed class PetEvent {
    data class FaceSeen(
        val smilingProbability: Float,
        val leftEyeOpenProbability: Float,
        val rightEyeOpenProbability: Float,
        val headTiltZ: Float
    ) : PetEvent()

    data object FaceLost : PetEvent()
    data object UserTapped : PetEvent()
    data object UserLongPressed : PetEvent()
    data object UserPetted : PetEvent()
    data class Touch(val zone: PetTouchZone, val gesture: PetTouchGesture) : PetEvent()
    data class UserSaid(val text: String) : PetEvent()
    data object ListeningStarted : PetEvent()
    data object ListeningFailed : PetEvent()
    data object DeviceShaken : PetEvent()
    data object DeviceImpacted : PetEvent()
    data object DeviceSpun : PetEvent()
    data object IdleTick : PetEvent()
    data object SleepRequested : PetEvent()
    data object WakeRequested : PetEvent()
}

data class PetReaction(
    val state: PetState,
    val shouldSpeak: Boolean = false,
    val speech: String? = null
)
