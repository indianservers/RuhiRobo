package com.indianservers.ruhi

import android.graphics.Color
import com.indianservers.ruhi.hardware.LedEffect
import com.indianservers.ruhi.hardware.RobotHardwareController
import com.indianservers.ruhi.hardware.RobotSensorState
import com.indianservers.ruhi.hardware.RobotSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class SelfAssessment(val description: String, val confidence: Float, val shouldApologize: Boolean = false)

class SelfModelEngine(private val database: RuhiDatabase) {
    private var conversationQuality = 0.6f
    private var predictionAccuracy = 0.5f

    fun recordUserReaction(positive: Boolean) {
        conversationQuality = (conversationQuality + if (positive) 0.05f else -0.06f).coerceIn(0f, 1f)
    }

    fun recordPrediction(correct: Boolean) {
        predictionAccuracy = (predictionAccuracy + if (correct) 0.08f else -0.05f).coerceIn(0f, 1f)
    }

    fun maybeMakeMistake(): Boolean = Random.nextFloat() < 0.05f

    fun describeSelf(): SelfAssessment {
        val text = when {
            conversationQuality < 0.35f -> "I know I am trying, but I may be talking too much. I am working on listening better."
            predictionAccuracy > 0.75f -> "I am getting better at noticing your patterns, but I still want you to correct me."
            else -> "I know I am playful and curious, and I do not know everything yet."
        }
        return SelfAssessment(text, (conversationQuality + predictionAccuracy) / 2f)
    }

    suspend fun updateEvolutionMilestones(firstSeenAt: Long) = withContext(Dispatchers.IO) {
        val ageDays = ((System.currentTimeMillis() - firstSeenAt) / 86_400_000L).toInt()
        val milestones = listOf(
            1 to "Baby mode",
            7 to "First catchphrase",
            30 to "Solid personality",
            90 to "Prediction quirks",
            180 to "Old friend mode",
            365 to "One year letter"
        )
        milestones.filter { ageDays >= it.first }.forEach { (_, name) ->
            database.evolutionDao().upsert(EvolutionMilestone(milestone = name, achievedAt = System.currentTimeMillis(), note = "Ruhi reached $name after $ageDays days."))
        }
    }
}

class EmotionToMotionMap(private val hardware: RobotHardwareController) {
    suspend fun express(expression: RobotFaceView.Expression) {
        when (expression) {
            RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.GRIN -> {
                hardware.setLed(Color.YELLOW, LedEffect.RAINBOW); hardware.playSound(RobotSound.EXCITED_CHIRP); hardware.spin(1f)
            }
            RobotFaceView.Expression.SAD, RobotFaceView.Expression.CRYING -> {
                hardware.setLed(Color.BLUE, LedEffect.BREATHE); hardware.lookAt(0f, 0.8f)
            }
            RobotFaceView.Expression.ANGRY -> {
                hardware.setLed(Color.RED, LedEffect.PULSE); hardware.shake()
            }
            RobotFaceView.Expression.CURIOUS -> {
                hardware.setLed(Color.CYAN, LedEffect.PULSE); hardware.lookAt(0.45f, -0.2f); hardware.turnRight(18f)
            }
            RobotFaceView.Expression.LOVE, RobotFaceView.Expression.HEARTS -> {
                hardware.setLed(Color.rgb(255, 105, 180), LedEffect.BREATHE); hardware.nod()
            }
            RobotFaceView.Expression.NERVOUS, RobotFaceView.Expression.SHOCK, RobotFaceView.Expression.SURPRISED -> {
                hardware.stopAll(); hardware.driveBackward(20f, 0.8f)
            }
            RobotFaceView.Expression.SLEEP -> {
                hardware.setLed(Color.rgb(80, 40, 130), LedEffect.BREATHE); hardware.dockReturn()
            }
            RobotFaceView.Expression.THINKING -> {
                hardware.setLed(Color.WHITE, LedEffect.PULSE); hardware.playSound(RobotSound.CURIOUS_HUM); hardware.lookAt(-0.35f, -0.1f)
            }
            else -> hardware.expressEmotion(expression)
        }
    }
}

class PhysicalInstinctEngine(private val hardware: RobotHardwareController) {
    private var stuckAttempts = 0
    private var lastLeft = 0
    private var lastRight = 0

    suspend fun reflex(sensor: RobotSensorState, commandedMovement: Boolean, freefallG: Float = 1f, cpuTempC: Float = 32f): RobotFaceView.Expression? {
        if (sensor.distanceMm in 1..30 || sensor.cliffDetected) {
            hardware.stopAll()
            hardware.driveBackward(15f, 0.8f)
            return RobotFaceView.Expression.NERVOUS
        }
        if (freefallG < 0.5f) {
            hardware.stopAll()
            return RobotFaceView.Expression.NERVOUS
        }
        if (commandedMovement && sensor.leftEncoderTicks == lastLeft && sensor.rightEncoderTicks == lastRight) {
            stuckAttempts++
            if (stuckAttempts <= 3) {
                hardware.turnLeft(45f)
                delay(250)
                hardware.driveForward(8f, 0.45f)
            } else {
                hardware.stopAll()
                return RobotFaceView.Expression.WORRIED
            }
        } else {
            stuckAttempts = 0
        }
        lastLeft = sensor.leftEncoderTicks
        lastRight = sensor.rightEncoderTicks
        return if (cpuTempC > 45f) RobotFaceView.Expression.COLD_SWEAT else null
    }
}
