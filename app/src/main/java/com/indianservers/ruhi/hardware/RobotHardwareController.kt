package com.indianservers.ruhi.hardware

import android.graphics.Color
import com.indianservers.ruhi.RobotFaceView
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class LedEffect(val code: Int) { SOLID(0), PULSE(1), RAINBOW(2), CHASE(3), BREATHE(4) }

enum class RobotSound(val id: Int) {
    PURR(1), BOOP(2), GIGGLE(3), CURIOUS_HUM(4), SAD_WHIMPER(5), EXCITED_CHIRP(6), BOOT(7), SHUTDOWN(8)
}

class RobotHardwareController(private val ble: BleRobotManager) {
    suspend fun expressEmotion(expr: RobotFaceView.Expression, durationMs: Int = 2_000) {
        ble.sendEmotion(expr, intensity = 1f, durationMs = durationMs)
        val (color, effect) = ledFor(expr)
        setLed(color, effect)
    }

    suspend fun lookAt(normalizedX: Float, normalizedY: Float) {
        ble.sendHeadPosition(
            panAngle = normalizedX.coerceIn(-1f, 1f) * 45f,
            tiltAngle = normalizedY.coerceIn(-1f, 1f) * 30f,
            speed = 0.55f
        )
    }

    suspend fun driveForward(cm: Float, speed: Float = 0.5f) = drive(cm, speed.coerceIn(0.1f, 1f), speed.coerceIn(0.1f, 1f))
    suspend fun driveBackward(cm: Float, speed: Float = 0.5f) = drive(cm, -speed.coerceIn(0.1f, 1f), -speed.coerceIn(0.1f, 1f))
    suspend fun turnLeft(degrees: Float) = drive(abs(degrees) / 90f * 350f, -0.45f, 0.45f)
    suspend fun turnRight(degrees: Float) = drive(abs(degrees) / 90f * 350f, 0.45f, -0.45f)
    suspend fun spin(rotations: Float) = drive((rotations * 1_200f).toInt().toFloat(), 0.65f, -0.65f)

    suspend fun dockReturn() {
        expressEmotion(RobotFaceView.Expression.SLEEP, 1_000)
        setLed(Color.rgb(90, 40, 160), LedEffect.BREATHE)
    }

    suspend fun playSound(sound: RobotSound) = ble.playSound(sound.id)
    suspend fun setLed(color: Int, effect: LedEffect = LedEffect.SOLID) = ble.sendLedColor(color, effect.code)

    suspend fun stopAll() {
        ble.sendBodyMotor(0f, 0f, 1)
        ble.sendHeadPosition(0f, 0f, 0.6f)
    }

    suspend fun wiggle() {
        repeat(2) {
            ble.sendBodyMotor(-0.35f, 0.35f, 180)
            delay(190)
            ble.sendBodyMotor(0.35f, -0.35f, 180)
            delay(190)
        }
        stopAll()
    }

    suspend fun nod() {
        ble.sendHeadPosition(0f, 20f, 0.8f)
        delay(180)
        ble.sendHeadPosition(0f, -8f, 0.8f)
        delay(180)
        ble.sendHeadPosition(0f, 0f, 0.6f)
    }

    suspend fun shake() {
        repeat(3) {
            ble.sendHeadPosition(-24f, 0f, 1f)
            delay(120)
            ble.sendHeadPosition(24f, 0f, 1f)
            delay(120)
        }
        ble.sendHeadPosition(0f, 0f, 0.8f)
    }

    private fun drive(distanceLike: Float, left: Float, right: Float) {
        val duration = distanceLike.toInt().coerceIn(120, 6_000)
        ble.sendBodyMotor(left, right, duration)
    }

    private fun ledFor(expr: RobotFaceView.Expression): Pair<Int, LedEffect> = when (expr) {
        RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.GRIN -> Color.rgb(255, 210, 72) to LedEffect.SOLID
        RobotFaceView.Expression.ANGRY -> Color.RED to LedEffect.PULSE
        RobotFaceView.Expression.SAD, RobotFaceView.Expression.CRYING -> Color.rgb(64, 140, 255) to LedEffect.BREATHE
        RobotFaceView.Expression.LOVE, RobotFaceView.Expression.HEARTS -> Color.rgb(255, 105, 180) to LedEffect.PULSE
        RobotFaceView.Expression.SLEEP -> Color.rgb(80, 40, 130) to LedEffect.BREATHE
        else -> Color.WHITE to LedEffect.SOLID
    }
}
