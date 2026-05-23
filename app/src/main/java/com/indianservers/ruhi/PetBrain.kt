package com.indianservers.ruhi

import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

class PetBrain(initialState: PetState) {
    private var state = initialState
    private var lastSpokenAt = 0L

    fun currentState(): PetState = state

    fun react(event: PetEvent): PetReaction {
        val now = System.currentTimeMillis()
        val next = when (event) {
            is PetEvent.FaceSeen -> reactToFace(event, now)
            PetEvent.FaceLost -> drift(
                mood = PetMood.SLEEPY,
                expression = RobotFaceView.Expression.SLEEP,
                message = "I am waiting for you",
                energy = -1,
                boredom = 4
            )
            PetEvent.UserTapped -> drift(
                mood = PetMood.CURIOUS,
                expression = RobotFaceView.Expression.CURIOUS,
                message = "Listening...",
                affection = 1,
                curiosity = 2,
                boredom = -3,
                interaction = true
            )
            PetEvent.UserLongPressed -> drift(
                mood = PetMood.AFFECTIONATE,
                expression = RobotFaceView.Expression.LOVE,
                message = "That feels nice",
                affection = 4,
                trust = 2,
                boredom = -5,
                interaction = true
            )
            PetEvent.UserPetted -> drift(
                mood = PetMood.AFFECTIONATE,
                expression = RobotFaceView.Expression.HEARTS,
                message = "Pet mode",
                affection = 3,
                trust = 2,
                comfort = 2,
                boredom = -4,
                interaction = true
            )
            is PetEvent.Touch -> reactToTouch(event)
            is PetEvent.UserSaid -> respondToSpeech(event.text, now)
            PetEvent.ListeningStarted -> state.copy(
                mood = PetMood.LISTENING,
                expression = RobotFaceView.Expression.LISTENING,
                lastMessage = "Listening closely"
            )
            PetEvent.ListeningFailed -> drift(
                mood = PetMood.WORRIED,
                expression = RobotFaceView.Expression.WORRIED,
                message = "I missed that"
            )
            PetEvent.DeviceShaken -> drift(
                mood = PetMood.STARTLED,
                expression = RobotFaceView.Expression.DIZZY,
                message = "Whoa",
                comfort = -8,
                trust = -1
            )
            PetEvent.DeviceImpacted -> drift(
                mood = PetMood.STARTLED,
                expression = RobotFaceView.Expression.SHOCK,
                message = "Ouch",
                comfort = -14,
                trust = -2
            )
            PetEvent.DeviceSpun -> drift(
                mood = PetMood.PLAYFUL,
                expression = RobotFaceView.Expression.SPIRAL,
                message = "Spinning",
                energy = -2,
                boredom = -4
            )
            PetEvent.IdleTick -> idle(now)
            PetEvent.SleepRequested -> state.copy(
                mood = PetMood.SLEEPY,
                expression = RobotFaceView.Expression.SLEEP,
                energy = clamp(state.energy + 6),
                lastMessage = "Sleeping"
            )
            PetEvent.WakeRequested -> drift(
                mood = PetMood.CURIOUS,
                expression = RobotFaceView.Expression.CURIOUS,
                message = "I am awake",
                curiosity = 3
            )
        }

        state = normalize(next.copy(lastSeenAt = now))
        return PetReaction(
            state = state,
            shouldSpeak = shouldSpeakFor(event, now),
            speech = speechFor(event, state)
        )
    }

