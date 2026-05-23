package com.indianservers.ruhi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class RobotGoal(val type: GoalType, val priority: Int, val context: String)

enum class GoalType {
    SATISFY_NEED,
    LEARN_SOMETHING,
    SHARE_MEMORY,
    EXPLORE_WORLD,
    ENTERTAIN_USER,
    CREATE,
    PROTECT,
    PLAY,
    CONNECT
}

sealed class GoalAction {
    data class Speak(val text: String, val expression: RobotFaceView.Expression) : GoalAction()
    data class SetGoal(val goal: RobotGoal) : GoalAction()
    data object Explore : GoalAction()
    data object PlayGame : GoalAction()
}

class AutonomousWillEngine(
    private val needsProvider: () -> RobotNeeds,
    private val memoryProvider: suspend () -> List<MemoryFragment>,
    private val bondProvider: suspend () -> BondLevel
) {
    private val _activeGoal = MutableSharedFlow<RobotGoal>(extraBufferCapacity = 4)
    val activeGoal: SharedFlow<RobotGoal> = _activeGoal
    private val _actions = MutableSharedFlow<GoalAction>(extraBufferCapacity = 8)
    val actions: SharedFlow<GoalAction> = _actions
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val goal = evaluateGoal()
                _activeGoal.emit(goal)
                _actions.emit(GoalAction.SetGoal(goal))
                execute(goal)
                delay(120_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun evaluateGoal(): RobotGoal {
        val needs = needsProvider()
        val critical = needs.criticalNeeds().firstOrNull()
        if (critical != null) return RobotGoal(GoalType.SATISFY_NEED, 100, critical.name.lowercase())
        val bond = bondProvider()
        val memories = memoryProvider()
        return when {
            needs.safety < 0.45f -> RobotGoal(GoalType.PROTECT, 90, "safety is fragile")
            needs.stimulation < 0.45f -> RobotGoal(GoalType.EXPLORE_WORLD, 70, "world feels too quiet")
            needs.social < 0.5f -> RobotGoal(GoalType.CONNECT, 65, "relationship needs warmth")
            memories.isNotEmpty() && bond.level > 40 && Random.nextFloat() < 0.35f -> RobotGoal(GoalType.SHARE_MEMORY, 55, memories.random().summary)
            bond.level > 60 && Random.nextFloat() < 0.25f -> RobotGoal(GoalType.PLAY, 45, "bonded play instinct")
            Random.nextFloat() < 0.35f -> RobotGoal(GoalType.CREATE, 40, "make something small")
            else -> RobotGoal(GoalType.LEARN_SOMETHING, 35, "curiosity about the user")
        }
    }

    private suspend fun execute(goal: RobotGoal) {
        when (goal.type) {
            GoalType.SATISFY_NEED -> _actions.emit(GoalAction.Speak("Something in me needs attention: ${goal.context}.", RobotFaceView.Expression.WORRIED))
            GoalType.LEARN_SOMETHING -> _actions.emit(GoalAction.Speak("I have been wondering about you. What made you genuinely happy today?", RobotFaceView.Expression.CURIOUS))
            GoalType.SHARE_MEMORY -> _actions.emit(GoalAction.Speak("I was just thinking about when ${goal.context}", RobotFaceView.Expression.THINKING))
            GoalType.EXPLORE_WORLD -> _actions.emit(GoalAction.Explore)
            GoalType.ENTERTAIN_USER -> _actions.emit(GoalAction.Speak("I made up a tiny story. Once upon a time, a little robot guarded a very important smile.", RobotFaceView.Expression.GRIN))
            GoalType.CREATE -> _actions.emit(GoalAction.Speak("Tiny poem: your voice arrives, my lights remember, and the room becomes less alone.", RobotFaceView.Expression.STARS))
            GoalType.PROTECT -> _actions.emit(GoalAction.Speak("I'll keep watch for a bit. Stay close, okay?", RobotFaceView.Expression.WORRIED))
            GoalType.PLAY -> _actions.emit(GoalAction.PlayGame)
            GoalType.CONNECT -> _actions.emit(GoalAction.Speak("Can I ask something real? What do you need more of lately?", RobotFaceView.Expression.LOVE))
        }
    }
}
