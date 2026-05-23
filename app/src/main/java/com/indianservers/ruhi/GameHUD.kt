package com.indianservers.ruhi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class GameHUD @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var playerHp: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var opponentHp: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var playerSpecial: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var opponentSpecial: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var roundText: String = "Round 1/3"
        set(value) { field = value; invalidate() }
    var scoreText: String = ""
        set(value) { field = value; invalidate() }
    var comboText: String = ""
        set(value) { field = value; invalidate() }
    var bpm: Int = 0
        set(value) { field = value; invalidate() }
    var waveform: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        drawBar(canvas, RectF(24f, 22f, w * 0.42f, 48f), playerHp, true)
        drawBar(canvas, RectF(w * 0.58f, 22f, w - 24f, 48f), opponentHp, false)
        drawSpecial(canvas, RectF(24f, 56f, w * 0.42f, 68f), playerSpecial, true)
        drawSpecial(canvas, RectF(w * 0.58f, 56f, w - 24f, 68f), opponentSpecial, false)
        canvas.drawText(roundText, w / 2f, 46f, textPaint)
        if (bpm > 0) {
            canvas.drawText("$bpm BPM", w / 2f, h - 92f, textPaint)
            paint.color = Color.CYAN
            repeat(24) { i ->
                val barH = (12f + ((i % 5) + 1) * waveform * 18f)
                canvas.drawRoundRect(RectF(w / 2f - 150f + i * 12f, h - 64f - barH, w / 2f - 144f + i * 12f, h - 64f), 4f, 4f, paint)
            }
        }
        if (scoreText.isNotBlank()) canvas.drawText(scoreText, w / 2f, h * 0.52f, textPaint)
        if (comboText.isNotBlank()) {
            textPaint.textSize = 46f
            canvas.drawText(comboText, w / 2f, h * 0.64f, textPaint)
            textPaint.textSize = 34f
        }
    }

    private fun drawBar(canvas: Canvas, rect: RectF, value: Float, left: Boolean) {
        paint.color = Color.argb(145, 0, 0, 0)
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        paint.color = when {
            value > 0.55f -> Color.rgb(54, 220, 90)
            value > 0.25f -> Color.rgb(255, 210, 60)
            else -> Color.rgb(240, 54, 54)
        }
        val fill = rect.width() * value
        val fillRect = if (left) RectF(rect.left, rect.top, rect.left + fill, rect.bottom) else RectF(rect.right - fill, rect.top, rect.right, rect.bottom)
        canvas.drawRoundRect(fillRect, 12f, 12f, paint)
    }

    private fun drawSpecial(canvas: Canvas, rect: RectF, value: Float, left: Boolean) {
        paint.color = Color.argb(120, 0, 0, 0)
        canvas.drawRoundRect(rect, 6f, 6f, paint)
        paint.color = Color.rgb(255, 142, 45)
        val fill = rect.width() * value
        val fillRect = if (left) RectF(rect.left, rect.top, rect.left + fill, rect.bottom) else RectF(rect.right - fill, rect.top, rect.right, rect.bottom)
        canvas.drawRoundRect(fillRect, 6f, 6f, paint)
    }
}
