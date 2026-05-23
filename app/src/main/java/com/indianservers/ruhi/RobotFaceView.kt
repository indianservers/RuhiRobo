package com.indianservers.ruhi

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

open class RobotFaceView @JvmOverloads constructor(
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

    fun interface PetTouchListener {
        fun onPetTouch(zone: PetTouchZone, gesture: PetTouchGesture)
    }

    fun interface TouchSpeechListener {
        fun onTouchSpeech(text: String)
    }

    private var currentExpression = Expression.NEUTRAL
    private var previousExpression = Expression.NEUTRAL
    private var expressionBlend = 1f
    private var expressionAnimator: ValueAnimator? = null
    private var emotionalBlend = EmotionalBlend(Expression.NEUTRAL)
    private var petTouchListener: PetTouchListener? = null
    private var touchSpeechListener: TouchSpeechListener? = null
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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var blinkProgress = 1f
    private var eyePositionOffset = PointF(0f, 0f)
    private var audioAmplitude = 0f
    private var energyLevel = 0.72f
    private var affectionLevel = 0.45f
    private var curiosityLevel = 0.6f
    private var comfortLevel = 0.8f
    private var speaking = false
    private var mouthOpenRatio = 0f
    private var lifePhase = 0f
    private var backgroundTop = Color.BLACK
    private var backgroundBottom = Color.parseColor("#1A1A1A")
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bgAnimator: ValueAnimator? = null

    private var eyeWidth = 0f
    private var eyeHeight = 0f
    private var eyeSpacing = 0f
    private var eyeBeatScale = 1f
    private var beatHeadBobY = 0f
    private var cheekBlushBoost = 0f
    var facePositionX: Float = 0.5f
        private set
    var facePositionY: Float = 0.5f
        private set
    private var velocityX = 0f
    private var velocityY = 0f
    private val friction = 0.85f
    private val bounce = 0.4f
    private var squishScaleY = 1f
    private var scaredShakeUntil = 0L
    private var scaredShrinkUntil = 0L
    private var pupilScale = 1f
    private var microHeadTilt = 0f
    private var microEyeOffset = PointF(0f, 0f)
    private var microMouthTwitch = 0f
    private var dreamMode = false
    private var nightmareMode = false
    private var lastSleepCorner = 0
    private var targetSleepCorner: PointF? = null
    private var needValues = RobotNeeds()
    private var showNeedLabels = false

    // Ripple effect data
    private class Ripple(val x: Float, val y: Float, var radius: Float, var alpha: Int)
    private val ripples = mutableListOf<Ripple>()

    enum class ParticleType {
        HEART, STAR, TEAR, ZZZ, SPARKLE, CONFETTI
    }

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var maxLife: Float,
        var color: Int,
        var size: Float,
        var type: ParticleType,
        var rotation: Float = 0f
    )
    private val particleList: MutableList<Particle> = mutableListOf()

    var isIntimateMode: Boolean = false
        set(value) {
            field = value
            if (value) isAttentionMode = false
            invalidate()
        }

    var isAttentionMode: Boolean = false
        set(value) {
            field = value
            if (value) isIntimateMode = false
            invalidate()
        }

    var reducedBlinkRate: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isNightDimmed: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var headTilt = 0f
        set(value) {
            field = value
            invalidate()
        }

    var eyeColorOverride: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var particleEffectsEnabled: Boolean = true

    var faceBorderStyle: String = "Pulse"
        set(value) {
            field = value
            invalidate()
        }

    private var headTranslationX = 0f
    private var headTranslationY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var lastTapAt = 0L
    private var traveledDistance = 0f
    private var activeZone = PetTouchZone.BODY

    private val funFacts = listOf(
        "Octopuses have three hearts.",
        "Honey never spoils.",
        "Bananas are berries, but strawberries are not.",
        "A day on Venus is longer than its year.",
        "Sharks are older than trees.",
        "Wombat poop is cube-shaped.",
        "The Eiffel Tower can grow taller in summer.",
        "Some cats are allergic to humans.",
        "Butterflies taste with their feet.",
        "Your brain uses about 20 percent of your body's energy."
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                setExpression(Expression.GRIN)
                touchSpeechListener?.onTouchSpeech(funFacts.random())
                petTouchListener?.onPetTouch(zoneFor(e.x, e.y), PetTouchGesture.DOUBLE_TAP)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (e.y > height - dp(58f)) {
                    showNeedLabels = !showNeedLabels
                    invalidate()
                } else if (zoneFor(e.x, e.y) == PetTouchZone.NOSE) {
                    setExpression(Expression.WINK)
                    touchSpeechListener?.onTouchSpeech("Want to hear a secret?")
                    petTouchListener?.onPetTouch(PetTouchZone.NOSE, PetTouchGesture.HOLD)
                }
            }
        }
    )

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                return true
            }
        }
    )

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

    fun setHeadTranslation(x: Float, y: Float) {
        headTranslationX = x
        headTranslationY = y
        invalidate()
    }

    fun getCurrentExpression(): Expression = currentExpression

    fun setExpression(expression: Expression) {
        if (currentExpression != expression) {
            previousExpression = currentExpression
            currentExpression = expression
            emotionalBlend = emotionalBlend.copy(base = expression)
            animateBackgroundFor(expression)
            emitParticlesForExpression(expression)
            expressionAnimator?.cancel()
            expressionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 280L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    expressionBlend = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    fun setPetTouchListener(listener: PetTouchListener?) {
        petTouchListener = listener
    }

    fun setTouchSpeechListener(listener: TouchSpeechListener?) {
        touchSpeechListener = listener
    }

    fun setEmotionalBlend(blend: EmotionalBlend) {
        emotionalBlend = blend
        setExpression(blend.dominantExpression)
    }

    fun setEyeOffset(x: Float, y: Float) {
        eyePositionOffset.set(x, y)
        invalidate()
    }

    fun setAudioAmplitude(amplitude: Float) {
        audioAmplitude = amplitude.coerceIn(0f, 1.4f)
        invalidate()
    }

    fun setVitality(energy: Float, affection: Float, curiosity: Float, comfort: Float) {
        energyLevel = energy.coerceIn(0f, 1f)
        affectionLevel = affection.coerceIn(0f, 1f)
        curiosityLevel = curiosity.coerceIn(0f, 1f)
        comfortLevel = comfort.coerceIn(0f, 1f)
        invalidate()
    }

    fun setNeeds(needs: RobotNeeds) {
        needValues = needs
        if (needs.energy < RobotNeeds.CRITICAL && targetSleepCorner == null) {
            val corners = listOf(PointF(0.18f, 0.22f), PointF(0.82f, 0.22f), PointF(0.18f, 0.78f), PointF(0.82f, 0.78f))
            lastSleepCorner = (lastSleepCorner + 1) % corners.size
            targetSleepCorner = corners[lastSleepCorner]
        } else if (needs.energy > 0.35f) {
            targetSleepCorner = null
        }
        setVitality(needs.energy, needs.social, needs.stimulation, needs.comfort)
        invalidate()
    }

    fun setSpeaking(isSpeaking: Boolean) {
        speaking = isSpeaking
        invalidate()
    }

    fun setMouthOpenRatio(ratio: Float) {
        mouthOpenRatio = ratio.coerceIn(0f, 1f)
        invalidate()
    }

    fun applyPhysicsTilt(tiltX: Float, tiltY: Float, faceDown: Boolean = false, shaken: Boolean = false) {
        velocityX += tiltX.coerceIn(-1f, 1f) * 0.003f
        velocityY += tiltY.coerceIn(-1f, 1f) * 0.003f
        if (faceDown) {
            velocityY += 0.018f
            setExpression(Expression.DIZZY)
        }
        if (shaken) {
            velocityX += ((Math.random() - 0.5) * 0.06).toFloat()
            velocityY += ((Math.random() - 0.5) * 0.06).toFloat()
            setExpression(Expression.DIZZY)
        }
        invalidate()
    }

    fun jumpFromTilt() {
        velocityY -= 0.055f
        invalidate()
    }

    fun triggerLandingSquish() {
        ValueAnimator.ofFloat(1f, 0.8f, 1.1f, 1f).apply {
            duration = 260L
            addUpdateListener {
                squishScaleY = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun triggerScaredRecovery() {
        setExpression(Expression.NERVOUS)
        scaredShakeUntil = System.currentTimeMillis() + 1_200L
        scaredShrinkUntil = System.currentTimeMillis() + 1_600L
        pupilScale = 0.55f
        invalidate()
        postDelayed({
            setExpression(Expression.CURIOUS)
            pupilScale = 1f
        }, 1_650L)
    }

    fun applyMicroExpression(micro: MicroExpression) {
        microEyeOffset = PointF(micro.eyeOffsetX, micro.eyeOffsetY)
        pupilScale = micro.pupilScale
        microHeadTilt = micro.headTilt
        if (micro.type == MicroExpressionType.DOUBLE_BLINK) forceSlowBlink()
        if (micro.type == MicroExpressionType.MOUTH_TWITCH) microMouthTwitch = 1f
        if (micro.type == MicroExpressionType.SAD_FLICKER) {
            previousExpression = currentExpression
            currentExpression = Expression.SAD
            expressionBlend = 0.45f
        }
        invalidate()
        postDelayed({
            microEyeOffset = PointF(0f, 0f)
            pupilScale = 1f
            microHeadTilt = 0f
            microMouthTwitch = 0f
            if (micro.type == MicroExpressionType.SAD_FLICKER) {
                currentExpression = emotionalBlend.base
                expressionBlend = 1f
            }
            invalidate()
        }, micro.durationMs)
    }

    fun setDreamMode(enabled: Boolean, nightmare: Boolean = false) {
        dreamMode = enabled
        nightmareMode = nightmare
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                downAt = event.eventTime
                traveledDistance = 0f
                addRipple(event.x, event.y)
                activeZone = zoneFor(event.x, event.y)
                handleTouchDown(activeZone)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                traveledDistance += hypot(event.x - lastX, event.y - lastY)
                lastX = event.x
                lastY = event.y
                if (traveledDistance > width * 0.18f && event.eventTime - downAt > 180L) {
                    setEyeOffset(((event.x / width) - 0.5f) * 2f, ((event.y / height) - 0.5f) * 2f)
                }
                if (activeZone == PetTouchZone.CHIN && event.y - downY < -dp(18f)) {
                    cheekBlushBoost = 1f
                    setExpression(Expression.LOVE)
                    petTouchListener?.onPetTouch(PetTouchZone.CHIN, PetTouchGesture.SWIPE_UP)
                    emitParticles(ParticleType.HEART, 8, event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val zone = zoneFor(event.x, event.y)
                val gesture = gestureFor(event)
                petTouchListener?.onPetTouch(zone, gesture)
                if (gesture == PetTouchGesture.TAP || gesture == PetTouchGesture.DOUBLE_TAP) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun handleTouchDown(zone: PetTouchZone) {
        when (zone) {
            PetTouchZone.FOREHEAD -> {
                setExpression(Expression.HAPPY)
                touchSpeechListener?.onTouchSpeech("Mmm...")
                emitParticles(ParticleType.HEART, 12, width / 2f, height * 0.28f)
                petTouchListener?.onPetTouch(zone, PetTouchGesture.PETTING)
            }
            PetTouchZone.NOSE -> {
                setExpression(Expression.SURPRISED)
                touchSpeechListener?.onTouchSpeech("Hey! That tickles!")
                emitParticles(ParticleType.SPARKLE, 16, width / 2f, height / 2f)
                petTouchListener?.onPetTouch(zone, PetTouchGesture.TAP)
            }
            else -> Unit
        }
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

    fun emitParticles(type: com.indianservers.ruhi.ParticleType, count: Int) {
        emitParticles(ParticleType.valueOf(type.name), count)
    }

    fun emitParticles(type: ParticleType, count: Int, originX: Float = width / 2f, originY: Float = height / 2f) {
        if (!particleEffectsEnabled) return
        val palette = when (type) {
            ParticleType.HEART -> listOf(Color.parseColor("#FF5C8A"), Color.parseColor("#FF2D55"))
            ParticleType.STAR -> listOf(Color.parseColor("#FFE066"), Color.parseColor("#F7C948"))
            ParticleType.TEAR -> listOf(Color.parseColor("#4DABF7"), Color.parseColor("#74C0FC"))
            ParticleType.ZZZ -> listOf(Color.WHITE, Color.parseColor("#D8E4FF"))
            ParticleType.SPARKLE -> listOf(Color.WHITE, Color.parseColor("#B8FFF4"))
            ParticleType.CONFETTI -> listOf(Color.RED, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.GREEN)
        }
        repeat(count) {
            val angle = Math.random() * Math.PI * 2.0
            val speed = 1.5f + (Math.random() * 6.0).toFloat()
            val velocity = when (type) {
                ParticleType.HEART -> PointF(
                    ((Math.random() - 0.5) * 2.4).toFloat(),
                    (-2.6 - Math.random() * 3.8).toFloat()
                )
                ParticleType.TEAR -> PointF(
                    ((Math.random() - 0.5) * 1.2).toFloat(),
                    (2.0 + Math.random() * 3.2).toFloat()
                )
                ParticleType.ZZZ -> PointF(
                    (0.8 + Math.random() * 1.8).toFloat(),
                    (-1.4 - Math.random() * 2.0).toFloat()
                )
                ParticleType.STAR -> PointF(cos(angle).toFloat() * speed, sin(angle).toFloat() * speed)
                ParticleType.SPARKLE -> PointF(cos(angle).toFloat() * speed * 0.7f, sin(angle).toFloat() * speed * 0.7f)
                ParticleType.CONFETTI -> PointF(cos(angle).toFloat() * speed, sin(angle).toFloat() * speed - 1.2f)
            }
            val maxLife = 55f + (Math.random() * 45.0).toFloat()
            particleList.add(
                Particle(
                    x = originX,
                    y = originY,
                    vx = velocity.x,
                    vy = velocity.y,
                    life = maxLife,
                    maxLife = maxLife,
                    color = palette.random(),
                    size = 8f + (Math.random() * 18.0).toFloat(),
                    type = type
                )
            )
        }
        invalidate()
    }

    private fun zoneFor(x: Float, y: Float): PetTouchZone {
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()
        val centerX = w / 2f
        val centerY = h / 2f
        val zoneSpacing = eyeSpacing.takeIf { it > 0f } ?: (w * 0.12f)
        val noseRadius = dp(15f)
        val cheekHeight = zoneSpacing * 0.85f
        val leftCheek = RectF(
            centerX - zoneSpacing * 2.15f,
            centerY + zoneSpacing * 0.35f,
            centerX - zoneSpacing * 0.65f,
            centerY + zoneSpacing * 0.35f + cheekHeight
        )
        val rightCheek = RectF(
            centerX + zoneSpacing * 0.65f,
            centerY + zoneSpacing * 0.35f,
            centerX + zoneSpacing * 2.15f,
            centerY + zoneSpacing * 0.35f + cheekHeight
        )
        return when {
            y < centerY - zoneSpacing -> PetTouchZone.FOREHEAD
            hypot(x - centerX, y - centerY) <= noseRadius -> PetTouchZone.NOSE
            pointInOval(x, y, leftCheek) -> PetTouchZone.LEFT_CHEEK
            pointInOval(x, y, rightCheek) -> PetTouchZone.RIGHT_CHEEK
            y > centerY + zoneSpacing * 1.5f -> PetTouchZone.CHIN
            else -> PetTouchZone.BODY
        }
    }

    private fun pointInOval(x: Float, y: Float, oval: RectF): Boolean {
        val radiusX = oval.width() / 2f
        val radiusY = oval.height() / 2f
        if (radiusX <= 0f || radiusY <= 0f) return false
        val normalizedX = (x - oval.centerX()) / radiusX
        val normalizedY = (y - oval.centerY()) / radiusY
        return normalizedX * normalizedX + normalizedY * normalizedY <= 1f
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun gestureFor(event: MotionEvent): PetTouchGesture {
        val dx = event.x - downX
        val dy = event.y - downY
        val duration = event.eventTime - downAt
        val distance = hypot(dx, dy)
        val now = event.eventTime
        val doubleTap = now - lastTapAt < 320L && distance < 48f
        if (distance < 48f) lastTapAt = now

        return when {
            doubleTap -> PetTouchGesture.DOUBLE_TAP
            duration > 650L && distance < 72f -> PetTouchGesture.HOLD
            traveledDistance > width * 0.38f && duration > 260L && distance < width * 0.24f -> PetTouchGesture.PETTING
            abs(dx) > abs(dy) && abs(dx) > width * 0.14f -> {
                if (dx < 0f) PetTouchGesture.SWIPE_LEFT else PetTouchGesture.SWIPE_RIGHT
            }
            abs(dy) > height * 0.14f -> {
                if (dy < 0f) PetTouchGesture.SWIPE_UP else PetTouchGesture.SWIPE_DOWN
            }
            else -> PetTouchGesture.TAP
        }
    }

    private fun startBlinking() {
        val blinkAction = object : Runnable {
            override fun run() {
                if (currentExpression != Expression.SLEEP && currentExpression != Expression.POWER_OFF) {
                    animateBlink()
                }
                val delayRange = if (reducedBlinkRate) 7000..13000 else 2000..6000
                postDelayed(this, delayRange.random().toLong())
            }
        }
        postDelayed(blinkAction, 3000)
    }

    fun forceSlowBlink() {
        animateBlink(durationMs = 650L)
    }

    fun onBeat() {
        ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = 150L
            addUpdateListener {
                eyeBeatScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        if (currentExpression == Expression.HAPPY || currentExpression == Expression.GRIN) {
            ValueAnimator.ofFloat(0f, -dp(5f), dp(5f), 0f).apply {
                duration = 150L
                addUpdateListener {
                    beatHeadBobY = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    private fun animateBlink(durationMs: Long = 150L) {
        val animator = ValueAnimator.ofFloat(1f, 0f, 1f)
        animator.duration = durationMs
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

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        backgroundPaint.shader = LinearGradient(0f, 0f, 0f, h, backgroundTop, backgroundBottom, Shader.TileMode.CLAMP)
        backgroundPaint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)
        backgroundPaint.shader = null
        if (isNightDimmed) {
            extraPaint.color = Color.BLACK
            extraPaint.alpha = 95
            extraPaint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w, h, extraPaint)
            extraPaint.alpha = 255
        }

        updateScreenPhysics()

        // Draw Moving Background Grid
        val proximitySpeedScale = when {
            isIntimateMode -> 0.55f
            isAttentionMode -> 1.15f
            else -> 1f
        }
        val vitalitySpeed = (0.35f + (energyLevel * 0.45f) + (curiosityLevel * 0.2f)) * proximitySpeedScale
        gridOffset = (gridOffset + vitalitySpeed) % 100f
        lifePhase = (lifePhase + (0.035f + (energyLevel * 0.02f)) * proximitySpeedScale) % 360f
        for (i in -100 until w.toInt() + 100 step 100) {
            canvas.drawLine(i + gridOffset, 0f, i + gridOffset, h, gridPaint)
        }
        for (i in -100 until h.toInt() + 100 step 100) {
            canvas.drawLine(0f, i + gridOffset, w, i + gridOffset, gridPaint)
        }
        postInvalidateOnAnimation()

        val themeColor = getThemeColor()

        updateAndDrawParticles(canvas)

        // Draw Ripples (Behind the head)
        ripples.forEach {
            ripplePaint.alpha = it.alpha
            ripplePaint.color = themeColor
            canvas.drawCircle(it.x, it.y, it.radius, ripplePaint)
        }

        canvas.save()
        // Apply Head Tilt and Translation for more life
        val breathCycle = when {
            sleepWeight() > 0.4f -> 0.45f
            currentExpression in listOf(Expression.NERVOUS, Expression.SHOCK, Expression.SURPRISED) -> 2.6f
            currentExpression in listOf(Expression.HAPPY, Expression.GRIN, Expression.LOVE) -> 1.45f
            else -> 1f
        }
        val breathDepth = when {
            sleepWeight() > 0.4f -> 0.04f
            currentExpression in listOf(Expression.NERVOUS, Expression.SHOCK, Expression.SURPRISED) -> 0.022f
            else -> 0.015f
        }
        val breath = sin((lifePhase * breathCycle).toDouble()).toFloat() * (4f + affectionLevel * 8f)
        val sleepyDrift = sleepWeight() * sin((lifePhase * 0.35f).toDouble()).toFloat() * 18f
        val tinyHeadBob = sin((lifePhase * 1.6f).toDouble()).toFloat() * (2f + energyLevel * 4f)
        val tinyCuriousSway = cos((lifePhase * 0.55f).toDouble()).toFloat() * curiosityLevel * 5f
        val physicsX = (facePositionX - 0.5f) * w
        val physicsY = (facePositionY - 0.5f) * h
        val scaredShake = if (System.currentTimeMillis() < scaredShakeUntil) sin((lifePhase * 28f).toDouble()).toFloat() * dp(8f) else 0f
        val bodyScale = (1f + sin((lifePhase * breathCycle).toDouble()).toFloat() * breathDepth) *
            if (System.currentTimeMillis() < scaredShrinkUntil) 0.72f else 1f
        canvas.translate(physicsX + scaredShake + headTranslationX + tinyCuriousSway + sleepyDrift, physicsY + headTranslationY + breath + tinyHeadBob + beatHeadBobY)
        canvas.scale(bodyScale, bodyScale * squishScaleY, w / 2f, h / 2f)
        canvas.rotate(headTilt + microHeadTilt + sleepyDrift * 0.25f, w / 2, h / 2)

        // Draw Face Border with Theme Color Glow
        borderPaint.color = themeColor
        if (faceBorderStyle != "Off") {
            val attentionBoost = if (isAttentionMode || faceBorderStyle == "Pulse") 80 else 0
            val shadowBoost = if (isAttentionMode || faceBorderStyle == "Pulse") 35f else 0f
            borderPaint.alpha = if (faceBorderStyle == "Solid") 140 else (35 + affectionLevel * 70 + attentionBoost).toInt().coerceIn(0, 220)
            borderPaint.setShadowLayer(20f + affectionLevel * 35f + shadowBoost, 0f, 0f, themeColor)
            if (faceBorderStyle == "Rainbow") {
                borderPaint.shader = SweepGradient(w / 2f, h / 2f, intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.MAGENTA, Color.RED), null)
            }
            val borderRect = RectF(w * 0.05f, h * 0.1f, w * 0.95f, h * 0.9f)
            canvas.drawRoundRect(borderRect, 100f, 100f, borderPaint)
            borderPaint.shader = null
        }

        val proximityEyeScale = when {
            isIntimateMode -> 0.8f
            isAttentionMode -> 1.2f
            else -> 1f
        }
        eyeWidth = w * 0.18f * proximityEyeScale * eyeBeatScale
        eyeHeight = h * (0.28f + energyLevel * 0.09f) * proximityEyeScale * eyeBeatScale
        eyeSpacing = w * 0.12f

        val centerY = h / 2f
        val leftEyeX = w / 2f - eyeSpacing - eyeWidth / 2f
        val rightEyeX = w / 2f + eyeSpacing + eyeWidth / 2f

        // Adjust offsets to be more responsive to center-focused tracking
        val idleLook = idleEyeOffset()
        val offsetX = (eyePositionOffset.x + idleLook.x + microEyeOffset.x) * (w * 0.15f)
        val offsetY = (eyePositionOffset.y + idleLook.y + microEyeOffset.y) * (h * 0.15f) + breath * 0.45f

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
        val blushWeight = emotionWeight(Expression.HAPPY) + emotionWeight(Expression.LOVE) + emotionWeight(Expression.SHY) + cheekBlushBoost
        if (blushWeight > 0.2f) {
            extraPaint.color = Color.parseColor("#44FF0000") // Soft red
            extraPaint.style = Paint.Style.FILL
            canvas.drawCircle(leftEyeX + offsetX - eyeWidth / 2, centerY + offsetY + eyeHeight / 2, 30f, extraPaint)
            canvas.drawCircle(rightEyeX + offsetX + eyeWidth / 2, centerY + offsetY + eyeHeight / 2, 30f, extraPaint)
            cheekBlushBoost = (cheekBlushBoost - 0.025f).coerceAtLeast(0f)
        }
        
        canvas.restore()

        drawDreamOverlay(canvas, w, h)
        drawNeedDots(canvas, w, h)
    }

    private fun updateScreenPhysics() {
        targetSleepCorner?.let { target ->
            velocityX += (target.x - facePositionX) * 0.002f
            velocityY += (target.y - facePositionY) * 0.002f
            if (currentExpression == Expression.SLEEP) emitParticles(ParticleType.ZZZ, 1, width * target.x, height * target.y)
        }
        facePositionX += velocityX
        facePositionY += velocityY
        velocityX *= friction
        velocityY *= friction
        var collided = false
        if (facePositionX < 0.12f) {
            facePositionX = 0.12f
            velocityX = -velocityX * bounce
            collided = true
        } else if (facePositionX > 0.88f) {
            facePositionX = 0.88f
            velocityX = -velocityX * bounce
            collided = true
        }
        if (facePositionY < 0.16f) {
            facePositionY = 0.16f
            velocityY = -velocityY * bounce
            collided = true
        } else if (facePositionY > 0.84f) {
            facePositionY = 0.84f
            velocityY = -velocityY * bounce
            collided = true
            if (kotlin.math.abs(velocityY) > 0.008f) triggerLandingSquish()
        }
        if (collided && currentExpression !in listOf(Expression.SLEEP, Expression.POWER_OFF)) {
            previousExpression = currentExpression
            currentExpression = Expression.SHOCK
            postDelayed({ currentExpression = emotionalBlend.base; invalidate() }, 180L)
        }
    }

    private fun drawNeedDots(canvas: Canvas, w: Float, h: Float) {
        val values = listOf(
            "energy" to needValues.energy,
            "social" to needValues.social,
            "stimulation" to needValues.stimulation,
            "comfort" to needValues.comfort,
            "expression" to needValues.expression,
            "safety" to needValues.safety
        )
        val spacing = dp(22f)
        val startX = w / 2f - spacing * (values.size - 1) / 2f
        values.forEachIndexed { index, (name, value) ->
            val pulse = if (value < RobotNeeds.CRITICAL) 1f + abs(sin((lifePhase * 3f).toDouble()).toFloat()) * 0.65f else 1f
            extraPaint.color = when {
                value > 0.7f -> Color.parseColor("#4ADE80")
                value > 0.3f -> Color.parseColor("#FACC15")
                else -> Color.parseColor("#F87171")
            }
            extraPaint.alpha = 190
            canvas.drawCircle(startX + index * spacing, h - dp(18f), dp(4.2f) * pulse, extraPaint)
            if (showNeedLabels) {
                textPaint.textSize = dp(10f)
                textPaint.alpha = 210
                canvas.drawText("${name.take(3)} ${(value * 100).toInt()}%", startX + index * spacing - dp(18f), h - dp(30f) - index % 2 * dp(12f), textPaint)
            }
        }
        extraPaint.alpha = 255
        textPaint.alpha = 255
        textPaint.textSize = 32f
    }

    private fun drawDreamOverlay(canvas: Canvas, w: Float, h: Float) {
        if (!dreamMode) return
        extraPaint.style = Paint.Style.FILL
        repeat(5) { index ->
            val phase = lifePhase * (0.08f + index * 0.02f) + index * 1.7f
            val x = w * (0.2f + index * 0.15f) + sin(phase.toDouble()).toFloat() * dp(30f)
            val y = h * (0.25f + (index % 3) * 0.16f) + cos((phase * 0.7f).toDouble()).toFloat() * dp(22f)
            extraPaint.color = if (nightmareMode) Color.argb(70, 80, 0, 40) else Color.argb(58, 120 + index * 20, 170, 255)
            canvas.drawOval(RectF(x - dp(32f), y - dp(18f), x + dp(32f), y + dp(18f)), extraPaint)
        }
    }

    private fun updateAndDrawParticles(canvas: Canvas) {
        val iterator = particleList.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            if (particle.life <= 0f) {
                iterator.remove()
                continue
            }
            val t = 1f - (particle.life / particle.maxLife)
            particle.x += particle.vx
            particle.y += particle.vy
            if (particle.type in listOf(ParticleType.TEAR, ParticleType.CONFETTI)) {
                particle.vy += 0.15f
            }
            particle.rotation += 8f
            extraPaint.color = particle.color
            extraPaint.alpha = ((1f - t) * 255).toInt()
            extraPaint.style = Paint.Style.FILL
            when (particle.type) {
                ParticleType.HEART -> drawHeart(canvas, particle.x, particle.y, particle.size)
                ParticleType.STAR -> drawStar(canvas, particle.x, particle.y, 6, particle.size, particle.size * 0.45f)
                ParticleType.TEAR -> canvas.drawOval(
                    RectF(particle.x - particle.size * 0.35f, particle.y - particle.size, particle.x + particle.size * 0.35f, particle.y + particle.size),
                    extraPaint
                )
                ParticleType.ZZZ -> {
                    textPaint.alpha = extraPaint.alpha
                    canvas.drawText("z", particle.x, particle.y, textPaint)
                }
                ParticleType.SPARKLE -> canvas.drawCircle(particle.x, particle.y, particle.size * 0.35f, extraPaint)
                ParticleType.CONFETTI -> {
                    canvas.save()
                    canvas.rotate(particle.rotation, particle.x, particle.y)
                    canvas.drawRect(particle.x - particle.size, particle.y - particle.size * 0.35f, particle.x + particle.size, particle.y + particle.size * 0.35f, extraPaint)
                    canvas.restore()
                }
            }
            particle.life -= 1f
        }
        extraPaint.alpha = 255
        textPaint.alpha = 255
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
        val angry = emotionWeight(Expression.ANGRY) + emotionWeight(Expression.EVIL)
        val sad = emotionWeight(Expression.SAD) + emotionWeight(Expression.WORRIED) + emotionWeight(Expression.CRYING)
        val surprised = emotionWeight(Expression.SURPRISED) + emotionWeight(Expression.SHOCK)
        val squint = emotionWeight(Expression.SQUINT) + emotionWeight(Expression.SMIRK)
        val browOffset = 30f * angry - 30f * sad - 50f * surprised + 10f * squint

        // Left Brow
        updateFeatureGradient(lx, y, browWidth, browHeight)
        val lPath = Path()
        when {
            angry > sad && angry > 0.1f -> {
                lPath.moveTo(lx - browWidth / 2, y - browOffset)
                lPath.lineTo(lx + browWidth / 2, y + browOffset)
            }
            sad > 0.1f -> {
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
        when {
            angry > sad && angry > 0.1f -> {
                rPath.moveTo(rx - browWidth / 2, y + browOffset)
                rPath.lineTo(rx + browWidth / 2, y - browOffset)
            }
            sad > 0.1f -> {
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
        val happy = emotionWeight(Expression.HAPPY) + emotionWeight(Expression.GRIN) + emotionWeight(Expression.LOVE)
        val sad = emotionWeight(Expression.SAD) + emotionWeight(Expression.WORRIED) + emotionWeight(Expression.CRYING)
        val shocked = emotionWeight(Expression.SURPRISED) + emotionWeight(Expression.SHOCK)
        val angry = emotionWeight(Expression.ANGRY) + emotionWeight(Expression.EVIL)
        val listening = emotionWeight(Expression.LISTENING)
        val thinking = emotionWeight(Expression.THINKING)

        if (speaking) {
            val open = if (mouthOpenRatio > 0f) 12f + mouthOpenRatio * 52f else mouthOpenAmount()
            val speechRect = RectF(x - 55, y + 20, x + 55, y + 20 + open)
            canvas.drawOval(speechRect, featurePaint)
            featurePaint.shader = null
            return
        }

        when (currentExpression) {
            Expression.LISTENING -> {
                val listeningHeight = 15f + audioAmplitude * 70f
                canvas.drawLine(x - mouthWidth / 2, y + 40 - listeningHeight, x - mouthWidth / 2, y + 40 + listeningHeight, featurePaint)
                canvas.drawLine(x, y + 40 - listeningHeight * 0.7f, x, y + 40 + listeningHeight * 0.7f, featurePaint)
                canvas.drawLine(x + mouthWidth / 2, y + 40 - listeningHeight, x + mouthWidth / 2, y + 40 + listeningHeight, featurePaint)
            }
            Expression.THINKING -> {
                canvas.drawCircle(x - 40, y + 40, 7f, featurePaint)
                canvas.drawCircle(x, y + 40, 7f, featurePaint)
                canvas.drawCircle(x + 40, y + 40, 7f, featurePaint)
            }
            Expression.HAPPY, Expression.GRIN, Expression.LOVE -> {
                val rect = RectF(x - mouthWidth / 2, y - 40, x + mouthWidth / 2, y + 40)
                canvas.drawArc(rect, 0f, 180f, false, featurePaint)
                if (microMouthTwitch > 0f) {
                    canvas.drawLine(x + mouthWidth * 0.22f, y + 38f, x + mouthWidth * 0.42f, y + 28f, featurePaint)
                }
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
                if (listening > 0.15f) {
                    val listeningHeight = 12f + audioAmplitude * 65f
                    canvas.drawLine(x - mouthWidth / 3, y + 40 - listeningHeight, x - mouthWidth / 3, y + 40 + listeningHeight, featurePaint)
                    canvas.drawLine(x + mouthWidth / 3, y + 40 - listeningHeight, x + mouthWidth / 3, y + 40 + listeningHeight, featurePaint)
                } else if (thinking > 0.15f) {
                    canvas.drawCircle(x - 40, y + 40, 7f, featurePaint)
                    canvas.drawCircle(x, y + 40, 7f, featurePaint)
                    canvas.drawCircle(x + 40, y + 40, 7f, featurePaint)
                } else if (shocked > 0.25f) {
                    canvas.drawCircle(x, y + 40, 18f + 22f * shocked, featurePaint)
                } else if (angry > 0.25f) {
                    canvas.drawLine(x - mouthWidth / 3, y + 40, x + mouthWidth / 3, y + 40, featurePaint)
                    canvas.drawLine(x - mouthWidth / 3, y + 40, x - mouthWidth / 2, y + 20, featurePaint)
                    canvas.drawLine(x + mouthWidth / 3, y + 40, x + mouthWidth / 2, y + 20, featurePaint)
                } else if (happy > 0.25f || sad > 0.25f) {
                    val arcTop = if (happy >= sad) y - 40 else y + 20
                    val arcBottom = if (happy >= sad) y + 40 else y + 80
                    val start = if (happy >= sad) 0f else 180f
                    canvas.drawArc(RectF(x - mouthWidth / 2, arcTop, x + mouthWidth / 2, arcBottom), start, 180f, false, featurePaint)
                } else {
                    canvas.drawLine(x - 50, y + 40, x + 50, y + 40, featurePaint)
                }
            }
        }
        featurePaint.shader = null
    }

    private fun getThemeColor(): Int {
        val previous = colorForExpression(previousExpression)
        val current = colorForExpression(emotionalBlend.base)
        val base = blendColor(previous, current, expressionBlend)
        val overlay = emotionalBlend.overlay?.let { colorForExpression(it) } ?: base
        val blended = blendColor(base, overlay, emotionalBlend.overlayIntensity)
        eyeColorOverride?.let { return it }
        return when {
            isIntimateMode -> blendColor(blended, Color.parseColor("#FFB6C1"), 0.72f)
            isAttentionMode -> blendColor(blended, Color.WHITE, 0.18f)
            else -> blended
        }
    }

    private fun getBackgroundColorsForExpression(expression: Expression): Pair<Int, Int> {
        return when (expression) {
            Expression.HAPPY, Expression.LOVE, Expression.HEARTS, Expression.GRIN -> Color.parseColor("#FF6B35") to Color.parseColor("#FF4D8D")
            Expression.SAD, Expression.CRYING, Expression.WORRIED -> Color.parseColor("#1A1A2E") to Color.parseColor("#16213E")
            Expression.ANGRY, Expression.EVIL -> Color.parseColor("#2D0000") to Color.parseColor("#151515")
            Expression.SLEEP -> Color.parseColor("#0D0221") to Color.parseColor("#050009")
            Expression.CURIOUS, Expression.THINKING -> Color.parseColor("#007C89") to Color.parseColor("#001B2E")
            Expression.STARS, Expression.SURPRISED, Expression.SHOCK -> Color.parseColor("#F7C948") to Color.parseColor("#F4A261")
            else -> Color.parseColor("#0A0A0A") to Color.parseColor("#1A1A1A")
        }
    }

    private fun animateBackgroundFor(expression: Expression) {
        val target = getBackgroundColorsForExpression(expression)
        val startTop = backgroundTop
        val startBottom = backgroundBottom
        bgAnimator?.cancel()
        bgAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800L
            addUpdateListener {
                val fraction = it.animatedValue as Float
                val evaluator = ArgbEvaluator()
                backgroundTop = evaluator.evaluate(fraction, startTop, target.first) as Int
                backgroundBottom = evaluator.evaluate(fraction, startBottom, target.second) as Int
                invalidate()
            }
            start()
        }
    }

    private fun emitParticlesForExpression(expression: Expression) {
        val originX = width / 2f
        val originY = height / 2f
        when (expression) {
            Expression.HAPPY, Expression.LOVE -> emitParticles(ParticleType.HEART, 10, originX, originY)
            Expression.SAD, Expression.CRYING -> emitParticles(ParticleType.TEAR, 8, originX, originY)
            Expression.SLEEP -> emitParticles(ParticleType.ZZZ, 6, originX + width * 0.18f, originY - height * 0.2f)
            Expression.SURPRISED, Expression.SHOCK -> emitParticles(ParticleType.STAR, 14, originX, originY)
            Expression.STARS, Expression.HEARTS -> emitParticles(ParticleType.SPARKLE, 18, originX, originY)
            else -> Unit
        }
    }

    private fun colorForExpression(expression: Expression): Int {
        return when (expression) {
            Expression.HAPPY, Expression.LOVE, Expression.GRIN -> Color.parseColor("#00FF00") // Vibrant Green
            Expression.ANGRY, Expression.EVIL -> Color.parseColor("#FF0000") // Bright Red
            Expression.SAD, Expression.WORRIED, Expression.CRYING, Expression.DEAD -> Color.parseColor("#0099FF") // Sky Blue
            Expression.SURPRISED, Expression.SHOCK, Expression.CURIOUS -> Color.parseColor("#FFCC00") // Gold/Yellow
            Expression.DIZZY, Expression.SPIRAL -> Color.parseColor("#CC00FF") // Purple
            Expression.POWER_OFF -> Color.DKGRAY
            else -> blendColor(Color.parseColor("#00FFCC"), Color.parseColor("#FF66AA"), affectionLevel * 0.35f)
        }
    }

    private fun blendColor(startColor: Int, endColor: Int, ratio: Float): Int {
        val amount = ratio.coerceIn(0f, 1f)
        val inverse = 1f - amount
        return Color.rgb(
            (Color.red(startColor) * inverse + Color.red(endColor) * amount).toInt(),
            (Color.green(startColor) * inverse + Color.green(endColor) * amount).toInt(),
            (Color.blue(startColor) * inverse + Color.blue(endColor) * amount).toInt()
        )
    }

    private fun emotionWeight(expression: Expression): Float {
        val previousWeight = if (previousExpression == expression) 1f - expressionBlend else 0f
        val currentWeight = if (currentExpression == expression) expressionBlend else 0f
        return (previousWeight + currentWeight).coerceIn(0f, 1f)
    }

    private fun sleepWeight(): Float {
        return emotionWeight(Expression.SLEEP) + emotionWeight(Expression.RELIEVED) * 0.45f
    }

    private fun idleEyeOffset(): PointF {
        if (abs(eyePositionOffset.x) > 0.04f || abs(eyePositionOffset.y) > 0.04f) {
            return PointF(0f, 0f)
        }

        val expressionLookX = when (currentExpression) {
            Expression.LEFT_LOOK, Expression.DIAGONAL_LEFT -> -0.75f
            Expression.RIGHT_LOOK, Expression.DIAGONAL_RIGHT -> 0.75f
            else -> 0f
        }
        val expressionLookY = when (currentExpression) {
            Expression.UP_LOOK, Expression.CHEVRON_UP, Expression.DIAGONAL_LEFT, Expression.DIAGONAL_RIGHT -> -0.55f
            Expression.DOWN_LOOK, Expression.CHEVRON_DOWN -> 0.55f
            else -> 0f
        }

        val scanX = sin((lifePhase * 0.62f).toDouble()).toFloat() * curiosityLevel * 0.24f
        val scanY = cos((lifePhase * 0.43f).toDouble()).toFloat() * curiosityLevel * 0.14f
        val sleepyY = sleepWeight() * 0.18f
        return PointF(
            expressionLookX + scanX,
            expressionLookY + scanY + sleepyY
        )
    }

    private fun mouthOpenAmount(): Float {
        val syllableA = sin((lifePhase * 8.0f).toDouble()).toFloat().coerceAtLeast(0f)
        val syllableB = sin((lifePhase * 13.0f + 1.7f).toDouble()).toFloat().coerceAtLeast(0f)
        return 12f + (syllableA * 28f) + (syllableB * 16f)
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

        val sleepy = sleepWeight()
        val happy = emotionWeight(Expression.HAPPY) + emotionWeight(Expression.GRIN)
        val squint = emotionWeight(Expression.SQUINT) + emotionWeight(Expression.SMIRK)
        val shocked = emotionWeight(Expression.SURPRISED) + emotionWeight(Expression.SHOCK) + emotionWeight(Expression.NERVOUS)
        val listening = emotionWeight(Expression.LISTENING)
        var currentEyeHeight = eyeHeight * blinkProgress
        currentEyeHeight *= (1f - sleepy * 0.88f - happy * 0.38f - squint * 0.72f).coerceIn(0.08f, 1.35f)
        currentEyeHeight += listening * audioAmplitude * eyeHeight * 0.35f
        currentEyeHeight += shocked * eyeHeight * 0.12f

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
            canvas.drawCircle(pupilX, pupilY, eyeWidth * 0.25f * pupilScale, pupilPaint)
            
            // Pupil core
            pupilPaint.color = Color.BLACK
            pupilPaint.setShadowLayer(0f, 0f, 0f, 0)
            canvas.drawCircle(pupilX, pupilY, eyeWidth * 0.15f * pupilScale, pupilPaint)

            // Inner iris highlight
            extraPaint.color = themeColor
            extraPaint.alpha = 100
            canvas.drawCircle(pupilX, pupilY, eyeWidth * 0.1f * pupilScale, extraPaint)
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
