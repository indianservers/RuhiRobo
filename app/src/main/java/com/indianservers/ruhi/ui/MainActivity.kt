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
import com.indianservers.ruhi.AmbientSoundEngine
import com.indianservers.ruhi.ARModeManager
import com.indianservers.ruhi.AutonomousWillEngine
import com.indianservers.ruhi.BehaviorPredictor
import com.indianservers.ruhi.CausalLearningEngine
import com.indianservers.ruhi.CuriosityEngine
import com.indianservers.ruhi.DrawingEngine
import com.indianservers.ruhi.EmotionToMotionMap
import com.indianservers.ruhi.GoalAction
import com.indianservers.ruhi.HapticEngine
import com.indianservers.ruhi.HapticPattern
import com.indianservers.ruhi.HypothesisEngine
import com.indianservers.ruhi.MicroExpressionEngine
import com.indianservers.ruhi.NeedAction
import com.indianservers.ruhi.NeedsEngine
import com.indianservers.ruhi.PhysicalInstinctEngine
import com.indianservers.ruhi.PoemGenerator
import com.indianservers.ruhi.PetTouchGesture
import com.indianservers.ruhi.PetTouchZone
import com.indianservers.ruhi.R
import com.indianservers.ruhi.RelationshipEngine
import com.indianservers.ruhi.ReactiveSound
import com.indianservers.ruhi.RuhiDatabase
import com.indianservers.ruhi.RuhiInnerMonologue
import com.indianservers.ruhi.RuhiWidget
import com.indianservers.ruhi.SettingsActivity
import com.indianservers.ruhi.SelfModelEngine
import com.indianservers.ruhi.SongEngine
import com.indianservers.ruhi.SpatialMapper
import com.indianservers.ruhi.VoiceManager
import com.indianservers.ruhi.VoicePersonalityEngine
import com.indianservers.ruhi.databinding.ActivityMainBinding
import com.indianservers.ruhi.hardware.BleRobotManager
import com.indianservers.ruhi.hardware.RobotHardwareController
import com.indianservers.ruhi.repository.CameraRepository
import com.indianservers.ruhi.repository.ConversationRepository
import com.indianservers.ruhi.repository.SensorRepository
import com.indianservers.ruhi.viewmodel.RuhiViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: RuhiViewModel
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var sensorRepository: SensorRepository
    private lateinit var database: RuhiDatabase
    private lateinit var rootRepository: com.indianservers.ruhi.ConversationRepository
    private lateinit var needsEngine: NeedsEngine
    private lateinit var relationshipEngine: RelationshipEngine
    private lateinit var hapticEngine: HapticEngine
    private lateinit var ambientSoundEngine: AmbientSoundEngine
    private lateinit var microExpressionEngine: MicroExpressionEngine
    private lateinit var autonomousWillEngine: AutonomousWillEngine
    private lateinit var innerMonologue: RuhiInnerMonologue
    private lateinit var curiosityEngine: CuriosityEngine
    private lateinit var hypothesisEngine: HypothesisEngine
    private lateinit var causalLearningEngine: CausalLearningEngine
    private lateinit var spatialMapper: SpatialMapper
    private lateinit var behaviorPredictor: BehaviorPredictor
    private lateinit var voicePersonalityEngine: VoicePersonalityEngine
    private lateinit var voiceManager: VoiceManager
    private lateinit var drawingEngine: DrawingEngine
    private lateinit var poemGenerator: PoemGenerator
    private lateinit var songEngine: SongEngine
    private lateinit var selfModelEngine: SelfModelEngine
    private lateinit var emotionToMotionMap: EmotionToMotionMap
    private lateinit var physicalInstinctEngine: PhysicalInstinctEngine
    private lateinit var arModeManager: ARModeManager
    private var currentExpression = com.indianservers.ruhi.RobotFaceView.Expression.NEUTRAL
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tts = TextToSpeech(this, this)
        database = RuhiDatabase.getInstance(this)
        rootRepository = com.indianservers.ruhi.ConversationRepository(this)
        val bleRobotManager = BleRobotManager(this)
        val hardwareController = RobotHardwareController(bleRobotManager)
        needsEngine = NeedsEngine(this, database, hardwareController)
        relationshipEngine = RelationshipEngine(database)
        curiosityEngine = CuriosityEngine(database, hardwareController)
        hypothesisEngine = HypothesisEngine(database)
        causalLearningEngine = CausalLearningEngine(database)
        spatialMapper = SpatialMapper(database)
        behaviorPredictor = BehaviorPredictor(database)
        voicePersonalityEngine = VoicePersonalityEngine()
        voiceManager = VoiceManager(this, database)
        drawingEngine = DrawingEngine(this, database)
        poemGenerator = PoemGenerator(database)
        songEngine = SongEngine()
        selfModelEngine = SelfModelEngine(database)
        emotionToMotionMap = EmotionToMotionMap(hardwareController)
        physicalInstinctEngine = PhysicalInstinctEngine(hardwareController)
        arModeManager = ARModeManager(this)
        hapticEngine = HapticEngine(this)
        ambientSoundEngine = AmbientSoundEngine(this)
        microExpressionEngine = MicroExpressionEngine(
            needsProvider = { needsEngine.needs.value },
            expressionProvider = { binding.contentMain.robotFaceView.getCurrentExpression() }
        )
        autonomousWillEngine = AutonomousWillEngine(
            needsProvider = { needsEngine.needs.value },
            memoryProvider = { rootRepository.topMemories() },
            bondProvider = { relationshipEngine.currentBond() }
        )
        innerMonologue = RuhiInnerMonologue(database, { needsEngine.needs.value }, { rootRepository.topMemories() })
        viewModel = ViewModelProvider(
            this,
            RuhiViewModel.Factory(ConversationRepository(ConversationManager(rootRepository)), bleRobotManager, PreferenceManager.getDefaultSharedPreferences(this))
        )[RuhiViewModel::class.java]
        sensorRepository = SensorRepository(this) { reading ->
            viewModel.onSensorEvent(reading.ax, reading.ay, reading.az, reading.gx, reading.gy, reading.gz)
            val shaken = abs(reading.ax) + abs(reading.ay) + abs(reading.az) > 32f || abs(reading.gx) + abs(reading.gy) + abs(reading.gz) > 8f
            binding.contentMain.robotFaceView.applyPhysicsTilt(reading.ax / 10f, reading.ay / 10f, faceDown = reading.az < -8f, shaken = shaken)
            if (reading.ay < -12f) binding.contentMain.robotFaceView.jumpFromTilt()
            if (shaken) {
                needsEngine.registerSafetyThreat()
                hapticEngine.play(HapticPattern.STARTLED)
            }
            arModeManager.stabilizeFromGyro(reading.gx, reading.gy)
        }
        setupTouch()
        setupSpeech()
        collectViewModel()
        collectLivingSystems()
        collectMindSystems(bleRobotManager)
        startLivingSystems()
        binding.micFab.setOnClickListener { startSpeech() }
        binding.gameFab.setOnClickListener { startActivity(Intent(this, com.indianservers.ruhi.GameHub::class.java)) }
        binding.settingsFab.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        if (allPermissionsGranted()) startCamera() else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)
    }

    override fun onResume() {
        super.onResume()
        sensorRepository.start()
        lifecycleScope.launch {
            relationshipEngine.recordAppOpenAndReturnMessage()?.let { reunionLine ->
                binding.contentMain.robotFaceView.setExpression(com.indianservers.ruhi.RobotFaceView.Expression.SHOCK)
                binding.contentMain.robotFaceView.postDelayed({ binding.contentMain.robotFaceView.setExpression(com.indianservers.ruhi.RobotFaceView.Expression.LOVE) }, 600)
                speakAsRuhi(reunionLine, com.indianservers.ruhi.RobotFaceView.Expression.CRYING)
            }
        }
    }

    override fun onPause() {
        saveWidgetState()
        sensorRepository.stop()
        needsEngine.stop()
        super.onPause()
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { tts.language = Locale.US; tts.setPitch(1.18f); tts.setSpeechRate(0.92f) } }

    private fun setupTouch() {
        binding.contentMain.robotFaceView.setPetTouchListener { zone, gesture ->
            viewModel.onUserTouch(zone)
            needsEngine.markInteraction(NeedsEngine.InteractionKind.PETTING)
            lifecycleScope.launch { relationshipEngine.recordPetting() }
            if (zone == PetTouchZone.FOREHEAD || gesture == PetTouchGesture.PETTING) hapticEngine.play(HapticPattern.PURR)
            if (zone == PetTouchZone.NOSE) hapticEngine.play(HapticPattern.STARTLED)
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
                needsEngine.markInteraction(NeedsEngine.InteractionKind.CONVERSATION)
                lifecycleScope.launch {
                    relationshipEngine.recordConversation()
                    relationshipEngine.maybeStoreInsideJoke(text, "speech recognition")
                    behaviorPredictor.record("conversation", text.take(80))
                    behaviorPredictor.learnPhrase(text)
                    hypothesisEngine.recordInteractionPattern("appear", "speech")
                    causalLearningEngine.observe("user_said_${text.take(24)}", "ruhi_answered")
                    curiosityEngine.absorbConversation(text)?.let { target ->
                        viewModel.updateMindState { it.copy(curiosityTarget = target.topic) }
                    }
                    behaviorPredictor.anticipatePhrase(text, relationshipEngine.currentBond())?.let { phrase ->
                        speakAsRuhi("Oh, you mean ${phrase.completion}? See? I know you.", com.indianservers.ruhi.RobotFaceView.Expression.LOVE)
                        selfModelEngine.recordPrediction(true)
                    }
                }
                if (text.contains("rest now", ignoreCase = true)) needsEngine.satisfyEnergy()
                if (listOf("good", "love", "kind", "thanks", "sweet").any { text.contains(it, ignoreCase = true) }) needsEngine.registerPositiveWords()
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
        lifecycleScope.launch { viewModel.emotionState.collect { expression -> currentExpression = expression; binding.contentMain.robotFaceView.setExpression(expression); hapticEngine.forExpression(expression); voiceManager.soundFor(expression)?.let { ambientSoundEngine.play(ReactiveSound.CURIOUS_BEEP) }; launch { emotionToMotionMap.express(expression) }; if (expression == com.indianservers.ruhi.RobotFaceView.Expression.SLEEP) binding.contentMain.robotFaceView.setDreamMode(true, needsEngine.needs.value.safety < 0.35f) else binding.contentMain.robotFaceView.setDreamMode(false); if (expression.isMajorWidgetEmotion()) updateWidget() } }
        lifecycleScope.launch { viewModel.faceData.collect { it?.let { face -> binding.contentMain.robotFaceView.setEyeOffset(face.eyeOffsetX, face.eyeOffsetY); needsEngine.markInteraction(NeedsEngine.InteractionKind.EYE_CONTACT) } } }
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
                needsEngine.markSpokenOrMoved()
                speakAsRuhi(it, currentExpression)
            }
        }
        lifecycleScope.launch {
            viewModel.moodState.collect {
                ambientSoundEngine.update(currentExpression, it.happiness)
                binding.contentMain.moodLabel.text = when {
                    it.happiness > 0.7f -> "Feeling Happy"
                    it.energy < 0.3f -> "Feeling Sleepy"
                    it.curiosity > 0.65f -> "Feeling Curious"
                    else -> "Feeling Calm"
                }
            }
        }
    }

    private fun collectLivingSystems() {
        lifecycleScope.launch {
            needsEngine.needs.collect { needs ->
                binding.contentMain.robotFaceView.setNeeds(needs)
                viewModel.updateMindState { it.copy(needs = needs) }
                if (needs.energy < 0.4f) tts.setSpeechRate(0.72f)
            }
        }
        lifecycleScope.launch {
            needsEngine.actions.collect { action ->
                when (action) {
                    is NeedAction.Speak -> speakAsRuhi(action.text, action.expression)
                    is NeedAction.Express -> binding.contentMain.robotFaceView.setExpression(action.expression)
                    is NeedAction.Notify -> Unit
                    NeedAction.DockAndRest -> binding.contentMain.robotFaceView.setExpression(com.indianservers.ruhi.RobotFaceView.Expression.SLEEP)
                    NeedAction.Explore -> lifecycleScope.launch {
                        val art = drawingEngine.createArtwork(com.indianservers.ruhi.MoodState(), needsEngine.needs.value)
                        speakAsRuhi("Here, I made this for you.", com.indianservers.ruhi.RobotFaceView.Expression.STARS)
                        causalLearningEngine.observe("stimulation_low", "created_art_${art.id}")
                    }
                    NeedAction.ScaredRecovery -> {
                        binding.contentMain.robotFaceView.triggerScaredRecovery()
                        speakAsRuhi("Did you see that? Something scared me!", com.indianservers.ruhi.RobotFaceView.Expression.NERVOUS)
                    }
                }
            }
        }
        lifecycleScope.launch { microExpressionEngine.events.collect { binding.contentMain.robotFaceView.applyMicroExpression(it) } }
        lifecycleScope.launch {
            autonomousWillEngine.actions.collect { action ->
                when (action) {
                    is GoalAction.Speak -> speakAsRuhi(action.text, action.expression)
                    is GoalAction.SetGoal -> {
                        needsEngine.markInteraction(NeedsEngine.InteractionKind.NEW_INPUT)
                        viewModel.updateMindState { it.copy(activeGoal = action.goal.type.name) }
                    }
                    GoalAction.Explore -> binding.contentMain.robotFaceView.setExpression(com.indianservers.ruhi.RobotFaceView.Expression.CURIOUS)
                    GoalAction.PlayGame -> speakAsRuhi("Want to play something with me? I've been wanting to.", com.indianservers.ruhi.RobotFaceView.Expression.GRIN)
                }
            }
        }
        lifecycleScope.launch { innerMonologue.leaks.collect { speakAsRuhi(it, com.indianservers.ruhi.RobotFaceView.Expression.SHY) } }
    }

    private fun collectMindSystems(bleRobotManager: BleRobotManager) {
        lifecycleScope.launch {
            bleRobotManager.sensorState.collect { state ->
                spatialMapper.updateFromSensors(state)
                physicalInstinctEngine.reflex(state, commandedMovement = false)?.let { expression ->
                    binding.contentMain.robotFaceView.setExpression(expression)
                    needsEngine.registerSafetyThreat()
                }
                viewModel.updateMindState { it.copy(spatialRoom = spatialMapper.state().roomName) }
            }
        }
        lifecycleScope.launch {
            val bond = relationshipEngine.currentBond()
            val stage = relationshipEngine.stage().name
            viewModel.updateMindState { it.copy(bondLevel = bond.level, bondStage = stage, selfAssessment = selfModelEngine.describeSelf().description) }
            behaviorPredictor.predictNow()?.let { prediction ->
                viewModel.updateMindState { it.copy(prediction = prediction.text) }
                if (prediction.confidence > 0.65f) speakAsRuhi(prediction.text, com.indianservers.ruhi.RobotFaceView.Expression.CURIOUS)
            }
            hypothesisEngine.nextTheory()?.let { hypothesis ->
                speakAsRuhi("I have a theory: ${hypothesis.theory}", com.indianservers.ruhi.RobotFaceView.Expression.THINKING)
            }
        }
    }

    private fun startLivingSystems() {
        needsEngine.start()
        ambientSoundEngine.start(lifecycleScope)
        microExpressionEngine.start(lifecycleScope)
        autonomousWillEngine.start(lifecycleScope)
        innerMonologue.start(lifecycleScope)
    }

    private fun speakAsRuhi(text: String, expression: com.indianservers.ruhi.RobotFaceView.Expression) {
        currentExpression = expression
        binding.contentMain.robotFaceView.setExpression(expression)
        binding.contentMain.ruhiBubble.text = text
        binding.contentMain.ruhiBubble.visibility = View.VISIBLE
        if (selfModelEngine.maybeMakeMistake() && needsEngine.needs.value.safety > 0.5f) {
            binding.contentMain.robotFaceView.setExpression(com.indianservers.ruhi.RobotFaceView.Expression.SHY)
            voicePersonalityEngine.speak(tts, "Oh no, I think I almost said that wrong. $text", com.indianservers.ruhi.RobotFaceView.Expression.SHY, needsEngine.needs.value)
        } else {
            voicePersonalityEngine.speak(tts, text, expression, needsEngine.needs.value)
        }
        needsEngine.markSpokenOrMoved()
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
        ambientSoundEngine.stop()
        hapticEngine.cancel()
        microExpressionEngine.stop()
        autonomousWillEngine.stop()
        innerMonologue.stop()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
