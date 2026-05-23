package com.indianservers.ruhi

import android.graphics.RectF

data class MoodState(
    val happiness: Float = 0.55f,
    val energy: Float = 0.72f,
    val curiosity: Float = 0.6f
)

data class FaceData(
    val trackingId: Int? = null,
    val smilingProbability: Float = -1f,
    val leftEyeOpenProbability: Float = -1f,
    val rightEyeOpenProbability: Float = -1f,
    val boundingBox: RectF = RectF(),
    val descriptor: FaceDescriptor? = null,
    val profile: FaceProfile? = null
)

data class FaceDescriptor(
    val values: FloatArray
) {
    fun distanceTo(other: FaceDescriptor): Float {
        val limit = minOf(values.size, other.values.size)
        if (limit == 0) return Float.MAX_VALUE
        var sum = 0f
        for (index in 0 until limit) {
            val diff = values[index] - other.values[index]
            sum += diff * diff
        }
        return kotlin.math.sqrt(sum)
    }

    fun encode(): String = values.joinToString(",")

    companion object {
        fun decode(value: String): FaceDescriptor {
            return FaceDescriptor(value.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray())
        }
    }
}

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)

data class SceneContext(
    val isOutdoors: Boolean = false,
    val isNight: Boolean = false,
    val hasPeople: Boolean = false,
    val brightness: Float = 0.5f
)

enum class GestureType {
    WAVE,
    THUMBS_UP,
    THUMBS_DOWN,
    OPEN_PALM,
    POINTING,
    FIST_RAISED,
    HEART_HANDS
}

sealed class GestureEvent {
    data class Detected(val type: GestureType) : GestureEvent()
}

enum class ParticleType {
    HEART,
    STAR,
    TEAR,
    ZZZ,
    SPARKLE,
    CONFETTI
}

enum class MouthShape {
    CLOSED,
    HALF,
    OPEN
}