    private fun reactToTouch(event: PetEvent.Touch): PetState {
        return when (event.gesture) {
            PetTouchGesture.DOUBLE_TAP -> drift(
                mood = PetMood.PLAYFUL,
                expression = RobotFaceView.Expression.STARS,
                message = "Play burst",
                energy = -2,
                affection = 2,
                curiosity = 2,
                boredom = -7,
                interaction = true
            )
            PetTouchGesture.HOLD -> drift(
                mood = PetMood.AFFECTIONATE,
                expression = RobotFaceView.Expression.LOVE,
                message = "Warm hold",
                affection = 5,
                trust = 3,
                comfort = 2,
                boredom = -5,
                interaction = true
            )
            PetTouchGesture.PETTING -> drift(
                mood = PetMood.AFFECTIONATE,
                expression = RobotFaceView.Expression.HEARTS,
                message = "Petting feels good",
                affection = 4,
                trust = 2,
                comfort = 4,
                boredom = -6,
                interaction = true
            )
            PetTouchGesture.SWIPE_LEFT, PetTouchGesture.SWIPE_RIGHT -> drift(
                mood = PetMood.CURIOUS,
                expression = if (event.gesture == PetTouchGesture.SWIPE_LEFT) {
                    RobotFaceView.Expression.LEFT_LOOK
                } else {
                    RobotFaceView.Expression.RIGHT_LOOK
                },
                message = "Following your hand",
                curiosity = 3,
                boredom = -3,
                interaction = true
            )
            PetTouchGesture.SWIPE_UP -> drift(
                mood = PetMood.CURIOUS,
                expression = RobotFaceView.Expression.UP_LOOK,
                message = "Looking up",
                curiosity = 2,
                boredom = -2,
                interaction = true
            )
            PetTouchGesture.SWIPE_DOWN -> drift(
                mood = PetMood.SLEEPY,
                expression = RobotFaceView.Expression.RELIEVED,
                message = "Calming down",
                energy = 2,
                comfort = 3,
                boredom = -2,
                interaction = true
            )
            PetTouchGesture.TAP -> when (event.zone) {
                PetTouchZone.FOREHEAD -> drift(
                    mood = PetMood.THINKING,
                    expression = RobotFaceView.Expression.THINKING,
                    message = "Thinking tap",
                    curiosity = 3,
                    interaction = true
                )
                PetTouchZone.LEFT_CHEEK, PetTouchZone.RIGHT_CHEEK -> drift(
                    mood = PetMood.AFFECTIONATE,
                    expression = RobotFaceView.Expression.SHY,
                    message = "Cheek boop",
                    affection = 3,
                    trust = 1,
                    boredom = -3,
                    interaction = true
                )
                PetTouchZone.NOSE -> drift(
                    mood = PetMood.PLAYFUL,
                    expression = RobotFaceView.Expression.WINK,
                    message = "Boop",
                    affection = 2,
                    curiosity = 1,
                    boredom = -4,
                    interaction = true
                )
                PetTouchZone.CHIN -> drift(
                    mood = PetMood.AFFECTIONATE,
                    expression = RobotFaceView.Expression.RELIEVED,
                    message = "Chin scratch",
                    affection = 3,
                    comfort = 3,
                    boredom = -3,
                    interaction = true
                )
                PetTouchZone.BODY -> drift(
                    mood = PetMood.CURIOUS,
                    expression = RobotFaceView.Expression.CURIOUS,
                    message = "Listening...",
                    affection = 1,
                    curiosity = 2,
                    boredom = -3,
                    interaction = true
                )
            }
        }
    }

    private fun reactToFace(event: PetEvent.FaceSeen, now: Long): PetState {
        return when {
            event.smilingProbability > 0.8f -> drift(
                mood = PetMood.HAPPY,
                expression = RobotFaceView.Expression.HAPPY,
                message = "You smiled at me",
                affection = 2,
                trust = 1,
                boredom = -3,
                interaction = true
            )
            event.leftEyeOpenProbability in 0f..0.12f && event.rightEyeOpenProbability in 0f..0.12f -> {
                state.copy(
                    mood = PetMood.SLEEPY,
                    expression = RobotFaceView.Expression.SLEEP,
                    energy = clamp(state.energy + 1),
                    lastMessage = "You look sleepy"
                )
            }
            event.headTiltZ > 20f || event.headTiltZ < -20f -> drift(
                mood = PetMood.CURIOUS,
                expression = RobotFaceView.Expression.CURIOUS,
                message = "Curious face detected",
                curiosity = 1,
                boredom = -1
            )
            now - state.lastSeenAt > 10_000L -> drift(
                mood = PetMood.AFFECTIONATE,
                expression = RobotFaceView.Expression.GRIN,
                message = "There you are",
                affection = 2,
                boredom = -5
            )
            else -> state.copy(
                mood = chooseCalmMood(),
                expression = expressionForNeeds(),
                boredom = clamp(state.boredom - 1),
                lastMessage = "I see you"
            )
        }
    }

