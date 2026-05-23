package com.indianservers.ruhi

import com.indianservers.ruhi.hardware.RobotHardwareController
import com.indianservers.ruhi.hardware.RobotSensorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AutonomyState { IDLE, EXPLORING, FOLLOWING, DOCKING, PATROLLING, SLEEPING }

class AutonomyEngine(
    private val hardware: RobotHardwareController,
    private val sensors: StateFlow<RobotSensorState>,
    private val waypointManager: WaypointManager? = null
) {
    private val _state = MutableStateFlow(AutonomyState.IDLE)
    val state: StateFlow<AutonomyState> = _state
    private var job: Job? = null
    private var lastInteractionAt = System.currentTimeMillis()
    private val visited = Array(50) { IntArray(50) }

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                tick()
                delay(1_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun onUserInteraction() {
        lastInteractionAt = System.currentTimeMillis()
        if (_state.value != AutonomyState.SLEEPING) _state.value = AutonomyState.IDLE
    }

    fun onFaceDetected(areaRatio: Float, centerX: Float, centerY: Float) {
        lastInteractionAt = System.currentTimeMillis()
        _state.value = AutonomyState.FOLLOWING
        job?.let {
            // Movement is handled on the next tick; keep this method lightweight for camera callbacks.
        }
    }

    fun patrol() {
        _state.value = AutonomyState.PATROLLING
    }

    fun dock() {
        _state.value = AutonomyState.DOCKING
    }

    private suspend fun tick() {
        val idleMs = System.currentTimeMillis() - lastInteractionAt
        if (_state.value == AutonomyState.IDLE && idleMs > 180_000) _state.value = AutonomyState.EXPLORING
        when (_state.value) {
            AutonomyState.IDLE -> hardware.stopAll()
            AutonomyState.EXPLORING -> explore()
            AutonomyState.FOLLOWING -> follow()
            AutonomyState.DOCKING -> dockStep()
            AutonomyState.PATROLLING -> waypointManager?.playRouteByName("Patrol Loop A") ?: explore()
            AutonomyState.SLEEPING -> hardware.expressEmotion(RobotFaceView.Expression.SLEEP, 5_000)
        }
    }

    private suspend fun explore() {
        val state = sensors.value
        if (state.cliffDetected || state.distanceMm in 1..199) {
            hardware.stopAll()
            hardware.driveBackward(10f, 0.4f)
            if (Random.nextBoolean()) hardware.turnLeft(60f) else hardware.turnRight(60f)
            return
        }
        visited[Random.nextInt(50)][Random.nextInt(50)] = 1
        hardware.expressEmotion(RobotFaceView.Expression.CURIOUS, 1_000)
        hardware.driveForward(Random.nextInt(12, 28).toFloat(), 0.35f)
        if (Random.nextBoolean()) hardware.turnLeft(Random.nextInt(20, 90).toFloat()) else hardware.turnRight(Random.nextInt(20, 90).toFloat())
    }

    private suspend fun follow() {
        val state = sensors.value
        if (state.cliffDetected) {
            hardware.stopAll()
            hardware.driveBackward(10f, 0.35f)
        } else if (state.distanceMm > 0 && state.distanceMm < 120) {
            hardware.driveBackward(6f, 0.25f)
        } else if (state.distanceMm > 650) {
            hardware.driveForward(8f, 0.25f)
        } else {
            hardware.stopAll()
        }
    }

    private suspend fun dockStep() {
        hardware.expressEmotion(RobotFaceView.Expression.SLEEP, 2_000)
        if (sensors.value.charging) {
            _state.value = AutonomyState.SLEEPING
        } else {
            hardware.driveForward(5f, 0.2f)
        }
    }
}
