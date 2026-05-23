package com.indianservers.ruhi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.indianservers.ruhi.hardware.LedEffect
import com.indianservers.ruhi.hardware.RobotHardwareController
import com.indianservers.ruhi.hardware.BleRobotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class Ball(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radius: Float = 20f,
    var spin: Float = 0f
)

enum class SoccerDifficulty(val keeperLerp: Float) { EASY(0.04f), MEDIUM(0.06f), HARD(0.09f) }
enum class SoccerMode { PENALTY_SHOOTOUT, TIMED_MATCH, ENDLESS, WORLD_CUP }

class SoccerGame(
    var fieldWidth: Float = 1f,
    var fieldHeight: Float = 1f,
    var difficulty: SoccerDifficulty = SoccerDifficulty.MEDIUM,
    private val hardware: RobotHardwareController? = null
) {
    val ball = Ball(fieldWidth / 2f, fieldHeight * 0.65f)
    var userScore = 0
    var ruhiScore = 0
    var ruhiKeeperY = fieldHeight * 0.5f
    var strikerX = fieldWidth * 0.82f
    var strikerActive = false
    var lastEvent: String = ""
    private var lastCounterAt = 0L
    private val hardwareScope = CoroutineScope(Dispatchers.IO)

    fun resize(width: Float, height: Float) {
        fieldWidth = width.coerceAtLeast(1f)
        fieldHeight = height.coerceAtLeast(1f)
        if (ball.x <= 1f && ball.y <= 1f) resetBall()
    }

    fun update(dt: Float = 1f) {
        ball.x += ball.vx * dt
        ball.y += ball.vy * dt
        ball.vx *= 0.98f
        ball.vy += 0.5f * dt
        ball.vx += ball.spin * 0.02f
        val groundY = fieldHeight * 0.86f
        if (ball.x < ball.radius) {
            ball.x = ball.radius
            ball.vx = -ball.vx * 0.7f
        }
        if (ball.x > fieldWidth - ball.radius) {
            ball.x = fieldWidth - ball.radius
            ball.vx = -ball.vx * 0.7f
        }
        if (ball.y > groundY) {
            ball.y = groundY
            ball.vy = -ball.vy * 0.6f
            ball.vx *= 0.85f
        }
        if (ball.y < ball.radius) {
            ball.y = ball.radius
            ball.vy = -ball.vy * 0.55f
        }
        val anticipation = if (difficulty == SoccerDifficulty.HARD) ball.vy * 4f else 0f
        ruhiKeeperY += ((ball.y + anticipation).coerceIn(goalTop(), goalBottom()) - ruhiKeeperY) * difficulty.keeperLerp
        if (System.currentTimeMillis() - lastCounterAt > 8_000L && !strikerActive) {
            strikerActive = true
            strikerX = fieldWidth * 0.86f
            lastCounterAt = System.currentTimeMillis()
        }
        if (strikerActive) {
            strikerX -= 4f
            ball.x = strikerX - 24f
            ball.y = fieldHeight * 0.72f
            if (strikerX < fieldWidth * 0.35f) shootRuhi()
        }
        detectGoals()
        attemptSave()
    }

    fun shootFromSwipe(startX: Float, startY: Float, endX: Float, endY: Float, millis: Long, circularity: Float = 0f) {
        val dt = millis.coerceAtLeast(16).toFloat() / 16f
        ball.vx = (endX - startX) / dt * 0.34f
        ball.vy = (endY - startY) / dt * 0.34f
        ball.spin = circularity.coerceIn(-80f, 80f)
        if (hypot((endX - startX).toDouble(), (endY - startY).toDouble()) / millis.coerceAtLeast(1) > 1.0) {
            ball.vx *= 2.5f
            ball.spin *= 1.4f
            lastEvent = "ROCKET SHOT!"
        }
    }

    fun interceptOrTackle(x: Float, y: Float): Boolean {
        if (hypot((x - ball.x).toDouble(), (y - ball.y).toDouble()) < ball.radius * 2.2f) {
            ball.vx *= -0.65f
            ball.vy -= 12f
            strikerActive = false
            lastEvent = "Tackle!"
            return true
        }
        if (strikerActive && hypot((x - strikerX).toDouble(), (y - ball.y).toDouble()) < 80.0) {
            strikerActive = false
            lastEvent = "HEY!"
            return true
        }
        return false
    }

    private fun shootRuhi() {
        strikerActive = false
        val targetY = listOf(goalTop() + 24f, goalBottom() - 24f, (goalTop() + goalBottom()) / 2f).random()
        val angle = atan2(targetY - ball.y, 30f - ball.x)
        ball.vx = cos(angle) * 24f
        ball.vy = sin(angle) * 24f
        lastEvent = "Ruhi special!"
    }

    private fun attemptSave() {
        val inKeeperLane = ball.x > fieldWidth - 120f && ball.x < fieldWidth - 50f && ball.y in goalTop()..goalBottom()
        if (inKeeperLane && kotlin.math.abs(ball.y - ruhiKeeperY) < 70f) {
            ball.vx = -kotlin.math.abs(ball.vx) * 0.8f
            ball.vy += (ball.y - ruhiKeeperY) * 0.08f
            lastEvent = "Nice try!"
        }
    }

    private fun detectGoals() {
        val inGoalY = ball.y in goalTop()..goalBottom()
        when {
            ball.x < 60f && inGoalY -> {
                ruhiScore++
                lastEvent = "Ruhi scores!"
                hardwareCelebrate(true)
                resetBall()
            }
            ball.x > fieldWidth - 60f && inGoalY -> {
                userScore++
                lastEvent = if (userScore >= 3) "Hat trick!" else "Goal!"
                hardwareCelebrate(false)
                resetBall()
            }
        }
    }

    private fun hardwareCelebrate(ruhiScored: Boolean) {
        val hw = hardware ?: return
        hardwareScope.launch {
            if (ruhiScored) {
                hw.spin(1f)
                hw.setLed(Color.MAGENTA, LedEffect.RAINBOW)
            } else {
                hw.lookAt(0f, 0.8f)
                hw.setLed(Color.BLUE, LedEffect.BREATHE)
            }
        }
    }

    fun resetBall() {
        ball.x = fieldWidth / 2f
        ball.y = fieldHeight * 0.62f
        ball.vx = 0f
        ball.vy = 0f
        ball.spin = 0f
    }

    fun goalTop(): Float = fieldHeight * 0.34f
    fun goalBottom(): Float = fieldHeight * 0.72f
}

