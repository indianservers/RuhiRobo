package com.indianservers.ruhi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RuhiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConversationRepository(application)
    private val conversationManager = ConversationManager(repository)
    private val personalityEvolution = PersonalityEvolution(repository)

    private val _emotionBlend = MutableStateFlow(EmotionalBlend(RobotFaceView.Expression.NEUTRAL))
    val emotionBlend: StateFlow<EmotionalBlend> = _emotionBlend

    private val _moodState = MutableStateFlow(MoodState())
    val moodState: StateFlow<MoodState> = _moodState

    private val _faceData = MutableStateFlow<FaceData?>(null)
    val faceData: StateFlow<FaceData?> = _faceData

    private val _speechResult = MutableSharedFlow<String>()
    val speechResult: SharedFlow<String> = _speechResult

    private val _ttsOutput = MutableSharedFlow<String>()
    val ttsOutput: SharedFlow<String> = _ttsOutput

    private val _proactiveMessage = MutableSharedFlow<String>()
    val proactiveMessage: SharedFlow<String> = _proactiveMessage

    private val _statusText = MutableLiveData("Feeling Curious")
    val statusText: LiveData<String> = _statusText

    fun onFaceDetected(face: FaceData) {
        _faceData.value = face
        val happy = face.smilingProbability.coerceIn(0f, 1f)
        _moodState.value = _moodState.value.copy(happiness = happy)
        _emotionBlend.value = if (happy > 0.75f) {
            EmotionalBlend(RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.CURIOUS, 0.25f)
        } else {
            EmotionalBlend(RobotFaceView.Expression.CURIOUS)
        }
    }

    fun onSpeechResult(text: String) {
        viewModelScope.launch {
            _speechResult.emit(text)
            val response = conversationManager.reply(text, _moodState.value, _emotionBlend.value)
            personalityEvolution.evolveForConversation(text)
            _ttsOutput.emit(response)
        }
    }

    fun onSensorData(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        val impact = kotlin.math.abs(ax) + kotlin.math.abs(ay) + kotlin.math.abs(az) > 32f
        if (impact) {
            _emotionBlend.value = EmotionalBlend(RobotFaceView.Expression.SHOCK)
        }
    }

    fun onTouchZone(zone: PetTouchZone) {
        viewModelScope.launch {
            personalityEvolution.evolveForTouch()
        }
        _emotionBlend.value = when (zone) {
            PetTouchZone.FOREHEAD -> EmotionalBlend(RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.LOVE, 0.3f)
            PetTouchZone.NOSE -> EmotionalBlend(RobotFaceView.Expression.SURPRISED)
            PetTouchZone.LEFT_CHEEK, PetTouchZone.RIGHT_CHEEK -> EmotionalBlend(RobotFaceView.Expression.SHY)
            PetTouchZone.CHIN -> EmotionalBlend(RobotFaceView.Expression.RELIEVED, RobotFaceView.Expression.LOVE, 0.35f)
            PetTouchZone.BODY -> EmotionalBlend(RobotFaceView.Expression.CURIOUS)
        }
    }

    fun onGestureDetected(gesture: GestureType) {
        _emotionBlend.value = when (gesture) {
            GestureType.WAVE -> EmotionalBlend(RobotFaceView.Expression.HAPPY)
            GestureType.THUMBS_UP -> EmotionalBlend(RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.STARS, 0.7f)
            GestureType.THUMBS_DOWN -> EmotionalBlend(RobotFaceView.Expression.SAD)
            GestureType.OPEN_PALM -> EmotionalBlend(RobotFaceView.Expression.GRIN)
            GestureType.POINTING -> EmotionalBlend(RobotFaceView.Expression.CURIOUS)
            GestureType.FIST_RAISED -> EmotionalBlend(RobotFaceView.Expression.COOL)
            GestureType.HEART_HANDS -> EmotionalBlend(RobotFaceView.Expression.LOVE)
        }
    }

    fun emitProactive(message: String) {
        viewModelScope.launch {
            _proactiveMessage.emit(message)
        }
    }
}