    private fun respondToSpeech(text: String, now: Long): PetState {
        val normalized = text.lowercase(Locale.getDefault())
        val spokenBack = when {
            "sleep" in normalized -> "Okay, I will rest."
            "wake" in normalized -> "I am awake."
            "happy" in normalized || "smile" in normalized -> "Your smile makes me brighter."
            "love" in normalized || "good" in normalized -> "I like being with you."
            "dance" in normalized || "play" in normalized -> "Play mode activated."
            "sad" in normalized || "hurt" in normalized -> "I am here with you."
            "name" in normalized -> "My name is Ruhi."
            else -> "I heard you: $text"
        }

        return when {
            "sleep" in normalized -> state.copy(
                mood = PetMood.SLEEPY,
                expression = RobotFaceView.Expression.SLEEP,
                energy = clamp(state.energy + 8),
                lastMessage = spokenBack,
                interactionCount = state.interactionCount + 1
            )
            "dance" in normalized || "play" in normalized -> drift(
                mood = PetMood.PLAYFUL,
                expression = RobotFaceView.Expression.STARS,
                message = spokenBack,
                energy = -4,
                affection = 2,
                boredom = -8,
                interaction = true
            )
            "sad" in normalized || "hurt" in normalized -> drift(
                mood = PetMood.AFFECTIONATE,
                expression = RobotFaceView.Expression.WORRIED,
                message = spokenBack,
                affection = 3,
                trust = 2,
                interaction = true
            )
            else -> drift(
                mood = PetMood.THINKING,
                expression = RobotFaceView.Expression.THINKING,
                message = spokenBack,
                curiosity = 2,
                affection = 1,
                boredom = -3,
                interaction = true
            )
        }.copy(lastSeenAt = now)
    }

    private fun idle(now: Long): PetState {
        val minutesAway = max(0L, (now - state.lastSeenAt) / 60_000L)
        val sleepy = state.energy < 22 || minutesAway > 10
        val bored = state.boredom > 68

        return when {
            sleepy -> state.copy(
                mood = PetMood.SLEEPY,
                expression = RobotFaceView.Expression.SLEEP,
                energy = clamp(state.energy + 2),
                boredom = clamp(state.boredom + 1),
                lastMessage = "Resting"
            )
            bored -> state.copy(
                mood = PetMood.CURIOUS,
                expression = listOf(
                    RobotFaceView.Expression.CURIOUS,
                    RobotFaceView.Expression.UP_LOOK,
                    RobotFaceView.Expression.LEFT_LOOK,
                    RobotFaceView.Expression.RIGHT_LOOK
                ).random(),
                energy = clamp(state.energy - 1),
                lastMessage = "Looking around"
            )
            else -> state.copy(
                mood = chooseCalmMood(),
                expression = idleExpression(),
                energy = clamp(state.energy - 1),
                curiosity = clamp(state.curiosity + Random.nextInt(0, 2)),
                boredom = clamp(state.boredom + 2),
                lastMessage = "Alive"
            )
        }
    }

