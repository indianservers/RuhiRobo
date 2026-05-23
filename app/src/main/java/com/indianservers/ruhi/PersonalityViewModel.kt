package com.indianservers.ruhi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PersonalityViewModel(
    private val moodDao: MoodDao
) : ViewModel() {
    private val _moodState = MutableStateFlow(MoodState())
    val moodState: StateFlow<MoodState> = _moodState

    private var lastInteractionAt = System.currentTimeMillis()
    private var persistenceJob: Job? = null
    private var idleJob: Job? = null

    init {
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { moodDao.latest() }
            if (latest != null) {
                _moodState.value = MoodState(
                    happiness = latest.happiness,
                    energy = latest.energy,
                    curiosity = latest.curiosity
                ).clamped()
            }
            startPersistence()
            startIdleWatcher()
        }
    }

    fun onUserInteraction(emotion: String) {
        lastInteractionAt = System.currentTimeMillis()
        val normalized = emotion.lowercase()
        _moodState.value = when (normalized) {
            "happy" -> _moodState.value.copy(
                happiness = _moodState.value.happiness + 0.1f,
                energy = _moodState.value.energy + 0.05f
            )
            "sad" -> _moodState.value.copy(
                happiness = _moodState.value.happiness - 0.08f
            )
            else -> _moodState.value.copy(
                curiosity = _moodState.value.curiosity + 0.03f
            )
        }.clamped()
    }

    fun applyContagion(userMoodScore: Float): MoodState {
        val target = userMoodScore.coerceIn(0f, 1f)
        val current = _moodState.value
        val updated = current.copy(
            happiness = current.happiness + (target - current.happiness) * 0.05f
        ).clamped()
        _moodState.value = updated
        return updated
    }

    private fun startPersistence() {
        persistenceJob?.cancel()
        persistenceJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                persistSnapshot()
            }
        }
    }

    private fun startIdleWatcher() {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                if (System.currentTimeMillis() - lastInteractionAt >= 5 * 60_000L) {
                    _moodState.value = _moodState.value.copy(
                        energy = _moodState.value.energy - 0.05f,
                        curiosity = _moodState.value.curiosity + 0.1f
                    ).clamped()
                    lastInteractionAt = System.currentTimeMillis()
                }
            }
        }
    }

    private fun persistSnapshot() {
        val snapshot = _moodState.value
        viewModelScope.launch(Dispatchers.IO) {
            moodDao.insert(
                MoodSnapshot(
                    timestamp = System.currentTimeMillis(),
                    happiness = snapshot.happiness,
                    energy = snapshot.energy,
                    curiosity = snapshot.curiosity
                )
            )
        }
    }

    override fun onCleared() {
        persistSnapshot()
        super.onCleared()
    }

    private fun MoodState.clamped(): MoodState {
        return copy(
            happiness = happiness.coerceIn(0f, 1f),
            energy = energy.coerceIn(0f, 1f),
            curiosity = curiosity.coerceIn(0f, 1f)
        )
    }

    class Factory(private val moodDao: MoodDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonalityViewModel::class.java)) {
                return PersonalityViewModel(moodDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
