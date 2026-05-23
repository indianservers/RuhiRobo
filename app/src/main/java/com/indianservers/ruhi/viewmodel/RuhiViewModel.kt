package com.indianservers.ruhi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.SharedPreferences
import android.graphics.Color
import com.google.mlkit.vision.face.Face
import com.indianservers.ruhi.PetTouchZone
import com.indianservers.ruhi.RobotFaceView
import com.indianservers.ruhi.RuhiMindContext
import com.indianservers.ruhi.RuhiMindState
import com.indianservers.ruhi.hardware.BleRobotManager
import com.indianservers.ruhi.model.FaceData
import com.indianservers.ruhi.model.MoodState
import com.indianservers.ruhi.model.TouchZone
import com.indianservers.ruhi.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class RuhiViewModel(
    private val conversationRepository: ConversationRepository,
    private val bleRobotManager: BleRobotManager,
    private val sharedPreferences: SharedPreferences
) : ViewModel(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val _emotionState = MutableStateFlow(RobotFaceView.Expression.NEUTRAL)
    val emotionState: StateFlow<RobotFaceView.Expression> = _emotionState

    private val _moodState = MutableStateFlow(MoodState())
    val moodState: StateFlow<MoodState> = _moodState

    private val _faceData = MutableStateFlow<FaceData?>(null)
    val faceData: StateFlow<FaceData?> = _faceData

    private val _speechResult = MutableSharedFlow<String>()
    val speechResult: SharedFlow<String> = _speechResult

    private val _ttsText = MutableSharedFlow<String>()
    val ttsText: SharedFlow<String> = _ttsText

    private val _settings = MutableStateFlow(RuhiSettings.from(sharedPreferences))
    val settings: StateFlow<RuhiSettings> = _settings

    val mindState: StateFlow<RuhiMindState> = RuhiMindContext.state

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        applyPersonalityPreset(sharedPreferences.getString("personality_preset", "cheerful").orEmpty())
        applyBleSettings(_settings.value)
        viewModelScope.launch {
            emotionState.collectLatest { expression ->
                bleRobotManager.sendEmotion(expression)
                bleRobotManager.sendLedColor(colorFor(expression))
            }
        }
    }

    fun onFaceDetected(face: Face) {
        val data = FaceData(
            smilingProbability = face.smilingProbability ?: -1f,
            leftEyeOpenProbability = face.leftEyeOpenProbability ?: -1f,
            rightEyeOpenProbability = face.rightEyeOpenProbability ?: -1f,
            headTiltZ = face.headEulerAngleZ
        )
        onFaceDetected(data)
    }

    fun onFaceDetected(face: FaceData) {
        _faceData.value = face
        bleRobotManager.sendHeadPosition(face.eyeOffsetX, face.eyeOffsetY)
        val happiness = face.smilingProbability.coerceIn(0f, 1f)
        if (happiness >= 0f) {
            _moodState.value = _moodState.value.copy(happiness = happiness).clamped()
        }
        _emotionState.value = when {
            happiness > 0.75f -> RobotFaceView.Expression.HAPPY
            face.leftEyeOpenProbability in 0f..0.15f && face.rightEyeOpenProbability in 0f..0.15f -> RobotFaceView.Expression.SLEEP
            abs(face.headTiltZ) > 18f -> RobotFaceView.Expression.CURIOUS
            else -> RobotFaceView.Expression.NEUTRAL
        }
    }

    fun onSpeechResult(text: String) {
        viewModelScope.launch {
            _speechResult.emit(text)
            val response = conversationRepository.reply(text, _moodState.value, _emotionState.value)
            _ttsText.emit(response)
        }
    }

    fun onSensorEvent(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        if (abs(ax) + abs(ay) + abs(az) > 32f || abs(gz) > 5f) {
            _emotionState.value = RobotFaceView.Expression.SHOCK
        }
    }

    fun updateMindState(transform: (RuhiMindState) -> RuhiMindState) {
        RuhiMindContext.update(transform)
    }

    fun onUserTouch(zone: TouchZone) {
        val delta = 0.02f * _settings.value.emotionSensitivity
        _emotionState.value = when (zone) {
            TouchZone.HEAD_TOP -> RobotFaceView.Expression.HAPPY
            TouchZone.NOSE -> RobotFaceView.Expression.SURPRISED
            TouchZone.LEFT_CHEEK, TouchZone.RIGHT_CHEEK -> RobotFaceView.Expression.SHY
            TouchZone.CHIN -> RobotFaceView.Expression.LOVE
            TouchZone.BODY -> RobotFaceView.Expression.CURIOUS
        }
        _moodState.value = _moodState.value.copy(curiosity = _moodState.value.curiosity + delta).clamped()
    }

    fun onUserTouch(zone: PetTouchZone) {
        onUserTouch(
            when (zone) {
                PetTouchZone.FOREHEAD -> TouchZone.HEAD_TOP
                PetTouchZone.NOSE -> TouchZone.NOSE
                PetTouchZone.LEFT_CHEEK -> TouchZone.LEFT_CHEEK
                PetTouchZone.RIGHT_CHEEK -> TouchZone.RIGHT_CHEEK
                PetTouchZone.CHIN -> TouchZone.CHIN
                PetTouchZone.BODY -> TouchZone.BODY
            }
        )
    }

    private fun colorFor(expression: RobotFaceView.Expression): Int {
        _settings.value.eyeColor?.let { return it }
        return when (expression) {
            RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.LOVE -> 0xFFFF6B6B.toInt()
            RobotFaceView.Expression.SAD, RobotFaceView.Expression.CRYING -> 0xFF4DABF7.toInt()
            RobotFaceView.Expression.CURIOUS -> 0xFF00E5C7.toInt()
            RobotFaceView.Expression.SLEEP -> 0xFFB197FC.toInt()
            RobotFaceView.Expression.ANGRY -> 0xFFFF4444.toInt()
            else -> 0xFF00FFCC.toInt()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        _settings.value = RuhiSettings.from(sharedPreferences)
        if (key == "personality_preset") {
            applyPersonalityPreset(sharedPreferences.getString("personality_preset", "cheerful").orEmpty())
        }
        applyBleSettings(_settings.value)
    }

    private fun applyPersonalityPreset(preset: String) {
        _moodState.value = when (preset) {
            "calm" -> MoodState(happiness = 0.62f, energy = 0.42f, curiosity = 0.55f)
            "mischievous" -> MoodState(happiness = 0.72f, energy = 0.88f, curiosity = 0.9f)
            "baby" -> MoodState(happiness = 0.7f, energy = 0.58f, curiosity = 0.82f)
            else -> MoodState(happiness = 0.82f, energy = 0.78f, curiosity = 0.66f)
        }
    }

    private fun applyBleSettings(settings: RuhiSettings) {
        if (settings.bleEnabled) {
            bleRobotManager.startScan(settings.bleDeviceName)
        } else {
            bleRobotManager.disconnect()
        }
    }

    override fun onCleared() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onCleared()
    }

    class Factory(
        private val conversationRepository: ConversationRepository,
        private val bleRobotManager: BleRobotManager,
        private val sharedPreferences: SharedPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RuhiViewModel::class.java)) {
                return RuhiViewModel(conversationRepository, bleRobotManager, sharedPreferences) as T
            }
            throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
        }
    }
}

