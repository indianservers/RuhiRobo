package com.indianservers.ruhi

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class HoleState { EMPTY, APPEARING, VISIBLE, HIT, MISSED }
enum class MoleType { NORMAL, GOLDEN, BOMB }
data class MoleHole(var state: HoleState = HoleState.EMPTY, var type: MoleType = MoleType.NORMAL, var scale: Float = 0f, var visibleUntil: Long = 0L)

class WhackAMoleGame {
    val holes = List(9) { MoleHole() }
    var score = 0
    var combo = 0
    var startedAt = System.currentTimeMillis()
    val elapsedSeconds: Int get() = ((System.currentTimeMillis() - startedAt) / 1000).toInt()

    fun popRandom() {
        val activeMax = when {
            elapsedSeconds < 15 -> 3
            elapsedSeconds < 30 -> 4
            elapsedSeconds < 45 -> 5
            else -> 6
        }
        if (holes.count { it.state == HoleState.VISIBLE || it.state == HoleState.APPEARING } >= activeMax) return
        val hole = holes.filter { it.state == HoleState.EMPTY }.randomOrNull() ?: return
        hole.type = when {
            elapsedSeconds > 45 && (0..100).random() < 18 -> MoleType.BOMB
            (0..100).random() < 10 -> MoleType.GOLDEN
            else -> MoleType.NORMAL
        }
        hole.state = HoleState.APPEARING
        hole.scale = 0f
        val window = when {
            hole.type == MoleType.GOLDEN -> 400L
            elapsedSeconds < 15 -> 1500L
            elapsedSeconds < 30 -> 1200L
            elapsedSeconds < 45 -> 900L
            else -> 700L
        }
        hole.visibleUntil = System.currentTimeMillis() + window
    }

    fun tick() {
        holes.forEach { hole ->
            when (hole.state) {
                HoleState.APPEARING -> {
                    hole.scale = (hole.scale + 0.18f).coerceAtMost(1f)
                    if (hole.scale >= 1f) hole.state = HoleState.VISIBLE
                }
                HoleState.VISIBLE -> if (System.currentTimeMillis() > hole.visibleUntil) {
                    hole.state = HoleState.MISSED
                    combo = 0
                }
                HoleState.HIT, HoleState.MISSED -> {
                    hole.scale = (hole.scale - 0.18f).coerceAtLeast(0f)
                    if (hole.scale <= 0f) hole.state = HoleState.EMPTY
                }
                else -> Unit
            }
        }
    }

    fun hit(index: Int): String {
        val hole = holes.getOrNull(index) ?: return ""
        if (hole.state != HoleState.VISIBLE && hole.state != HoleState.APPEARING) {
            combo = 0
            return "Too slow!"
        }
        hole.state = HoleState.HIT
        val multiplier = (combo + 1).coerceAtMost(5)
        return when (hole.type) {
            MoleType.NORMAL -> {
                combo++
                score += 10 * multiplier
                "OUCH! x$multiplier"
            }
            MoleType.GOLDEN -> {
                combo++
                score += 50 * multiplier
                "Golden Ruhi!"
            }
            MoleType.BOMB -> {
                combo = 0
                score -= 20
                "Bomb Ruhi!"
            }
        }
    }
}

class WhackAMoleView(context: android.content.Context) : View(context) {
    val game = WhackAMoleGame()
    var onMessage: ((String) -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overshoot = OvershootInterpolator()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(18, 24, 34))
        val cellW = width / 3f
        val cellH = height / 3f
        game.holes.forEachIndexed { index, hole ->
            val row = index / 3
            val col = index % 3
            val cx = col * cellW + cellW / 2f
            val cy = row * cellH + cellH * 0.62f
            paint.color = Color.rgb(30, 20, 18)
            canvas.drawOval(RectF(cx - cellW * 0.28f, cy - cellH * 0.08f, cx + cellW * 0.28f, cy + cellH * 0.08f), paint)
            if (hole.state != HoleState.EMPTY) drawRuhiHead(canvas, cx, cy - cellH * 0.16f, cellW * 0.18f * overshoot.getInterpolation(hole.scale.coerceIn(0f, 1f)), hole)
        }
        invalidate()
    }

    private fun drawRuhiHead(canvas: Canvas, cx: Float, cy: Float, radius: Float, hole: MoleHole) {
        paint.color = when (hole.type) {
            MoleType.NORMAL -> Color.CYAN
            MoleType.GOLDEN -> Color.rgb(255, 210, 45)
            MoleType.BOMB -> Color.RED
        }
        canvas.drawCircle(cx, cy, radius, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(cx - radius * 0.35f, cy - radius * 0.1f, radius * 0.13f, paint)
        canvas.drawCircle(cx + radius * 0.35f, cy - radius * 0.1f, radius * 0.13f, paint)
        paint.color = if (hole.state == HoleState.HIT) Color.YELLOW else Color.WHITE
        canvas.drawArc(RectF(cx - radius * 0.42f, cy, cx + radius * 0.42f, cy + radius * 0.52f), if (hole.state == HoleState.MISSED) 180f else 0f, 180f, false, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val col = (event.x / (width / 3f)).toInt().coerceIn(0, 2)
            val row = (event.y / (height / 3f)).toInt().coerceIn(0, 2)
            onMessage?.invoke(game.hit(row * 3 + col))
            return true
        }
        return true
    }
}

class WhackAMoleActivity : AppCompatActivity() {
    private lateinit var view: WhackAMoleView
    private lateinit var status: TextView
    private var loop: Job? = null
    private var startAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAt = System.currentTimeMillis()
        view = WhackAMoleView(this).apply { onMessage = { status.text = "$it  Score: ${game.score}" } }
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 22f; gravity = android.view.Gravity.CENTER; text = "Find me if you can!" }
        setContentView(FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(-1, -1))
            addView(status, FrameLayout.LayoutParams(-1, 72, android.view.Gravity.TOP))
        })
        loop = lifecycleScope.launch {
            while (isActive) {
                view.game.popRandom()
                view.game.tick()
                status.text = "Score: ${view.game.score}  Combo: ${view.game.combo}"
                delay(220)
            }
        }
    }

    override fun onDestroy() {
        loop?.cancel()
        lifecycleScope.launch {
            RuhiDatabase.getInstance(this@WhackAMoleActivity).gameResultDao().insert(
                GameResult(
                    gameType = "WHACK_A_MOLE",
                    playerScore = view.game.score,
                    ruhiScore = 0,
                    outcome = "WIN",
                    durationMs = System.currentTimeMillis() - startAt,
                    timestamp = System.currentTimeMillis(),
                    bondLevelAtTime = 0
                )
            )
        }
        super.onDestroy()
    }
}
