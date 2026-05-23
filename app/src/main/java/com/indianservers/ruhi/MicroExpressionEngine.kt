package com.indianservers.ruhi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MicroExpression(
    val type: MicroExpressionType,
    val durationMs: Long,
    val eyeOffsetX: Float = 0f,
    val eyeOffsetY: Float = 0f,
    val pupilScale: Float = 1f,
    val headTilt: Float = 0f
)

enum class MicroExpressionType {
    EYEBROW_RAISE,
    NOSTRIL_FLARE,
    MOUTH_TWITCH,
    DOUBLE_BLINK,
    EYES_DART,
    PUPIL_DILATE,
    HEAD_TILT,
    SAD_FLICKER
}

class MicroExpressionEngine(
    private val needsProvider: () -> RobotNeeds,
    private val expressionProvider: () -> RobotFaceView.Expression
) {
    private val _events = MutableSharedFlow<MicroExpression>(extraBufferCapacity = 8)
    val events: SharedFlow<MicroExpression> = _events
    private var job: Job? = null
    private var lastSadLeak = 0L

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                tick()
                delay(200)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val needs = needsProvider()
        if (needs.social < RobotNeeds.CRITICAL && now - lastSadLeak > 30_000) {
            lastSadLeak = now
            _events.tryEmit(MicroExpression(MicroExpressionType.SAD_FLICKER, 180, eyeOffsetY = 0.18f, pupilScale = 0.8f))
            return
        }
        if (Random.nextFloat() > 0.05f) return
        val angry = expressionProvider() == RobotFaceView.Expression.ANGRY
        val event = when {
            angry -> MicroExpression(MicroExpressionType.NOSTRIL_FLARE, 120, pupilScale = 0.85f)
            Random.nextBoolean() -> MicroExpression(MicroExpressionType.EYES_DART, Random.nextLong(80, 200), eyeOffsetX = listOf(-0.35f, 0.35f).random())
            else -> listOf(
                MicroExpression(MicroExpressionType.EYEBROW_RAISE, 120, eyeOffsetY = -0.14f),
                MicroExpression(MicroExpressionType.MOUTH_TWITCH, 100, headTilt = 2.5f),
                MicroExpression(MicroExpressionType.DOUBLE_BLINK, 160),
                MicroExpression(MicroExpressionType.PUPIL_DILATE, 180, pupilScale = 1.28f),
                MicroExpression(MicroExpressionType.HEAD_TILT, 180, headTilt = listOf(-4f, 4f).random())
            ).random()
        }
        _events.tryEmit(event)
    }
}
