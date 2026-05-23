package com.indianservers.ruhi

import androidx.camera.core.ImageProxy

class GestureRecognitionManager {
    private var lastGestureAt = 0L

    fun detect(imageProxy: ImageProxy): GestureEvent? {
        val now = System.currentTimeMillis()
        if (now - lastGestureAt < 2_000L) return null
        return null
    }

    fun reactionFor(type: GestureType): Pair<RobotFaceView.Expression, String> {
        lastGestureAt = System.currentTimeMillis()
        return when (type) {
            GestureType.WAVE -> RobotFaceView.Expression.HAPPY to "Hi!"
            GestureType.THUMBS_UP -> RobotFaceView.Expression.HAPPY to "You're amazing!"
            GestureType.THUMBS_DOWN -> RobotFaceView.Expression.SAD to "Oh no, what did I do wrong?"
            GestureType.OPEN_PALM -> RobotFaceView.Expression.GRIN to "High five!"
            GestureType.POINTING -> RobotFaceView.Expression.CURIOUS to "I see where you are pointing."
            GestureType.FIST_RAISED -> RobotFaceView.Expression.COOL to "Fist bump!"
            GestureType.HEART_HANDS -> RobotFaceView.Expression.LOVE to "Aww. Heart received."
        }
    }
}
