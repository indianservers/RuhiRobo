package com.indianservers.ruhi

import android.os.Handler
import android.os.Looper

enum class IdleBehavior {
    YAWN,
    GLANCE,
    DANCE,
    CURIOUS_SCAN,
    STRETCH,
    WHISTLE,
    SLEEP
}

class IdleBehaviorSystem(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val onExpression: (RobotFaceView.Expression) -> Unit,
    private val onProactiveCue: () -> Unit
) {
    private var lastInteraction = System.currentTimeMillis()
    private val runnable = object : Runnable {
        override fun run() {
            val idleFor = System.currentTimeMillis() - lastInteraction
            when {
                idleFor > 10 * 60_000L -> onProactiveCue()
                idleFor > 3 * 60_000L -> runBehavior(IdleBehavior.SLEEP)
                idleFor > 20_000L -> runBehavior(IdleBehavior.entries.random())
            }
            handler.postDelayed(this, 8_000L)
        }
    }

    fun start() {
        handler.postDelayed(runnable, 8_000L)
    }

    fun stop() {
        handler.removeCallbacks(runnable)
    }

    fun reset() {
        lastInteraction = System.currentTimeMillis()
    }

    private fun runBehavior(behavior: IdleBehavior) {
        val sequence = when (behavior) {
            IdleBehavior.YAWN -> listOf(RobotFaceView.Expression.SLEEP, RobotFaceView.Expression.NEUTRAL)
            IdleBehavior.GLANCE -> listOf(RobotFaceView.Expression.LEFT_LOOK, RobotFaceView.Expression.NEUTRAL, RobotFaceView.Expression.RIGHT_LOOK)
            IdleBehavior.DANCE -> listOf(RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.GRIN, RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.NEUTRAL)
            IdleBehavior.CURIOUS_SCAN -> listOf(RobotFaceView.Expression.UP_LOOK, RobotFaceView.Expression.DOWN_LOOK, RobotFaceView.Expression.NEUTRAL)
            IdleBehavior.STRETCH -> listOf(RobotFaceView.Expression.SQUINT, RobotFaceView.Expression.RELIEVED)
            IdleBehavior.WHISTLE -> listOf(RobotFaceView.Expression.GRIN, RobotFaceView.Expression.WINK)
            IdleBehavior.SLEEP -> listOf(RobotFaceView.Expression.SLEEP)
        }
        sequence.forEachIndexed { index, expression ->
            handler.postDelayed({ onExpression(expression) }, index * 300L)
        }
    }
}