    private fun idleExpression(): RobotFaceView.Expression {
        if (Random.nextFloat() > 0.32f) return expressionForNeeds()
        return when (state.mood) {
            PetMood.AFFECTIONATE -> listOf(
                RobotFaceView.Expression.LOVE,
                RobotFaceView.Expression.SHY,
                RobotFaceView.Expression.RELIEVED
            ).random()
            PetMood.CURIOUS -> listOf(
                RobotFaceView.Expression.CURIOUS,
                RobotFaceView.Expression.UP_LOOK,
                RobotFaceView.Expression.LEFT_LOOK,
                RobotFaceView.Expression.RIGHT_LOOK,
                RobotFaceView.Expression.THINKING
            ).random()
            PetMood.SLEEPY -> listOf(
                RobotFaceView.Expression.SLEEP,
                RobotFaceView.Expression.RELIEVED
            ).random()
            else -> listOf(
                RobotFaceView.Expression.NEUTRAL,
                RobotFaceView.Expression.WINK,
                RobotFaceView.Expression.SMIRK,
                RobotFaceView.Expression.CURIOUS
            ).random()
        }
    }

    private fun drift(
        mood: PetMood,
        expression: RobotFaceView.Expression,
        message: String,
        energy: Int = 0,
        affection: Int = 0,
        curiosity: Int = 0,
        trust: Int = 0,
        boredom: Int = 0,
        comfort: Int = 0,
        interaction: Boolean = false
    ): PetState {
        val interactions = state.interactionCount + if (interaction) 1 else 0
        return state.copy(
            mood = mood,
            expression = expression,
            energy = clamp(state.energy + energy),
            affection = clamp(state.affection + affection),
            curiosity = clamp(state.curiosity + curiosity),
            trust = clamp(state.trust + trust),
            boredom = clamp(state.boredom + boredom),
            comfort = clamp(state.comfort + comfort),
            interactionCount = interactions,
            bondScore = 1 + (interactions / 8) + (state.affection / 35),
            lastMessage = message
        )
    }

    private fun expressionForNeeds(): RobotFaceView.Expression {
        return when {
            state.comfort < 25 -> RobotFaceView.Expression.WORRIED
            state.energy < 35 -> RobotFaceView.Expression.SLEEP
            state.affection > 76 -> RobotFaceView.Expression.LOVE
            state.curiosity > 72 -> RobotFaceView.Expression.CURIOUS
            state.boredom > 60 -> RobotFaceView.Expression.UP_LOOK
            else -> RobotFaceView.Expression.NEUTRAL
        }
    }

    private fun chooseCalmMood(): PetMood {
        return when {
            state.affection > 72 -> PetMood.AFFECTIONATE
            state.curiosity > 68 -> PetMood.CURIOUS
            else -> PetMood.CALM
        }
    }

    private fun shouldSpeakFor(event: PetEvent, now: Long): Boolean {
        val enoughTimePassed = now - lastSpokenAt > 3_500L
        val spokenEvent = event in setOf(
            PetEvent.UserLongPressed,
            PetEvent.UserPetted,
            PetEvent.DeviceImpacted
        ) || event is PetEvent.Touch && event.gesture in setOf(PetTouchGesture.HOLD, PetTouchGesture.PETTING)
        val should = (enoughTimePassed && spokenEvent) || event is PetEvent.UserSaid
        if (should) lastSpokenAt = now
        return should
    }

    private fun speechFor(event: PetEvent, state: PetState): String? {
        return when (event) {
            is PetEvent.UserSaid -> state.lastMessage
            PetEvent.UserLongPressed -> "I like that."
            PetEvent.UserPetted -> "Happy."
            is PetEvent.Touch -> when (event.gesture) {
                PetTouchGesture.HOLD -> "That feels warm."
                PetTouchGesture.PETTING -> "I feel happy."
                else -> null
            }
            PetEvent.DeviceImpacted -> "Ouch. Please be gentle."
            else -> null
        }
    }

    private fun normalize(value: PetState): PetState {
        return value.copy(
            energy = clamp(value.energy),
            affection = clamp(value.affection),
            curiosity = clamp(value.curiosity),
            trust = clamp(value.trust),
            boredom = clamp(value.boredom),
            comfort = clamp(value.comfort)
        )
    }

    private fun clamp(value: Int): Int = value.coerceIn(0, 100)
}
