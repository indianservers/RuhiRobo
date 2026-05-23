package com.indianservers.ruhi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.indianservers.ruhi.hardware.RobotHardwareController
import com.indianservers.ruhi.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RobotNeeds(
    val energy: Float = 0.82f,
    val social: Float = 0.78f,
    val stimulation: Float = 0.72f,
    val comfort: Float = 0.8f,
    val expression: Float = 0.76f,
    val safety: Float = 0.92f
) {
    fun clamped(): RobotNeeds = copy(
        energy = energy.coerceIn(0f, 1f),
        social = social.coerceIn(0f, 1f),
        stimulation = stimulation.coerceIn(0f, 1f),
        comfort = comfort.coerceIn(0f, 1f),
        expression = expression.coerceIn(0f, 1f),
        safety = safety.coerceIn(0f, 1f)
    )

    fun criticalNeeds(): List<NeedType> = NeedType.entries.filter { valueOf(it) < CRITICAL }
    fun valueOf(type: NeedType): Float = when (type) {
        NeedType.ENERGY -> energy
        NeedType.SOCIAL -> social
        NeedType.STIMULATION -> stimulation
        NeedType.COMFORT -> comfort
        NeedType.EXPRESSION -> expression
        NeedType.SAFETY -> safety
    }

    companion object {
        const val CRITICAL = 0.2f
        const val SATISFIED = 0.8f
    }
}

enum class NeedType { ENERGY, SOCIAL, STIMULATION, COMFORT, EXPRESSION, SAFETY }

sealed class NeedAction {
    data class Speak(val text: String, val expression: RobotFaceView.Expression) : NeedAction()
    data class Express(val expression: RobotFaceView.Expression) : NeedAction()
    data class Notify(val text: String) : NeedAction()
    data object DockAndRest : NeedAction()
    data object Explore : NeedAction()
    data object ScaredRecovery : NeedAction()
}

