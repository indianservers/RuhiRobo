package com.indianservers.ruhi

import android.content.Context
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicLong

class ObjectDetector(private val context: Context) {
    private val lastRunAt = AtomicLong(0L)
    private val lastReactionByLabel = mutableMapOf<String, Long>()

    fun detect(imageProxy: ImageProxy): List<DetectedObject> {
        val now = System.currentTimeMillis()
        if (now - lastRunAt.get() < 3_000L) return emptyList()
        lastRunAt.set(now)
        if (!assetExists("mobilenet_ssd.tflite") && !assetExists("mobilenet_ssd_v2_coco_quant.tflite")) return emptyList()
        return emptyList()
    }

    fun reactionFor(objects: List<DetectedObject>): Pair<RobotFaceView.Expression, String>? {
        val objectHit = objects.maxByOrNull { it.confidence } ?: return null
        val label = objectHit.label.lowercase()
        val now = System.currentTimeMillis()
        if (now - (lastReactionByLabel[label] ?: 0L) < 10_000L) return null
        lastReactionByLabel[label] = now
        return when (label) {
            "cat", "dog" -> RobotFaceView.Expression.WORRIED to "Oh, another pet?"
            "food", "pizza" -> RobotFaceView.Expression.HAPPY to "That looks delicious!"
            "phone" -> RobotFaceView.Expression.CURIOUS to "Taking a photo?"
            "book" -> RobotFaceView.Expression.THINKING to "Reading something interesting?"
            "person" -> RobotFaceView.Expression.CURIOUS to "Who is that? Want to be my friend?"
            else -> null
        }
    }

    fun objectEyeTarget(objects: List<DetectedObject>): RectF? {
        return objects.maxByOrNull { it.confidence }?.boundingBox
    }

    private fun assetExists(name: String): Boolean {
        return runCatching {
            context.assets.open(name).close()
            true
        }.getOrDefault(false)
    }
}
