package com.indianservers.ruhi

import com.indianservers.ruhi.hardware.RobotHardwareController
import com.indianservers.ruhi.hardware.RobotSensorState
import com.indianservers.ruhi.hardware.RobotTouchZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

data class RoomPose(val xCm: Float = 0f, val yCm: Float = 0f, val headingDeg: Float = 0f)

class SensorFusionEngine(
    private val hardware: RobotHardwareController,
    private val robotSensorState: StateFlow<RobotSensorState>
) {
    private val _pose = MutableStateFlow(RoomPose())
    val pose: StateFlow<RoomPose> = _pose
    private val visitedGrid = Array(50) { BooleanArray(50) }
    var robotFollowPhoneHead: Boolean = false

    fun start(scope: CoroutineScope, onTouch: (RobotTouchZone) -> Unit, onCliff: () -> Unit) {
        scope.launch {
            robotSensorState.collectLatest { state ->
                updatePoseFromEncoders(state)
                if (state.cliffDetected) {
                    onCliff()
                    hardware.stopAll()
                    hardware.driveBackward(10f, 0.45f)
                }
                if (state.touchMask and 0x01 != 0) onTouch(RobotTouchZone.HEAD)
                if (state.touchMask and 0x02 != 0) onTouch(RobotTouchZone.CHIN)
            }
        }
    }

    suspend fun onPhoneMotion(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        if (robotFollowPhoneHead) {
            hardware.lookAt(ax.coerceIn(-1f, 1f), ay.coerceIn(-1f, 1f))
        }
        if (abs(ax) + abs(ay) + abs(az) > 32f || abs(gx) + abs(gy) + abs(gz) > 8f) {
            hardware.wiggle()
        }
    }

    private fun updatePoseFromEncoders(state: RobotSensorState) {
        val distanceCm = (state.leftEncoderTicks + state.rightEncoderTicks) / 2f * 0.05f
        val pose = _pose.value.copy(xCm = _pose.value.xCm + distanceCm)
        _pose.value = pose
        val gx = ((pose.xCm / 10f).toInt() + 25).coerceIn(0, 49)
        val gy = ((pose.yCm / 10f).toInt() + 25).coerceIn(0, 49)
        visitedGrid[gx][gy] = true
    }
}
