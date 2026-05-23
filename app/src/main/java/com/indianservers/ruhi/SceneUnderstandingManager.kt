package com.indianservers.ruhi

import androidx.camera.core.ImageProxy

class SceneUnderstandingManager {
    private var lastRunAt = 0L

    fun analyze(imageProxy: ImageProxy, hasPeople: Boolean): SceneContext {
        val now = System.currentTimeMillis()
        if (now - lastRunAt < 5_000L) return SceneContext(hasPeople = hasPeople)
        lastRunAt = now
        val brightness = estimateBrightness(imageProxy)
        return SceneContext(
            isOutdoors = brightness > 0.72f,
            isNight = brightness < 0.22f,
            hasPeople = hasPeople,
            brightness = brightness
        )
    }

    fun reactionFor(scene: SceneContext): Pair<RobotFaceView.Expression, String>? {
        return when {
            scene.isNight -> RobotFaceView.Expression.SLEEP to "It's cozy and dark. I'll glow softly."
            scene.isOutdoors -> RobotFaceView.Expression.STARS to "We're outside? I love it!"
            scene.hasPeople -> RobotFaceView.Expression.HAPPY to "So many people. Social mode on!"
            scene.brightness > 0.75f -> RobotFaceView.Expression.HAPPY to "Bright room, bright mood."
            else -> null
        }
    }

    private fun estimateBrightness(imageProxy: ImageProxy): Float {
        val buffer = imageProxy.planes.firstOrNull()?.buffer ?: return 0.5f
        val count = minOf(buffer.remaining(), 1_000)
        if (count <= 0) return 0.5f
        var sum = 0
        for (index in 0 until count) {
            sum += buffer.get(index).toInt() and 0xFF
        }
        return (sum / count.toFloat()) / 255f
    }
}
