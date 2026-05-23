package com.indianservers.ruhi

import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import com.indianservers.ruhi.hardware.LedEffect
import com.indianservers.ruhi.hardware.RobotHardwareController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.roundToInt
import kotlin.random.Random

data class DanceMove(
    val name: String,
    val durationMs: Int,
    val keyframes: List<BodyKeyframe>
)

data class BodyKeyframe(
    val timeMs: Int,
    val headBobY: Float,
    val headWagX: Float,
    val torsoOffsetY: Float,
    val torsoRotation: Float,
    val armLAngle: Float,
    val armRAngle: Float,
    val legLAngle: Float,
    val legRAngle: Float,
    val expression: RobotFaceView.Expression,
    val particleType: RobotFaceView.ParticleType?
)

enum class DanceStyle { ROBOT_DANCE, BREAKDANCE, HIP_HOP, BALLET, BHANGRA, MOONWALK, FLOSS, FREESTYLE }

class DanceEngine(
    private val faceView: RobotFaceView,
    private val hardware: RobotHardwareController? = null
) {
    private val animator = DanceAnimator(faceView, hardware)
    private var loopJob: Job? = null

    fun start(scope: CoroutineScope, style: DanceStyle, beatSync: Boolean = false) {
        stop()
        loopJob = scope.launch {
            while (isActive) {
                val move = moveFor(style)
                animator.play(move, loop = false, beatSync = beatSync)
                delay(move.durationMs.toLong())
                if (Random.nextInt(30) == 0) animator.play(moveFor(DanceStyle.FREESTYLE), loop = false)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        animator.stop()
        faceView.resetBodyPose()
    }

    fun styleForBpm(bpm: Int): DanceStyle = when {
        bpm < 80 -> DanceStyle.BALLET
        bpm < 100 -> DanceStyle.HIP_HOP
        bpm < 120 -> if (Random.nextBoolean()) DanceStyle.BHANGRA else DanceStyle.FLOSS
        bpm < 140 -> DanceStyle.BREAKDANCE
        else -> DanceStyle.ROBOT_DANCE
    }

    fun moveFor(style: DanceStyle): DanceMove = when (style) {
        DanceStyle.ROBOT_DANCE -> DanceMove("Robot Dance", 1600, listOf(
            k(0, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.NEUTRAL),
            k(200, 0f, 12f, 0f, 0f, -90f, 0f, 0f, 0f, RobotFaceView.Expression.COOL),
            k(400, 0f, -12f, 0f, 0f, 0f, 90f, 0f, 0f, RobotFaceView.Expression.COOL),
            k(600, 0f, 0f, 0f, -18f, -45f, 45f, -20f, 10f, RobotFaceView.Expression.GRIN),
            k(800, 0f, 0f, 0f, 18f, 45f, -45f, 10f, -20f, RobotFaceView.Expression.GRIN),
            k(1000, -12f, 0f, 15f, 0f, -20f, 20f, 0f, 0f, RobotFaceView.Expression.HAPPY),
            k(1200, 0f, 0f, 0f, 0f, -60f, 60f, 0f, 0f, RobotFaceView.Expression.STARS, RobotFaceView.ParticleType.SPARKLE),
            k(1600, 0f, 0f, 0f, 360f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.COOL)
        ))
        DanceStyle.BREAKDANCE -> DanceMove("Breakdance", 1800, listOf(
            k(0, 0f, 0f, 20f, 0f, 20f, -20f, 35f, -35f, RobotFaceView.Expression.SQUINT),
            k(300, -30f, 0f, -30f, 0f, -120f, 120f, -40f, 40f, RobotFaceView.Expression.SURPRISED, RobotFaceView.ParticleType.STAR),
            k(500, 0f, 0f, 0f, -20f, 180f, -90f, 60f, -20f, RobotFaceView.Expression.GRIN),
            k(800, 0f, 0f, 0f, 0f, 0f, 90f, 0f, 0f, RobotFaceView.Expression.COOL),
            k(1400, 12f, 0f, 12f, 180f, 360f, -180f, 70f, -70f, RobotFaceView.Expression.DIZZY),
            k(1800, 0f, 0f, 0f, 360f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.GRIN)
        ))
        DanceStyle.HIP_HOP -> pulseMove("Hip Hop", 1800, RobotFaceView.Expression.COOL, amp = 10f, arm = 60f, leg = 20f)
        DanceStyle.BALLET -> DanceMove("Ballet", 2400, listOf(
            k(0, 0f, 0f, 0f, -10f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.HAPPY),
            k(1000, -4f, 0f, -8f, 15f, -150f, 150f, -12f, 12f, RobotFaceView.Expression.HAPPY),
            k(2000, 0f, 0f, -2f, 720f, -120f, 120f, -8f, 8f, RobotFaceView.Expression.LOVE, RobotFaceView.ParticleType.SPARKLE),
            k(2400, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.HAPPY)
        ))
        DanceStyle.BHANGRA -> pulseMove("Bhangra", 1400, RobotFaceView.Expression.GRIN, amp = 20f, arm = 90f, leg = 25f, wag = 12f)
        DanceStyle.MOONWALK -> DanceMove("Moonwalk", 2000, listOf(
            k(0, 0f, 0f, 0f, 0f, -30f, 30f, 0f, 0f, RobotFaceView.Expression.COOL),
            k(500, 0f, 0f, 0f, 0f, -30f, 30f, 0f, -20f, RobotFaceView.Expression.COOL),
            k(1000, 0f, 0f, 0f, 0f, -30f, 30f, -20f, 0f, RobotFaceView.Expression.COOL),
            k(1500, 0f, 0f, 0f, 0f, -30f, 30f, 0f, -20f, RobotFaceView.Expression.COOL),
            k(2000, 0f, 0f, 0f, 0f, -30f, 30f, 0f, 0f, RobotFaceView.Expression.COOL)
        ))
        DanceStyle.FLOSS -> DanceMove("Floss", 1500, listOf(
            k(0, 0f, -8f, 0f, -20f, -80f, 80f, -15f, 15f, RobotFaceView.Expression.SMIRK),
            k(250, 0f, 8f, 0f, 20f, 80f, -80f, 15f, -15f, RobotFaceView.Expression.SMIRK),
            k(500, 0f, -8f, 0f, -20f, -80f, 80f, -15f, 15f, RobotFaceView.Expression.SMIRK),
            k(750, 0f, 8f, 0f, 20f, 80f, -80f, 15f, -15f, RobotFaceView.Expression.SMIRK),
            k(1500, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, RobotFaceView.Expression.GRIN)
        ))
        DanceStyle.FREESTYLE -> freestyleMove()
    }

    fun parseFreestyleJson(json: String): DanceMove {
        val array = JSONArray(json)
        val frames = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            k(
                item.optInt("timeMs", index * 250),
                item.optDouble("headBobY", 0.0).toFloat(),
                item.optDouble("headWagX", 0.0).toFloat(),
                item.optDouble("torsoOffsetY", 0.0).toFloat(),
                item.optDouble("torsoRotation", 0.0).toFloat(),
                item.optDouble("armLAngle", 0.0).toFloat(),
                item.optDouble("armRAngle", 0.0).toFloat(),
                item.optDouble("legLAngle", 0.0).toFloat(),
                item.optDouble("legRAngle", 0.0).toFloat(),
                runCatching { RobotFaceView.Expression.valueOf(item.optString("expression", "HAPPY")) }.getOrDefault(RobotFaceView.Expression.HAPPY)
            )
        }
        return DanceMove("Freestyle JSON", frames.maxOfOrNull { it.timeMs } ?: 2000, frames)
    }

    private fun pulseMove(name: String, duration: Int, expression: RobotFaceView.Expression, amp: Float, arm: Float, leg: Float, wag: Float = 8f): DanceMove {
        val frames = (0..duration step 300).mapIndexed { index, time ->
            val sign = if (index % 2 == 0) 1f else -1f
            k(time, sign * -8f, sign * wag, sign * amp, sign * 8f, -arm * sign, arm * sign, leg * sign, -leg * sign, expression)
        }
        return DanceMove(name, duration, frames)
    }

    private fun freestyleMove(): DanceMove {
        val frames = (0..2000 step 250).map { time ->
            k(time, Random.nextInt(-18, 18).toFloat(), Random.nextInt(-20, 20).toFloat(), Random.nextInt(-18, 18).toFloat(), Random.nextInt(-45, 45).toFloat(), Random.nextInt(-120, 120).toFloat(), Random.nextInt(-120, 120).toFloat(), Random.nextInt(-50, 50).toFloat(), Random.nextInt(-50, 50).toFloat(), listOf(RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.COOL, RobotFaceView.Expression.GRIN, RobotFaceView.Expression.STARS).random(), RobotFaceView.ParticleType.SPARKLE)
        }
        return DanceMove("AI Freestyle", 2000, frames)
    }

    private fun k(time: Int, headBob: Float, headWag: Float, torsoY: Float, torsoRot: Float, armL: Float, armR: Float, legL: Float, legR: Float, expr: RobotFaceView.Expression, particle: RobotFaceView.ParticleType? = null): BodyKeyframe {
        return BodyKeyframe(time, headBob, headWag, torsoY, torsoRot, armL, armR, legL, legR, expr, particle)
    }
}

class DanceAnimator(
    private val faceView: RobotFaceView,
    private val hardware: RobotHardwareController? = null
) {
    private var animator: ValueAnimator? = null
    private val hardwareScope = CoroutineScope(Dispatchers.IO)

    fun play(move: DanceMove, loop: Boolean = true, beatSync: Boolean = false) {
        stop()
        animator = ValueAnimator.ofInt(0, move.durationMs).apply {
            duration = move.durationMs.toLong()
            repeatCount = if (loop) ValueAnimator.INFINITE else 0
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val time = it.animatedValue as Int
                val frame = interpolate(move.keyframes, time)
                faceView.applyBodyKeyframe(frame)
                if (beatSync && time % 300 < 24) faceView.onBeat()
                syncHardware(frame)
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    private fun interpolate(frames: List<BodyKeyframe>, timeMs: Int): BodyKeyframe {
        val ordered = frames.sortedBy { it.timeMs }
        val before = ordered.lastOrNull { it.timeMs <= timeMs } ?: ordered.first()
        val after = ordered.firstOrNull { it.timeMs >= timeMs } ?: ordered.last()
        if (before == after) return before
        val t = ((timeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs).coerceAtLeast(1)).coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float) = a + (b - a) * t
        return before.copy(
            timeMs = timeMs,
            headBobY = lerp(before.headBobY, after.headBobY),
            headWagX = lerp(before.headWagX, after.headWagX),
            torsoOffsetY = lerp(before.torsoOffsetY, after.torsoOffsetY),
            torsoRotation = lerp(before.torsoRotation, after.torsoRotation),
            armLAngle = lerp(before.armLAngle, after.armLAngle),
            armRAngle = lerp(before.armRAngle, after.armRAngle),
            legLAngle = lerp(before.legLAngle, after.legLAngle),
            legRAngle = lerp(before.legRAngle, after.legRAngle),
            expression = if (t < 0.5f) before.expression else after.expression,
            particleType = after.particleType
        )
    }

    private fun syncHardware(frame: BodyKeyframe) {
        val hw = hardware ?: return
        hardwareScope.launch {
            hw.lookAt((frame.headWagX / 48f).coerceIn(-1f, 1f), (frame.headBobY / 48f).coerceIn(-1f, 1f))
            hw.setLed(colorFor(frame.expression), LedEffect.PULSE)
        }
    }

    private fun colorFor(expression: RobotFaceView.Expression): Int = when (expression) {
        RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.GRIN -> android.graphics.Color.YELLOW
        RobotFaceView.Expression.COOL, RobotFaceView.Expression.CURIOUS -> android.graphics.Color.CYAN
        RobotFaceView.Expression.LOVE, RobotFaceView.Expression.HEARTS -> android.graphics.Color.MAGENTA
        else -> android.graphics.Color.WHITE
    }
}