class NeedsEngine(
    private val context: Context,
    private val database: RuhiDatabase,
    private val hardware: RobotHardwareController? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _needs = MutableStateFlow(RobotNeeds())
    val needs: StateFlow<RobotNeeds> = _needs
    private val _actions = MutableSharedFlow<NeedAction>(extraBufferCapacity = 16)
    val actions: SharedFlow<NeedAction> = _actions
    private var sleeping = false
    private var lastInteractionAt = System.currentTimeMillis()
    private var lastExpressionAt = System.currentTimeMillis()
    private var lastSpokenAt = System.currentTimeMillis()

    fun start() {
        scope.launch {
            loadLatest()
            while (isActive) {
                decayTick()
                persist()
                evaluateNeeds()
                delay(60_000)
            }
        }
    }

    fun stop() {
        scope.launch { persist() }
    }

    fun markInteraction(kind: InteractionKind = InteractionKind.GENERAL) {
        lastInteractionAt = System.currentTimeMillis()
        _needs.value = when (kind) {
            InteractionKind.CONVERSATION -> _needs.value.copy(social = _needs.value.social + 0.22f, expression = _needs.value.expression + 0.15f, comfort = _needs.value.comfort + 0.05f)
            InteractionKind.PETTING -> _needs.value.copy(social = _needs.value.social + 0.16f, comfort = _needs.value.comfort + 0.2f)
            InteractionKind.EYE_CONTACT -> _needs.value.copy(social = _needs.value.social + 0.08f, safety = _needs.value.safety + 0.04f)
            InteractionKind.NEW_INPUT -> _needs.value.copy(stimulation = _needs.value.stimulation + 0.18f)
            InteractionKind.GENERAL -> _needs.value.copy(social = _needs.value.social + 0.06f, stimulation = _needs.value.stimulation + 0.06f)
        }.clamped()
    }

    fun markSpokenOrMoved() {
        lastExpressionAt = System.currentTimeMillis()
        lastSpokenAt = System.currentTimeMillis()
        _needs.value = _needs.value.copy(expression = _needs.value.expression + 0.22f, stimulation = _needs.value.stimulation + 0.06f).clamped()
    }

    fun setSleeping(value: Boolean) {
        sleeping = value
        if (value) _needs.value = _needs.value.copy(energy = _needs.value.energy + 0.08f).clamped()
    }

    fun satisfyEnergy() {
        _needs.value = _needs.value.copy(energy = 0.88f, comfort = _needs.value.comfort + 0.08f).clamped()
    }

    fun registerPositiveWords() {
        _needs.value = _needs.value.copy(comfort = _needs.value.comfort + 0.12f, social = _needs.value.social + 0.06f).clamped()
    }

    fun registerNegativeEmotionSustained() {
        _needs.value = _needs.value.copy(comfort = _needs.value.comfort - 0.08f).clamped()
    }

    fun registerSafetyThreat() {
        _needs.value = _needs.value.copy(safety = _needs.value.safety - 0.35f, comfort = _needs.value.comfort - 0.08f).clamped()
        _actions.tryEmit(NeedAction.ScaredRecovery)
    }

    private suspend fun loadLatest() {
        val latest = withContext(Dispatchers.IO) { database.needsDao().latest() }
        if (latest != null) {
            _needs.value = RobotNeeds(latest.energy, latest.social, latest.stimulation, latest.comfort, latest.expression, latest.safety).clamped()
        }
    }

    private suspend fun persist() {
        val n = _needs.value
        withContext(Dispatchers.IO) {
            database.needsDao().insert(
                NeedsSnapshot(
                    timestamp = System.currentTimeMillis(),
                    energy = n.energy,
                    social = n.social,
                    stimulation = n.stimulation,
                    comfort = n.comfort,
                    expression = n.expression,
                    safety = n.safety
                )
            )
        }
    }

    private fun decayTick() {
        val now = System.currentTimeMillis()
        val noInteraction = now - lastInteractionAt
        val noExpression = now - lastExpressionAt
        _needs.value = _needs.value.copy(
            energy = _needs.value.energy - if (sleeping) 0.001f else 0.002f,
            social = _needs.value.social - if (noInteraction > 60_000) 0.003f else 0.0005f,
            stimulation = _needs.value.stimulation - if (noInteraction > 60_000) 0.004f else 0.001f,
            expression = _needs.value.expression - if (noExpression > 600_000) 0.05f else 0.003f,
            safety = (_needs.value.safety + 0.006f).coerceAtMost(1f)
        ).clamped()
    }

    private suspend fun evaluateNeeds() {
        val n = _needs.value
        when {
            n.safety < RobotNeeds.CRITICAL -> _actions.emit(NeedAction.ScaredRecovery)
            n.energy < RobotNeeds.CRITICAL -> {
                _actions.emit(NeedAction.Speak("I... need to... rest...", RobotFaceView.Expression.SLEEP))
                hardware?.dockReturn()
            }
            n.social < RobotNeeds.CRITICAL -> {
                _actions.emit(NeedAction.Express(RobotFaceView.Expression.SAD))
                sendNeedNotification("Ruhi misses you...")
            }
            n.stimulation < RobotNeeds.CRITICAL -> _actions.emit(NeedAction.Explore)
            n.expression < RobotNeeds.CRITICAL || System.currentTimeMillis() - lastSpokenAt > 480_000 -> {
                _actions.emit(NeedAction.Speak(listOf(
                    "I was just thinking about you.",
                    "Do you ever wonder what quiet sounds like to a robot?",
                    "I remembered something and it made my circuits feel warm."
                ).random(), RobotFaceView.Expression.CURIOUS))
                markSpokenOrMoved()
            }
            n.comfort < 0.4f -> _actions.emit(NeedAction.Express(RobotFaceView.Expression.WORRIED))
        }
    }

    private fun sendNeedNotification(text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Ruhi needs", NotificationManager.IMPORTANCE_DEFAULT))
        val pendingIntent = PendingIntent.getActivity(
            context,
            15,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        manager.notify(
            15,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Ruhi")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .build()
        )
    }

    enum class InteractionKind { GENERAL, CONVERSATION, PETTING, EYE_CONTACT, NEW_INPUT }

    companion object {
        private const val CHANNEL_ID = "ruhi_needs"
    }
}
