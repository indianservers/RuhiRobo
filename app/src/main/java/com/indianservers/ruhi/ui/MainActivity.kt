package com.indianservers.ruhi.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.indianservers.ruhi.ConversationManager
import com.indianservers.ruhi.PetTouchGesture
import com.indianservers.ruhi.PetTouchZone
import com.indianservers.ruhi.R
import com.indianservers.ruhi.RuhiWidget
import com.indianservers.ruhi.SettingsActivity
import com.indianservers.ruhi.databinding.ActivityMainBinding
import com.indianservers.ruhi.hardware.BleRobotManager
import com.indianservers.ruhi.repository.CameraRepository
import com.indianservers.ruhi.repository.ConversationRepository
import com.indianservers.ruhi.repository.SensorRepository
import com.indianservers.ruhi.viewmodel.RuhiViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: RuhiViewModel
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var sensorRepository: SensorRepository
    private var currentExpression = com.indianservers.ruhi.RobotFaceView.Expression.NEUTRAL
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tts = TextToSpeech(this, this)
        val rootRepo = com.indianservers.ruhi.ConversationRepository(this)
        viewModel = ViewModelProvider(
            this,
            RuhiViewModel.Factory(ConversationRepository(ConversationManager(rootRepo)), BleRobotManager(this), PreferenceManager.getDefaultSharedPreferences(this))
        )[RuhiViewModel::class.java]
        sensorRepository = SensorRepository(this) { reading ->
            viewModel.onSensorEvent(reading.ax, reading.ay, reading.az, reading.gx, reading.gy, reading.gz)
        }
        setupTouch()
        setupSpeech()
        collectViewModel()
        binding.micFab.setOnClickListener { startSpeech() }
        binding.gameFab.setOnClickListener { startActivity(Intent(this, com.indianservers.ruhi.GameActivity::class.java)) }
        binding.settingsFab.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        if (allPermissionsGranted()) startCamera() else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)
    }

    override fun onResume() { super.onResume(); sensorRepository.start() }
    override fun onPause() { saveWidgetState(); sensorRepository.stop(); super.onPause() }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { tts.language = Locale.US; tts.setPitch(1.18f); tts.setSpeechRate(0.92f) } }

    private fun setupTouch() {
        binding.contentMain.robotFaceView.setPetTouchListener { zone, gesture ->
            viewModel.onUserTouch(zone)
            if (gesture == PetTouchGesture.DOUBLE_TAP && zone == PetTouchZone.BODY) startSpeech()
        }
    }

    private fun setupSpeech() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                binding.contentMain.userBubble.text = text
                binding.contentMain.userBubble.visibility = View.VISIBLE
                viewModel.onSpeechResult(text)
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit; override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = binding.contentMain.robotFaceView.setAudioAmplitude(rmsdB / 10f)
            override fun onBufferReceived(buffer: ByteArray?) = Unit; override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) = Unit; override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun collectViewModel() {
        lifecycleScope.launch { viewModel.emotionState.collect { expression -> currentExpression = expression; binding.contentMain.robotFaceView.setExpression(expression); if (expression.isMajorWidgetEmotion()) updateWidget() } }
        lifecycleScope.launch { viewModel.faceData.collect { it?.let { face -> binding.contentMain.robotFaceView.setEyeOffset(face.eyeOffsetX, face.eyeOffsetY) } } }
        lifecycleScope.launch {
            viewModel.settings.collect {
                binding.contentMain.robotFaceView.eyeColorOverride = it.eyeColor
                binding.contentMain.robotFaceView.faceBorderStyle = it.faceBorderStyle
                binding.contentMain.robotFaceView.particleEffectsEnabled = it.particleEffects
                tts.setPitch(it.voicePitch); tts.setSpeechRate(it.voiceSpeed)
                tts.language = when (it.voiceLocale) {
                    "en_GB" -> Locale.UK
                    "hi_IN" -> Locale.Builder().setLanguage("hi").setRegion("IN").build()
                    else -> Locale.US
                }
            }
        }
        lifecycleScope.launch {
            viewModel.ttsText.collect {
                binding.contentMain.ruhiBubble.text = it
                binding.contentMain.ruhiBubble.visibility = View.VISIBLE
                tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, "ruhi")
            }
        }
        lifecycleScope.launch {
            viewModel.moodState.collect {
                binding.contentMain.moodLabel.text = when {
                    it.happiness > 0.7f -> "Feeling Happy"
                    it.energy < 0.3f -> "Feeling Sleepy"
                    it.curiosity > 0.65f -> "Feeling Curious"
                    else -> "Feeling Calm"
                }
            }
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.contentMain.previewView.surfaceProvider) }
            val analyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analyzer.setAnalyzer(cameraExecutor, CameraRepository({ viewModel.onFaceDetected(it) }, { viewModel.onFaceDetected(it) }))
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer.startListening(intent)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun saveWidgetState() {
        getSharedPreferences("ruhi_widget_state", MODE_PRIVATE).edit()
            .putString("expression", currentExpression.name)
            .putString("mood_label", binding.contentMain.moodLabel.text.toString())
            .putLong("last_seen", System.currentTimeMillis())
            .apply()
    }

    private fun updateWidget() {
        saveWidgetState()
        val intent = Intent(this, RuhiWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, AppWidgetManager.getInstance(this@MainActivity).getAppWidgetIds(ComponentName(this@MainActivity, RuhiWidget::class.java)))
        }
        sendBroadcast(intent)
    }

    private fun com.indianservers.ruhi.RobotFaceView.Expression.isMajorWidgetEmotion(): Boolean {
        return this in setOf(com.indianservers.ruhi.RobotFaceView.Expression.HAPPY, com.indianservers.ruhi.RobotFaceView.Expression.SAD, com.indianservers.ruhi.RobotFaceView.Expression.ANGRY, com.indianservers.ruhi.RobotFaceView.Expression.SLEEP, com.indianservers.ruhi.RobotFaceView.Expression.LOVE)
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.shutdown()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
