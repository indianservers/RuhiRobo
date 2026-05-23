package com.indianservers.ruhi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.indianservers.ruhi.databinding.ActivityMainBinding
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var lastExpressionTime = 0L

    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private val FALL_THRESHOLD = 30f // Threshold for detecting a fall/impact
    private val SHAKE_THRESHOLD = 15f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make Full Screen
        hideSystemUI()

        // Initialize TTS
        tts = TextToSpeech(this, this)

        // Initialize Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Request permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Use touch on the face to start listening since FAB is removed
        binding.contentMain.robotFaceView.setOnClickListener {
            startSpeechToText()
        }

        var currentExpIndex = 0
        val expressions = RobotFaceView.Expression.values()

        binding.contentMain.nextButton.setOnClickListener {
            currentExpIndex = (currentExpIndex + 1) % expressions.size
            val exp = expressions[currentExpIndex]
            binding.contentMain.robotFaceView.setExpression(exp)
            binding.contentMain.emotionTextView.text = "Variant: ${exp.name}"
        }

        binding.contentMain.prevButton.setOnClickListener {
            currentExpIndex = if (currentExpIndex - 1 < 0) expressions.size - 1 else currentExpIndex - 1
            val exp = expressions[currentExpIndex]
            binding.contentMain.robotFaceView.setExpression(exp)
            binding.contentMain.emotionTextView.text = "Variant: ${exp.name}"
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun startSpeechToText() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    binding.contentMain.robotFaceView.setExpression(RobotFaceView.Expression.LISTENING)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    binding.contentMain.robotFaceView.setAudioAmplitude(rmsdB / 10f)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    binding.contentMain.robotFaceView.showNeutral()
                }
                override fun onResults(results: Bundle?) {
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = data?.get(0) ?: ""
                    binding.contentMain.emotionTextView.text = text
                    binding.contentMain.robotFaceView.showHappy()
                    speak("I heard $text")
                    
                    binding.contentMain.robotFaceView.postDelayed({
                        binding.contentMain.robotFaceView.showNeutral()
                    }, 3000)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.contentMain.previewView.surfaceProvider)
            }

            val faceDetectorOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()

            val detector = FaceDetection.getClient(faceDetectorOptions)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(detector, imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(
        detector: com.google.mlkit.vision.face.FaceDetector,
        imageProxy: ImageProxy
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val smileProb = face.smilingProbability ?: -1f
                        val leftEyeOpen = face.leftEyeOpenProbability ?: -1f
                        val rightEyeOpen = face.rightEyeOpenProbability ?: -1f
                        
                        // Map face center to robot eye offset (-1.0 to 1.0)
                        val centerX = face.boundingBox.centerX().toFloat()
                        val centerY = face.boundingBox.centerY().toFloat()
                        
                        // Handle rotation for proper normalization
                        val isRotated = imageProxy.imageInfo.rotationDegrees % 180 != 0
                        val width = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
                        val height = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()

                        // Normalize to [-1, 1]
                        val offsetX = ((centerX / width) - 0.5f) * 2f
                        val offsetY = ((centerY / height) - 0.5f) * 2f
                        
                        runOnUiThread {
                            // Smooth the movement slightly
                            binding.contentMain.robotFaceView.setEyeOffset(-offsetX, offsetY)
                            
                            // Trigger expressions based on classified data
                            when {
                                smileProb > 0.8 -> binding.contentMain.robotFaceView.showHappy()
                                leftEyeOpen < 0.1 && rightEyeOpen < 0.1 -> binding.contentMain.robotFaceView.showSleep()
                                smileProb < 0.1 && face.headEulerAngleX > 15 -> binding.contentMain.robotFaceView.showAngry()
                                face.headEulerAngleZ > 20 || face.headEulerAngleZ < -20 -> binding.contentMain.robotFaceView.showCurious()
                                else -> binding.contentMain.robotFaceView.showNeutral()
                            }

                            val humanEmotion = when {
                                smileProb > 0.8 -> "Happy"
                                smileProb < 0.1 && face.headEulerAngleX > 15 -> "Angry"
                                leftEyeOpen < 0.1 && rightEyeOpen < 0.1 -> "Asleep"
                                else -> "Neutral"
                            }
                            binding.contentMain.emotionTextView.text = "Human: $humanEmotion"
                        }
                        lastExpressionTime = System.currentTimeMillis()
                    } else {
                        if (System.currentTimeMillis() - lastExpressionTime > 8000) {
                            runOnUiThread {
                                binding.contentMain.robotFaceView.showSleep()
                                binding.contentMain.robotFaceView.setEyeOffset(0f, 0f)
                            }
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Device Tilt for Head Tilt Effect (opposite to physical tilt for "stabilization")
                runOnUiThread {
                    binding.contentMain.robotFaceView.setHeadTilt(-x * 2f)
                    binding.contentMain.robotFaceView.setHeadTranslation(y * 10f, (z - 9.8f) * 10f)
                }

                // Detect sudden impact or fall
                val deltaX = Math.abs(x - lastAccelX)
                val deltaY = Math.abs(y - lastAccelY)
                val deltaZ = Math.abs(z - lastAccelZ)

                if (deltaX > FALL_THRESHOLD || deltaY > FALL_THRESHOLD || deltaZ > FALL_THRESHOLD) {
                    runOnUiThread {
                        binding.contentMain.robotFaceView.showShock()
                        speak("Ouch! That hurt.")
                    }
                } else if (deltaX > SHAKE_THRESHOLD || deltaY > SHAKE_THRESHOLD) {
                    runOnUiThread {
                        binding.contentMain.robotFaceView.showDizzy()
                    }
                }

                lastAccelX = x
                lastAccelY = y
                lastAccelZ = z
            }
            Sensor.TYPE_GYROSCOPE -> {
                val axisZ = event.values[2]

                // If rotating quickly
                if (Math.abs(axisZ) > 5f) {
                    runOnUiThread {
                        binding.contentMain.robotFaceView.setExpression(RobotFaceView.Expression.SPIRAL)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts.stop()
        tts.shutdown()
        speechRecognizer?.destroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            startCamera()
        }
    }

    companion object {
        private const val TAG = "RuhiRobo"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}