class SoccerFieldView(context: Context) : View(context) {
    var game: SoccerGame = SoccerGame()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    var onEvent: ((String) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        game.resize(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        drawField(canvas)
        drawGoals(canvas)
        drawKeeper(canvas)
        drawBall(canvas)
        if (game.strikerActive) drawStriker(canvas)
        invalidate()
    }

    private fun drawField(canvas: Canvas) {
        canvas.drawColor(Color.rgb(24, 112, 52))
        paint.color = Color.argb(45, 255, 255, 255)
        for (x in 0 until width step 42) canvas.drawLine(x.toFloat(), 0f, x + 120f, height.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.WHITE
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), paint)
        canvas.drawCircle(width / 2f, height * 0.55f, height * 0.16f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawGoals(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = Color.WHITE
        canvas.drawRect(0f, game.goalTop(), 60f, game.goalBottom(), paint)
        canvas.drawRect(width - 60f, game.goalTop(), width.toFloat(), game.goalBottom(), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawKeeper(canvas: Canvas) {
        paint.color = Color.CYAN
        canvas.save()
        canvas.translate(width - width * 0.15f, game.ruhiKeeperY)
        if (kotlin.math.abs(game.ball.vx) > 15f && game.ball.x > width * 0.72f) canvas.rotate(if (game.ball.y < game.ruhiKeeperY) -70f else 70f)
        canvas.drawRoundRect(RectF(-28f, -48f, 28f, 48f), 18f, 18f, paint)
        canvas.drawCircle(0f, -70f, 34f, paint)
        canvas.drawLine(-45f, -20f, 45f, -20f, paint)
        canvas.restore()
    }

    private fun drawStriker(canvas: Canvas) {
        paint.color = Color.MAGENTA
        canvas.drawCircle(game.strikerX, game.ball.y - 48f, 28f, paint)
        canvas.drawRoundRect(RectF(game.strikerX - 24f, game.ball.y - 20f, game.strikerX + 24f, game.ball.y + 46f), 14f, 14f, paint)
    }

    private fun drawBall(canvas: Canvas) {
        val b = game.ball
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(b.x, b.y, b.radius, paint)
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        val path = Path()
        repeat(6) { i ->
            val a = i * Math.PI / 3.0 + b.spin * 0.01
            val x = b.x + cos(a).toFloat() * b.radius * 0.55f
            val y = b.y + sin(a).toFloat() * b.radius * 0.55f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downAt = event.eventTime
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!game.interceptOrTackle(event.x, event.y)) {
                    game.shootFromSwipe(downX, downY, event.x, event.y, event.eventTime - downAt)
                }
                onEvent?.invoke(game.lastEvent)
                return true
            }
        }
        return true
    }
}

class SoccerActivity : AppCompatActivity() {
    private lateinit var game: SoccerGame
    private lateinit var field: SoccerFieldView
    private lateinit var score: TextView
    private var loop: Job? = null
    private var startAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = SoccerGame(hardware = RobotHardwareController(BleRobotManager(this)))
        field = SoccerFieldView(this).apply {
            game = this@SoccerActivity.game
            onEvent = { updateScore(it) }
        }
        score = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = android.view.Gravity.CENTER
        }
        setContentView(FrameLayout(this).apply {
            addView(field, FrameLayout.LayoutParams(-1, -1))
            addView(score, FrameLayout.LayoutParams(-1, 72, android.view.Gravity.TOP))
        })
        startAt = System.currentTimeMillis()
        startLoop()
    }

    private fun startLoop() {
        loop = lifecycleScope.launch {
            while (isActive) {
                game.update()
                updateScore(game.lastEvent)
                delay(16)
            }
        }
    }

    private fun updateScore(event: String) {
        val elapsed = ((System.currentTimeMillis() - startAt) / 1000).toInt()
        score.text = "YOU: ${game.userScore}    ${elapsed}s    RUHI: ${game.ruhiScore}   $event"
    }

    override fun onDestroy() {
        loop?.cancel()
        lifecycleScope.launch {
            RuhiDatabase.getInstance(this@SoccerActivity).gameResultDao().insert(
                GameResult(
                    gameType = "SOCCER",
                    playerScore = game.userScore,
                    ruhiScore = game.ruhiScore,
                    outcome = when {
                        game.userScore > game.ruhiScore -> "WIN"
                        game.userScore < game.ruhiScore -> "LOSE"
                        else -> "DRAW"
                    },
                    durationMs = System.currentTimeMillis() - startAt,
                    timestamp = System.currentTimeMillis(),
                    bondLevelAtTime = 0
                )
            )
        }
        super.onDestroy()
    }
}
