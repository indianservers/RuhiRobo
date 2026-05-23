package com.indianservers.ruhi.model

import com.indianservers.ruhi.RobotFaceView

data class EmotionState(
    val expression: RobotFaceView.Expression = RobotFaceView.Expression.NEUTRAL,
    val ledColor: Int = 0xFF00FFCC.toInt()
)

enum class TouchZone {
    HEAD_TOP,
    NOSE,
    LEFT_CHEEK,
    RIGHT_CHEEK,
    CHIN,
    BODY
}

data class SensorReading(
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float,
    val gy: Float,
    val gz: Float,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val motionG: Float = 0f,
    val gyroMagnitude: Float = 0f,
    val faceDown: Boolean = false,
    val shaken: Boolean = false
)