data class RuhiSettings(
    val emotionSensitivity: Float,
    val idleTimeoutSeconds: Int,
    val voicePitch: Float,
    val voiceSpeed: Float,
    val voiceLocale: String,
    val eyeColor: Int?,
    val faceBorderStyle: String,
    val particleEffects: Boolean,
    val bleEnabled: Boolean,
    val bleDeviceName: String
) {
    companion object {
        fun from(prefs: SharedPreferences): RuhiSettings {
            return RuhiSettings(
                emotionSensitivity = prefs.getInt("emotion_sensitivity", 5) / 5f,
                idleTimeoutSeconds = prefs.getInt("idle_timeout_seconds", 20),
                voicePitch = prefs.getInt("voice_pitch", 118) / 100f,
                voiceSpeed = prefs.getInt("voice_speed", 92) / 100f,
                voiceLocale = prefs.getString("voice_locale", "en_US").orEmpty(),
                eyeColor = runCatching { Color.parseColor(prefs.getString("eye_color", "#00FFCC")) }.getOrNull(),
                faceBorderStyle = prefs.getString("face_border_style", "Pulse").orEmpty(),
                particleEffects = prefs.getBoolean("particle_effects", true),
                bleEnabled = prefs.getBoolean("ble_enabled", false),
                bleDeviceName = prefs.getString("ble_device_name", "RuhiRobo").orEmpty()
            )
        }
    }
}
