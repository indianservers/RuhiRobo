package com.indianservers.ruhi

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GameActivity : AppCompatActivity() {
    private lateinit var face: RobotFaceView
    private lateinit var panel: ConstraintLayout
    private lateinit var title: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var achievementManager: AchievementManager
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("ruhi_games", MODE_PRIVATE) }

    private var latestMirrorExpression: RobotFaceView.Expression? = null
    private var copyRound = 0
    private var copyScore = 0
    private var simonRound = 1
    private var simonTarget = 1
    private var simonTaps = 0

    private val copyExpressions = listOf(
        RobotFaceView.Expression.HAPPY,
        RobotFaceView.Expression.SLEEP,
        RobotFaceView.Expression.SURPRISED,
        RobotFaceView.Expression.CURIOUS,
        RobotFaceView.Expression.SAD
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        achievementManager = AchievementManager(this, ConversationRepository(this))
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildLayout()
        lifecycleScope.launch { achievementManager.ensureDefaults() }
        startFaceAnalyzer()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        face = RobotFaceView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.4f)
        }
        panel = ConstraintLayout(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.6f)
            setPadding(32, 28, 32, 28)
        }
        root.addView(face)
        root.addView(panel)
        setContentView(root)
        showMenu()
    }

    private fun showMenu() {
        panel.removeAllViews()
        title = TextView(this).apply {
            id = View.generateViewId()
            text = "Ruhi Games"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        panel.addView(title)
        title.layoutParams = ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }

        var previousId = title.id
        listOf(
            "Copy My Face" to ::startCopyMyFace,
            "Mood Quiz" to ::startMoodQuiz,
            "Simon Says Blink" to ::startSimonBlink
        ).forEach { (label, action) ->
            val button = MaterialButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 18f
                setOnClickListener { action() }
            }
            panel.addView(button)
            button.layoutParams = ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                topToBottom = previousId
                topMargin = 20
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            previousId = button.id
        }
    }

    private fun startCopyMyFace() {
        copyRound = 0
        copyScore = 0
        nextCopyRound()
    }

    private fun nextCopyRound() {
        if (copyRound >= 5) {
            finishGame("copy_face", "Copy My Face score: $copyScore/5", copyScore)
            return
        }
        copyRound += 1
        val target = copyExpressions.random()
        face.setExpression(target)
        setPanelText("Copy My Face\nRound $copyRound/5\nMirror ${target.name.lowercase(Locale.US)}")
        handler.postDelayed({
            val matched = latestMirrorExpression == target
            if (matched) {
                copyScore += 1
                face.setExpression(RobotFaceView.Expression.HAPPY)
            } else {
                face.setExpression(RobotFaceView.Expression.SHOCK)
            }
            handler.postDelayed({ nextCopyRound() }, 900L)
        }, 5_000L)
    }

    private fun startMoodQuiz() {
        panel.removeAllViews()
        var score = 0
        var round = 0
        fun next() {
            if (round >= 5) {
                finishGame("mood_quiz", "Mood Quiz score: $score/5", score)
                return
            }
            round += 1
            panel.removeAllViews()
            val correct = copyExpressions.random()
            face.setExpression(correct)
            setPanelText("Mood Quiz\nRound $round/5")
            val options = (copyExpressions.shuffled().take(3) + correct).distinct().shuffled().take(4)
            var previousId = title.id
            options.forEach { option ->
                val button = MaterialButton(this).apply {
                    id = View.generateViewId()
                    text = option.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() }
                    setOnClickListener {
                        if (option == correct) {
                            score += 1
                            face.setExpression(RobotFaceView.Expression.HAPPY)
                            face.emitParticles(RobotFaceView.ParticleType.SPARKLE, 45)
                        } else {
                            face.setExpression(RobotFaceView.Expression.SHOCK)
                            ObjectAnimator.ofFloat(face, View.TRANSLATION_X, 0f, -18f, 18f, 0f).apply {
                                duration = 260L
                                start()
                            }
                        }
                        handler.postDelayed({ next() }, 700L)
                    }
                }
                panel.addView(button)
                button.layoutParams = ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
                    topToBottom = previousId
                    topMargin = 14
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }
                previousId = button.id
            }
        }
        next()
    }

    private fun startSimonBlink() {
        simonRound = 1
        simonTarget = 1
        simonTaps = 0
        nextSimonRound()
    }

    private fun nextSimonRound() {
        simonTarget = simonRound.coerceIn(1, 5)
        simonTaps = 0
        setPanelText("Simon Says Blink\nWatch Ruhi blink $simonTarget time(s)")
        repeat(simonTarget) { index ->
            handler.postDelayed({ face.forceSlowBlink() }, index * (650L - simonRound * 35L).coerceAtLeast(260L))
        }
        handler.postDelayed({ showSimonInput() }, 900L + simonTarget * 450L)
    }

    private fun showSimonInput() {
        panel.removeAllViews()
        setPanelText("Simon Says Blink\nTap the same count\nTaps: $simonTaps/$simonTarget")
        val tapButton = MaterialButton(this).apply {
            id = View.generateViewId()
            text = "Tap"
            textSize = 22f
            setOnClickListener {
                simonTaps += 1
                title.text = "Simon Says Blink\nTap the same count\nTaps: $simonTaps/$simonTarget"
            }
        }
        val submitButton = MaterialButton(this).apply {
            id = View.generateViewId()
            text = "Submit"
            setOnClickListener {
                if (simonTaps == simonTarget) {
                    simonRound += 1
                    face.setExpression(RobotFaceView.Expression.HAPPY)
                    if (simonRound > 5) {
                        val high = prefs.getInt("simon_high_score", 0).coerceAtLeast(simonRound - 1)
                        prefs.edit().putInt("simon_high_score", high).apply()
                        finishGame("simon_blink", "Simon high score: $high", 5)
                    } else {
                        handler.postDelayed({ nextSimonRound() }, 700L)
                    }
                } else {
                    val high = prefs.getInt("simon_high_score", 0).coerceAtLeast(simonRound - 1)
                    prefs.edit().putInt("simon_high_score", high).apply()
                    finishGame("simon_blink", "Simon score: ${simonRound - 1}. High: $high", simonRound - 1)
                }
            }
        }
        panel.addView(tapButton)
        panel.addView(submitButton)
        tapButton.layoutParams = ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            topToBottom = title.id
            topMargin = 20
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        submitButton.layoutParams = ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            topToBottom = tapButton.id
            topMargin = 14
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
    }

    private fun setPanelText(text: String) {
        panel.removeAllViews()
        title = TextView(this).apply {
            id = View.generateViewId()
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
        }
        panel.addView(title)
        title.layoutParams = ConstraintLayout.LayoutParams(0, ConstraintLayout.LayoutParams.WRAP_CONTENT).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
    }

    private fun finishGame(gameKey: String, message: String, score: Int) {
        setPanelText("$message\n\nTap to return")
        face.emitParticles(RobotFaceView.ParticleType.SPARKLE, 60)
        lifecycleScope.launch {
            val unlocked = achievementManager.recordGameCompleted(gameKey).toMutableList()
            if (score >= 5) {
                achievementManager.recordPerfectGame()?.let(unlocked::add)
            }
            unlocked.forEach { showAchievementUnlocked(it) }
        }
        title.setOnClickListener { showMenu() }
    }

    private fun showAchievementUnlocked(achievement: Achievement) {
        Snackbar.make(face, "* Achievement unlocked: ${achievement.title}", Snackbar.LENGTH_LONG).show()
        face.emitParticles(RobotFaceView.ParticleType.SPARKLE, 80)
    }

    private fun startFaceAnalyzer() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()
            )
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        analyzeFace(detector, imageProxy)
                    }
                }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analyzer)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeFace(detector: com.google.mlkit.vision.face.FaceDetector, imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull()
                latestMirrorExpression = when {
                    face == null -> null
                    (face.smilingProbability ?: -1f) > 0.7f -> RobotFaceView.Expression.HAPPY
                    (face.leftEyeOpenProbability ?: 1f) < 0.2f && (face.rightEyeOpenProbability ?: 1f) < 0.2f -> RobotFaceView.Expression.SLEEP
                    kotlin.math.abs(face.headEulerAngleZ) > 18f -> RobotFaceView.Expression.CURIOUS
                    (face.smilingProbability ?: 1f) < 0.2f -> RobotFaceView.Expression.SAD
                    else -> RobotFaceView.Expression.SURPRISED
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
