package com.indianservers.ruhi

import android.graphics.Color
import com.indianservers.ruhi.hardware.RobotHardwareController
import kotlinx.coroutines.delay

class WaypointManager(
    private val waypointDao: WaypointDao,
    private val hardware: RobotHardwareController
) {
    private val recording = mutableListOf<Waypoint>()
    private var recordingRouteId: Long = 0

    suspend fun createRoute(name: String): Long {
        val id = waypointDao.insertRoute(Route(name = name, createdAt = System.currentTimeMillis(), stepCount = 0))
        recordingRouteId = id
        recording.clear()
        return id
    }

    fun recordCommand(leftSpeed: Float, rightSpeed: Float, durationMs: Int, headPan: Float, headTilt: Float, ledColor: Int) {
        if (recordingRouteId == 0L) return
        recording += Waypoint(
            routeId = recordingRouteId,
            sequence = recording.size,
            leftSpeed = leftSpeed,
            rightSpeed = rightSpeed,
            durationMs = durationMs,
            headPan = headPan,
            headTilt = headTilt,
            ledColor = ledColor
        )
    }

    suspend fun finishRecording() {
        waypointDao.insertWaypoints(recording)
        recording.clear()
        recordingRouteId = 0
    }

    suspend fun playRoute(routeId: Long) {
        waypointDao.waypointsForRoute(routeId).forEach { step ->
            hardware.setLed(step.ledColor)
            hardware.lookAt(step.headPan / 45f, step.headTilt / 30f)
            hardwareDrive(step)
            delay(step.durationMs.toLong())
        }
        hardware.stopAll()
    }

    suspend fun playRouteByName(name: String) {
        val route = waypointDao.routes().firstOrNull { it.name == name } ?: return
        playRoute(route.id)
    }

    suspend fun installPrebuiltRoutes() {
        if (waypointDao.routes().isNotEmpty()) return
        savePrebuilt("Birthday Dance", listOf(
            Waypoint(routeId = 0, sequence = 0, leftSpeed = 0.7f, rightSpeed = -0.7f, durationMs = 900, headPan = 0f, headTilt = -5f, ledColor = Color.MAGENTA),
            Waypoint(routeId = 0, sequence = 1, leftSpeed = -0.7f, rightSpeed = 0.7f, durationMs = 900, headPan = 0f, headTilt = 10f, ledColor = Color.YELLOW)
        ))
        savePrebuilt("Morning Stretch", listOf(
            Waypoint(routeId = 0, sequence = 0, leftSpeed = 0f, rightSpeed = 0f, durationMs = 600, headPan = -20f, headTilt = 18f, ledColor = Color.CYAN),
            Waypoint(routeId = 0, sequence = 1, leftSpeed = 0f, rightSpeed = 0f, durationMs = 600, headPan = 20f, headTilt = -10f, ledColor = Color.WHITE)
        ))
        savePrebuilt("Patrol Loop A", listOf(
            Waypoint(routeId = 0, sequence = 0, leftSpeed = 0.35f, rightSpeed = 0.35f, durationMs = 1_000, headPan = 0f, headTilt = 0f, ledColor = Color.GREEN),
            Waypoint(routeId = 0, sequence = 1, leftSpeed = 0.35f, rightSpeed = -0.35f, durationMs = 450, headPan = 15f, headTilt = 0f, ledColor = Color.GREEN)
        ))
    }

    private suspend fun savePrebuilt(name: String, steps: List<Waypoint>) {
        val routeId = waypointDao.insertRoute(Route(name = name, createdAt = System.currentTimeMillis(), stepCount = steps.size))
        waypointDao.insertWaypoints(steps.mapIndexed { index, waypoint -> waypoint.copy(routeId = routeId, sequence = index) })
    }

    private suspend fun hardwareDrive(step: Waypoint) {
        hardware.lookAt(step.headPan / 45f, step.headTilt / 30f)
        // Body commands are exposed through the controller's movement verbs; direct route playback
        // uses distance-like timing through forward/back/turn approximation.
        when {
            step.leftSpeed > 0 && step.rightSpeed > 0 -> hardware.driveForward(step.durationMs / 30f, step.leftSpeed)
            step.leftSpeed < 0 && step.rightSpeed < 0 -> hardware.driveBackward(step.durationMs / 30f, kotlin.math.abs(step.leftSpeed))
            step.leftSpeed < step.rightSpeed -> hardware.turnLeft(step.durationMs / 8f)
            else -> hardware.turnRight(step.durationMs / 8f)
        }
    }
}
