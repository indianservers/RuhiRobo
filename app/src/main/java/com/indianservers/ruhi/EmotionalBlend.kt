package com.indianservers.ruhi

data class EmotionalBlend(
    val base: RobotFaceView.Expression,
    val overlay: RobotFaceView.Expression? = null,
    val overlayIntensity: Float = 0f
) {
    val dominantExpression: RobotFaceView.Expression
        get() = overlay?.takeIf { overlayIntensity >= 0.5f } ?: base
}
