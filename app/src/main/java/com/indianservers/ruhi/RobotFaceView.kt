package com.indianservers.ruhi

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class RobotFaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Expression {
        NEUTRAL, HAPPY, SAD, ANGRY, CURIOUS, SLEEP, LISTENING, SURPRISED,
        HEARTS, STARS, SPIRAL, CROSS, TEARS, WINK, THINKING, SQUINT, 
        DIZZY, MONEY, WORRIED, COLD_SWEAT, EVIL, SHY, MASK, NERVOUS,
        LOVE, COOL, DEAD, CRYING, RELIEVED, SHOCK, GRIN, SMIRK,
        UP_LOOK, DOWN_LOOK, LEFT_LOOK, RIGHT_LOOK, FLAT, 
        DIAGONAL_LEFT, DIAGONAL_RIGHT, CHEVRON_UP, CHEVRON_DOWN,
        SQUARE, CIRCLE, TRIANGLE, DIAMOND, HEXAGON, PACMAN, GHOST,
        ALIEN, ROBOT_EYE, POWER_OFF
    }

    private var currentExpression = Expression.NEUTRAL
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.parseColor("#444444")
    }
    
    private val featurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#8000FFCC")
    }

    private val extraPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var blinkProgress = 1f
    private var eyePositionOffset = PointF(0f, 0f)
    private var audioAmplitude = 0f

    private var eyeWidth = 0f
    private var eyeHeight = 0f
    private var eyeSpacing = 0f

    // Ripple effect data
    private class Ripple(val x: Float, val y: Float, var radius: Float, var alpha: Int)
    private val ripples = mutableListOf<Ripple>()

    private var headTilt = 0f
    private var headTranslationX = 0f
    private var headTranslationY = 0f

    init {
        startBlinking()
    }

    // Convenience methods for expressions
    fun showNeutral() = setExpression(Expression.NEUTRAL)
    fun showHappy() = setExpression(Expression.HAPPY)
    fun showSad() = setExpression(Expression.SAD)
    fun showAngry() = setExpression(Expression.ANGRY)
    fun showSurprised() = setExpression(Expression.SURPRISED)
    fun showSleep() = setExpression(Expression.SLEEP)
    fun showCurious() = setExpression(Expression.CURIOUS)
    fun showShock() = setExpression(Expression.SHOCK)
    fun showDizzy() = setExpression(Expression.DIZZY)
    fun showLove() = setExpression(Expression.LOVE)
    fun showThinking() = setExpression(Expression.THINKING)
    fun showWink() = setExpression(Expression.WINK)
    fun showDead() = setExpression(Expression.DEAD)
    fun showCrying() = setExpression(Expression.CRYING)
    fun showEvil() = setExpression(Expression.EVIL)
    fun showCool() = setExpression(Expression.COOL)
    fun showNervous() = setExpression(Expression.NERVOUS)
    fun showShy() = setExpression(Expression.SHY)
    fun showWorried() = setExpression(Expression.WORRIED)
    fun showGrin() = setExpression(Expression.GRIN)
    fun showSmirk() = setExpression(Expression.SMIRK)
    fun showHearts() = setExpression(Expression.HEARTS)
    fun showStars() = setExpression(Expression.STARS)
    fun showSpiral() = setExpression(Expression.SPIRAL)
    fun showListening(amplitude: Float) {
        setExpression(Expression.LISTENING)
        setAudioAmplitude(amplitude)
    }
    fun showPowerOff() = setExpression(Expression.POWER_OFF)

    fun setHeadTilt(degrees: Float) {
        headTilt = degrees
        invalidate()
    }

    fun setHeadTranslation(x: Float, y: Float) {
        headTranslationX = x
        headTranslationY = y
        invalidate()
    }

    fun setExpression(expression: Expression) {
        if (currentExpression != expression) {
            currentExpression = expression
            invalidate()
        }
    }

    fun setEyeOffset(x: Float, y: Float) {
        eyePositionOffset.set(x, y)
        invalidate()
    }

    fun setAudioAmplitude(amplitude: Float) {
        audioAmplitude = amplitude
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            addRipple(event.x, event.y)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun addRipple(x: Float, y: Float) {
        val ripple = Ripple(x, y, 0f, 255)
        ripples.add(ripple)
        
        val animator = ValueAnimator.ofFloat(0f, 300f)
        animator.duration = 1000
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            val value = it.animatedValue as Float
            ripple.radius = value
            ripple.alpha = (255 * (1 - value / 300f)).toInt()
            if (value >= 300f) {
                ripples.remove(ripple)
            }
            invalidate()
        }
        animator.start()
    }

    private fun startBlinking() {
        val blinkAction = object : Runnable {
            override fun run() {
                if (currentExpression != Expression.SLEEP && currentExpression != Expression.POWER_OFF) {
                    animateBlink()
                }
                postDelayed(this, (2000..6000).random().toLong())
            }
        }
        postDelayed(blinkAction, 3000)
    }

    private fun animateBlink() {
        val animator = ValueAnimator.ofFloat(1f, 0f, 1f)
        animator.duration = 150
        animator.addUpdateListener {
            blinkProgress = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.DKGRAY
        alpha = 30
    }

    private var gridOffset = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // Draw Moving Background Grid
        gridOffset = (gridOffset + 0.5f) % 100f
        for (i in -100 until w.toInt() + 100 step 100) {
            canvas.drawLine(i + gridOffset, 0f, i + gridOffset, h, gridPaint)
        }
        for (i in -100 until h.toInt() + 100 step 100) {
            canvas.drawLine(0f, i + gridOffset, w, i + gridOffset, gridPaint)
        }
        postInvalidateOnAnimation()

        val themeColor = getThemeColor()

        // Draw Ripples (Behind the head)
        ripples.forEach {
            ripplePaint.alpha = it.alpha
            ripplePaint.color = themeColor
            canvas.drawCircle(it.x, it.y, it.radius, ripplePaint)
        }

        canvas.save()
        // Apply Head Tilt and Translation for more life
        canvas.translate(headTranslationX, headTranslationY)
        canvas.rotate(headTilt, w / 2, h / 2)

        // Draw Face Border with Theme Color Glow
        borderPaint.color = themeColor
        borderPaint.alpha = 50
        borderPaint.setShadowLayer(30f, 0f, 0f, themeColor)
        val borderRect = RectF(w * 0.05f, h * 0.1f, w * 0.95f, h * 0.9f)
        canvas.drawRoundRect(borderRect, 100f, 100f, borderPaint)

        eyeWidth = w * 0.18f
        eyeHeight = h * 0.35f
        eyeSpacing = w * 0.12f

        val centerY = h / 2f
        val leftEyeX = w / 2f - eyeSpacing - eyeWidth / 2f
        val rightEyeX = w / 2f + eyeSpacing + eyeWidth / 2f

        // Adjust offsets to be more responsive to center-focused tracking
        val offsetX = eyePositionOffset.x * (w * 0.15f)
        val offsetY = eyePositionOffset.y * (h * 0.15f)

        // Draw Brows
        drawBrows(canvas, leftEyeX + offsetX, rightEyeX + offsetX, centerY + offsetY - eyeHeight / 1.5f)

        // Draw Nose
        drawNose(canvas, w / 2f + offsetX, centerY + offsetY + eyeHeight / 3f)

        // Draw Mouth
        drawMouth(canvas, w / 2f + offsetX, centerY + offsetY + eyeHeight / 1.5f)

        // Draw Eyes
        drawEye(canvas, leftEyeX + offsetX, centerY + offsetY, true)
        drawEye(canvas, rightEyeX + offsetX, centerY + offsetY, false)

        // Draw Cheeks for happy expressions
        if (currentExpression == Expression.HAPPY || currentExpression == Expression.LOVE || currentExpression == Expression.SHY) {
            extraPaint.color = Color.parseColor("#44FF0000") // Soft red
            extraPaint.style = Paint.Style.FILL
            canvas.drawCircle(leftEyeX + offsetX - eyeWidth / 2, centerY + offsetY + eyeHeight / 2, 30f, extraPaint)
            canvas.drawCircle(rightEyeX + offsetX + eyeWidth / 2, centerY + offsetY + eyeHeight / 2, 30f, extraPaint)
        }
        
        canvas.restore()
    }

    private fun updateFeatureGradient(x: Float, y: Float, width: Float, height: Float) {
        val themeColor = getThemeColor()
        featurePaint.shader = LinearGradient(
            x - width / 2, y - height / 2, x + width / 2, y + height / 2,
            intArrayOf(Color.WHITE, themeColor, Color.WHITE),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.MIRROR
        )
        featurePaint.setShadowLayer(20f, 0f, 0f, themeColor)
    }

    private fun drawBrows(canvas: Canvas, lx: Float, rx: Float, y: Float) {
        val browWidth = eyeWidth * 1.0f
        val browHeight = 40f
        
        val browOffset = when (currentExpression) {
            Expression.ANGRY, Expression.EVIL -> 30f
            Expression.SAD, Expression.WORRIED, Expression.CRYING -> -30f
            Expression.SURPRISED, Expression.SHOCK -> -50f
            Expression.SQUINT, Expression.SMIRK -> 10f
            else -> 0f
        }

        // Left Brow
        updateFeatureGradient(lx, y, browWidth, browHeight)
        val lPath = Path()
        when (currentExpression) {
            Expression.ANGRY, Expression.EVIL -> {
                lPath.moveTo(lx - browWidth / 2, y - browOffset)
                lPath.lineTo(lx + browWidth / 2, y + browOffset)
            }
            Expression.SAD, Expression.WORRIED -> {
                lPath.moveTo(lx - browWidth / 2, y + browOffset)
                lPath.lineTo(lx + browWidth / 2, y - browOffset)
            }
            else -> {
                lPath.moveTo(lx - browWidth / 2, y + browOffset)
                lPath.lineTo(lx + browWidth / 2, y + browOffset)
            }
        }
        canvas.drawPath(lPath, featurePaint)

        // Right Brow
        updateFeatureGradient(rx, y, browWidth, browHeight)
        val rPath = Path()
        when (currentExpression) {
            Expression.ANGRY, Expression.EVIL -> {
                rPath.moveTo(rx - browWidth / 2, y + browOffset)
                rPath.lineTo(rx + browWidth / 2, y - browOffset)
            }
            Expression.SAD, Expression.WORRIED -> {
                rPath.moveTo(rx - browWidth / 2, y - browOffset)
                rPath.lineTo(rx + browWidth / 2, y + browOffset)
            }
            else -> {
                rPath.moveTo(rx - browWidth / 2, y + browOffset)
                rPath.lineTo(rx + browWidth / 2, y + browOffset)
            }
        }
        canvas.drawPath(rPath, featurePaint)
        featurePaint.shader = null
    }

    private fun drawNose(canvas: Canvas, x: Float, y: Float) {
        updateFeatureGradient(x, y, 60f, 60f)
        featurePaint.style = Paint.Style.STROKE
        
        val nosePath = Path()
        nosePath.moveTo(x - 25, y + 15)
        nosePath.lineTo(x, y - 25)
        nosePath.lineTo(x + 25, y + 15)
        canvas.drawPath(nosePath, featurePaint)
        
        glowPaint.color = Color.WHITE
        canvas.drawPoint(x, y - 10, glowPaint)
        featurePaint.shader = null
    }

    private fun drawMouth(canvas: Canvas, x: Float, y: Float) {
        val mouthWidth = eyeSpacing * 2.0f
        updateFeatureGradient(x, y, mouthWidth, 100f)
        val mouthPath = Path()
        
        when (currentExpression) {
            Expression.HAPPY, Expression.GRIN, Expression.LOVE -> {
                val rect = RectF(x - mouthWidth / 2, y - 40, x + mouthWidth / 2, y + 40)
                canvas.drawArc(rect, 0f, 180f, false, featurePaint)
            }
            Expression.SAD, Expression.WORRIED, Expression.CRYING -> {
                val rect = RectF(x - mouthWidth / 2, y + 20, x + mouthWidth / 2, y + 80)
                canvas.drawArc(rect, 180f, 180f, false, featurePaint)
            }
            Expression.SURPRISED, Expression.SHOCK -> {
                canvas.drawCircle(x, y + 40, 35f, featurePaint)
            }
            Expression.ANGRY, Expression.EVIL -> {
                canvas.drawLine(x - mouthWidth / 3, y + 40, x + mouthWidth / 3, y + 40, featurePaint)
                canvas.drawLine(x - mouthWidth / 3, y + 40, x - mouthWidth / 2, y + 20, featurePaint)
                canvas.drawLine(x + mouthWidth / 3, y + 40, x + mouthWidth / 2, y + 20, featurePaint)
            }
            else -> {
                canvas.drawLine(x - 50, y + 40, x + 50, y + 40, featurePaint)
            }
        }
        featurePaint.shader = null
    }

    private fun getThemeColor(): Int {
        return when (currentExpression) {
            Expression.HAPPY, Expression.LOVE, Expression.GRIN -> Color.parseColor("#00FF00") // Vibrant Green
            Expression.ANGRY, Expression.EVIL -> Color.parseColor("#FF0000") // Bright Red
            Expression.SAD, Expression.WORRIED, Expression.CRYING, Expression.DEAD -> Color.parseColor("#0099FF") // Sky Blue
            Expression.SURPRISED, Expression.SHOCK, Expression.CURIOUS -> Color.parseColor("#FFCC00") // Gold/Yellow
            Expression.DIZZY, Expression.SPIRAL -> Color.parseColor("#CC00FF") // Purple
            Expression.POWER_OFF -> Color.DKGRAY
            else -> Color.parseColor("#00FFCC") // Default Cyan
        }
    }

    private fun drawEye(canvas: Canvas, x: Float, y: Float, isLeft: Boolean) {
        if (currentExpression == Expression.POWER_OFF) return

        val themeColor = getThemeColor()
        val darkThemeColor = Color.argb(
            255, 
            (Color.red(themeColor) * 0.4).toInt(), 
            (Color.green(themeColor) * 0.4).toInt(), 
            (Color.blue(themeColor) * 0.4).toInt()
        )

        val gradient = RadialGradient(
            x, y, eyeWidth * 0.9f,
            intArrayOf(themeColor, darkThemeColor, Color.BLACK),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        eyePaint.shader = gradient
        eyePaint.style = Paint.Style.FILL

        var currentEyeHeight = eyeHeight * blinkProgress
        if (currentExpression == Expression.LISTENING) {
            currentEyeHeight = (eyeHeight * 0.7f) + (audioAmplitude * eyeHeight * 0.3f)
        }

        val rect = RectF(
            x - eyeWidth / 2f,
            y - currentEyeHeight / 2f,
            x + eyeWidth / 2f,
            y + currentEyeHeight / 2f
        )

        val save = canvas.save()
        
        when (currentExpression) {
            Expression.HEARTS, Expression.LOVE -> drawHeart(canvas, x, y, eyeWidth * 0.8f)
            Expression.STARS -> drawStar(canvas, x, y, 5, eyeWidth * 0.5f, eyeWidth * 0.25f)
            Expression.SPIRAL, Expression.DIZZY -> drawSpiral(canvas, x, y, eyeWidth * 0.4f)
            Expression.CROSS, Expression.DEAD -> drawCross(canvas, x, y, eyeWidth * 0.4f)
            Expression.TEARS, Expression.CRYING -> {
                drawStandardEye(canvas, rect)
                drawTears(canvas, x, y)
            }
            Expression.HAPPY, Expression.GRIN -> {
                canvas.drawRoundRect(rect, 40f, 40f, eyePaint)
                eyePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                canvas.drawRect(x - eyeWidth, y + currentEyeHeight * 0.1f, x + eyeWidth, y + eyeHeight, eyePaint)
                eyePaint.xfermode = null
            }
            Expression.SAD, Expression.WORRIED -> {
                canvas.drawRoundRect(rect, 40f, 40f, eyePaint)
                eyePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                val path = Path()
                if (isLeft) {
                    path.moveTo(x + eyeWidth, y - eyeHeight)
                    path.lineTo(x - eyeWidth, y - eyeHeight * 0.4f)
                    path.lineTo(x + eyeWidth, y - eyeHeight * 0.4f)
                } else {
                    path.moveTo(x - eyeWidth, y - eyeHeight)
                    path.lineTo(x + eyeWidth, y - eyeHeight * 0.4f)
                    path.lineTo(x - eyeWidth, y - eyeHeight * 0.4f)
                }
                path.close()
                canvas.drawPath(path, eyePaint)
                eyePaint.xfermode = null
            }
            Expression.ANGRY, Expression.EVIL -> {
                canvas.drawRoundRect(rect, 40f, 40f, eyePaint)
                eyePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                val path = Path()
                if (isLeft) {
                    path.moveTo(x - eyeWidth, y - eyeHeight)
                    path.lineTo(x + eyeWidth, y - eyeHeight * 0.2f)
                    path.lineTo(x - eyeWidth, y - eyeHeight * 0.2f)
                } else {
                    path.moveTo(x + eyeWidth, y - eyeHeight)
                    path.lineTo(x - eyeWidth, y - eyeHeight * 0.2f)
                    path.lineTo(x + eyeWidth, y - eyeHeight * 0.2f)
                }
                path.close()
                canvas.drawPath(path, eyePaint)
                eyePaint.xfermode = null
            }
            Expression.SLEEP, Expression.RELIEVED -> {
                val sleepRect = RectF(x - eyeWidth / 2f, y - 5f, x + eyeWidth / 2f, y + 5f)
                canvas.drawRoundRect(sleepRect, 5f, 5f, eyePaint)
            }
            Expression.SQUINT, Expression.SMIRK -> {
                val squintRect = RectF(x - eyeWidth / 2f, y - 20f, x + eyeWidth / 2f, y + 20f)
                canvas.drawRoundRect(squintRect, 10f, 10f, eyePaint)
            }
            Expression.SURPRISED, Expression.SHOCK, Expression.NERVOUS -> canvas.drawCircle(x, y, eyeWidth * 0.5f, eyePaint)
            Expression.WINK -> {
                if (isLeft) drawStandardEye(canvas, rect)
                else {
                    val winkRect = RectF(x - eyeWidth / 2f, y - 5f, x + eyeWidth / 2f, y + 5f)
                    canvas.drawRoundRect(winkRect, 5f, 5f, eyePaint)
                }
            }
            Expression.FLAT -> {
                val flatRect = RectF(x - eyeWidth / 2f, y - 10f, x + eyeWidth / 2f, y + 10f)
                canvas.drawRoundRect(flatRect, 5f, 5f, eyePaint)
            }
            Expression.CHEVRON_UP -> drawChevron(canvas, x, y, true)
            Expression.CHEVRON_DOWN -> drawChevron(canvas, x, y, false)
            Expression.SQUARE -> canvas.drawRect(rect, eyePaint)
            Expression.TRIANGLE -> drawTriangle(canvas, x, y, eyeWidth * 0.5f)
            Expression.DIAMOND -> drawDiamond(canvas, x, y, eyeWidth * 0.5f)
            Expression.HEXAGON -> drawHexagon(canvas, x, y, eyeWidth * 0.5f)
            Expression.PACMAN -> drawPacman(canvas, x, y, eyeWidth * 0.4f, isLeft)
            else -> drawStandardEye(canvas, rect)
        }
        
        canvas.restoreToCount(save)
        
        // Draw Inner Eye (Pupil) that tracks
        if (currentExpression != Expression.SLEEP && currentExpression != Expression.POWER_OFF && blinkProgress > 0.3f) {
            val pupilX = x + (eyePositionOffset.x * eyeWidth * 0.2f)
            val pupilY = y + (eyePositionOffset.y * currentEyeHeight * 0.2f)
            
            // Pupil shadow/glow
            pupilPaint.setShadowLayer(15f, 0f, 0f, themeColor)
            canvas.drawCircle(pupilX, pupilY, eyeWidth * 0.25f, pupilPaint)
            
            // Pupil core
            pupilPaint.color = Color.BLACK
            pupilPaint.setShadowLayer(0f, 0f, 0f, 0)
            canvas.drawCircle(pupilX, pupilY, eyeWidth * 0.15f, pupilPaint)

            // Inner iris highlight
            extraPaint.color = themeColor
            extraPaint.alpha = 100
            canvas.drawCircle(pupilX, pupilY, eyeWidth * 0.1f, extraPaint)
        }
        
        // Draw glint/reflection in eye
        if (currentExpression != Expression.SLEEP && currentExpression != Expression.POWER_OFF && blinkProgress > 0.5f) {
            extraPaint.color = Color.WHITE
            extraPaint.alpha = 180
            canvas.drawCircle(x - eyeWidth * 0.2f, y - currentEyeHeight * 0.2f, eyeWidth * 0.08f, extraPaint)
        }

        eyePaint.shader = null
    }

    private fun drawStandardEye(canvas: Canvas, rect: RectF) {
        canvas.drawRoundRect(rect, 50f, 50f, eyePaint)
    }

    private fun drawHeart(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path()
        path.moveTo(x, y + size / 4)
        path.cubicTo(x - size / 2, y - size / 2, x - size, y + size / 4, x, y + size)
        path.cubicTo(x + size, y + size / 4, x + size / 2, y - size / 2, x, y + size / 4)
        path.close()
        canvas.drawPath(path, eyePaint)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, spikes: Int, outerRadius: Float, innerRadius: Float) {
        val path = Path()
        var angle = Math.PI / 2 * 3
        val step = Math.PI / spikes
        path.moveTo(cx, (cy - outerRadius).toFloat())
        for (i in 0 until spikes) {
            path.lineTo((cx + cos(angle) * outerRadius).toFloat(), (cy + sin(angle) * outerRadius).toFloat())
            angle += step
            path.lineTo((cx + cos(angle) * innerRadius).toFloat(), (cy + sin(angle) * innerRadius).toFloat())
            angle += step
        }
        path.close()
        canvas.drawPath(path, eyePaint)
    }

    private fun drawSpiral(canvas: Canvas, x: Float, y: Float, radius: Float) {
        eyePaint.style = Paint.Style.STROKE
        eyePaint.strokeWidth = 10f
        val path = Path()
        for (i in 0 until 360 * 3 step 5) {
            val angle = Math.toRadians(i.toDouble())
            val r = (i.toDouble() / (360 * 3)) * radius
            val px = (x + cos(angle) * r).toFloat()
            val py = (y + sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        canvas.drawPath(path, eyePaint)
    }

    private fun drawCross(canvas: Canvas, x: Float, y: Float, size: Float) {
        eyePaint.style = Paint.Style.STROKE
        eyePaint.strokeWidth = 20f
        canvas.drawLine(x - size, y - size, x + size, y + size, eyePaint)
        canvas.drawLine(x + size, y - size, x - size, y + size, eyePaint)
    }

    private fun drawTears(canvas: Canvas, x: Float, y: Float) {
        extraPaint.color = Color.parseColor("#44AAFF")
        extraPaint.style = Paint.Style.FILL
        canvas.drawCircle(x, y + eyeHeight / 2 + 20f, 15f, extraPaint)
        canvas.drawCircle(x - 20, y + eyeHeight / 2 + 50f, 10f, extraPaint)
    }

    private fun drawChevron(canvas: Canvas, x: Float, y: Float, up: Boolean) {
        eyePaint.style = Paint.Style.STROKE
        eyePaint.strokeWidth = 20f
        val path = Path()
        val size = eyeWidth * 0.4f
        if (up) {
            path.moveTo(x - size, y + size / 2)
            path.lineTo(x, y - size / 2)
            path.lineTo(x + size, y + size / 2)
        } else {
            path.moveTo(x - size, y - size / 2)
            path.lineTo(x, y + size / 2)
            path.lineTo(x + size, y - size / 2)
        }
        canvas.drawPath(path, eyePaint)
    }

    private fun drawTriangle(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path()
        path.moveTo(x, y - size)
        path.lineTo(x - size, y + size)
        path.lineTo(x + size, y + size)
        path.close()
        canvas.drawPath(path, eyePaint)
    }

    private fun drawDiamond(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path()
        path.moveTo(x, y - size)
        path.lineTo(x + size, y)
        path.lineTo(x, y + size)
        path.lineTo(x - size, y)
        path.close()
        canvas.drawPath(path, eyePaint)
    }

    private fun drawHexagon(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60).toDouble())
            val px = (x + cos(angle) * size).toFloat()
            val py = (y + sin(angle) * size).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, eyePaint)
    }

    private fun drawPacman(canvas: Canvas, x: Float, y: Float, radius: Float, isLeft: Boolean) {
        val rect = RectF(x - radius, y - radius, x + radius, y + radius)
        if (isLeft) {
            canvas.drawArc(rect, 30f, 300f, true, eyePaint)
        } else {
            canvas.drawArc(rect, 210f, 300f, true, eyePaint)
        }
    }
}
