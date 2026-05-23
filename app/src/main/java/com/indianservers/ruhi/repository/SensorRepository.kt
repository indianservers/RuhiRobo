package com.indianservers.ruhi.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import com.indianservers.ruhi.model.SensorReading
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sqrt

class SensorRepository(
    context: Context,
    private val onReading: (SensorReading) -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var ax = 0f
    private var ay = 0f
    private var az = 0f
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private val gravity = FloatArray(3)
    private val linear = FloatArray(3)
    private var hasGravity = false
    private var lastAccelTimestampNs = 0L
    private var neutralTiltX = 0f
    private var neutralTiltY = 0f
    private var neutralSamples = 0
    private var filteredTiltX = 0f
    private var filteredTiltY = 0f
    private var lastEmitMs = 0L

    fun start() {
        resetCalibration()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                ax = event.values[0]
                ay = event.values[1]
                az = event.values[2]
                updateGravity(event.timestamp)
            }
            Sensor.TYPE_GYROSCOPE -> {
                gx = event.values[0]
                gy = event.values[1]
                gz = event.values[2]
            }
        }
        val now = System.currentTimeMillis()
        if (now - lastEmitMs < 33L) return
        lastEmitMs = now

        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay.rotation
        val (screenGravityX, screenGravityY) = rotateToScreen(gravity[0], gravity[1], rotation)
        val rawTiltX = (screenGravityX / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val rawTiltY = (screenGravityY / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val motionG = vectorMagnitude(linear[0], linear[1], linear[2]) / SensorManager.GRAVITY_EARTH
        val gyroMagnitude = vectorMagnitude(gx, gy, gz)

        updateNeutral(rawTiltX, rawTiltY, motionG, gyroMagnitude)
        val calibratedTiltX = applyDeadZone(rawTiltX - neutralTiltX)
        val calibratedTiltY = applyDeadZone(rawTiltY - neutralTiltY)
        filteredTiltX += (calibratedTiltX - filteredTiltX) * 0.16f
        filteredTiltY += (calibratedTiltY - filteredTiltY) * 0.16f

        onReading(
            SensorReading(
                ax = ax,
                ay = ay,
                az = az,
                gx = gx,
                gy = gy,
                gz = gz,
                tiltX = filteredTiltX,
                tiltY = filteredTiltY,
                motionG = motionG,
                gyroMagnitude = gyroMagnitude,
                faceDown = gravity[2] < -7.5f,
                shaken = motionG > 1.15f || gyroMagnitude > 5.5f
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun resetCalibration() {
        hasGravity = false
        lastAccelTimestampNs = 0L
        neutralTiltX = 0f
        neutralTiltY = 0f
        neutralSamples = 0
        filteredTiltX = 0f
        filteredTiltY = 0f
        lastEmitMs = 0L
    }

    private fun updateGravity(timestampNs: Long) {
        val dtSeconds = if (lastAccelTimestampNs == 0L) 0.02f else ((timestampNs - lastAccelTimestampNs) / 1_000_000_000f).coerceIn(0.005f, 0.08f)
        lastAccelTimestampNs = timestampNs
        val alpha = exp((-dtSeconds / 0.45f).toDouble()).toFloat()
        if (!hasGravity) {
            gravity[0] = ax
            gravity[1] = ay
            gravity[2] = az
            hasGravity = true
        } else {
            gravity[0] = gravity[0] * alpha + ax * (1f - alpha)
            gravity[1] = gravity[1] * alpha + ay * (1f - alpha)
            gravity[2] = gravity[2] * alpha + az * (1f - alpha)
        }
        linear[0] = ax - gravity[0]
        linear[1] = ay - gravity[1]
        linear[2] = az - gravity[2]
    }

    private fun updateNeutral(rawTiltX: Float, rawTiltY: Float, motionG: Float, gyroMagnitude: Float) {
        if (motionG > 0.08f || gyroMagnitude > 0.25f) return
        if (neutralSamples < 24) {
            val n = neutralSamples.toFloat()
            neutralTiltX = ((neutralTiltX * n) + rawTiltX) / (n + 1f)
            neutralTiltY = ((neutralTiltY * n) + rawTiltY) / (n + 1f)
            neutralSamples++
        } else {
            neutralTiltX += (rawTiltX - neutralTiltX) * 0.004f
            neutralTiltY += (rawTiltY - neutralTiltY) * 0.004f
        }
    }

    private fun applyDeadZone(value: Float): Float {
        val deadZone = 0.075f
        if (abs(value) <= deadZone) return 0f
        val scaled = (abs(value) - deadZone) / (0.72f - deadZone)
        return (scaled.coerceIn(0f, 1f) * sign(value))
    }

    private fun rotateToScreen(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        Surface.ROTATION_90 -> -y to x
        Surface.ROTATION_180 -> -x to -y
        Surface.ROTATION_270 -> y to -x
        else -> x to y
    }

    private fun vectorMagnitude(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)
}
