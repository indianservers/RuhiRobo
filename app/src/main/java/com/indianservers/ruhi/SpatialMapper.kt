package com.indianservers.ruhi

import android.graphics.Bitmap
import android.graphics.Color
import com.indianservers.ruhi.hardware.RobotSensorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.roundToInt

data class SpatialMindState(
    val grid: Array<FloatArray> = Array(50) { FloatArray(50) },
    val homeX: Int = 25,
    val homeY: Int = 25,
    val roomName: String = "home",
    val rearranged: Boolean = false
)

class SpatialMapper(private val database: RuhiDatabase) {
    private val grid = Array(50) { FloatArray(50) }
    private var robotX = 25
    private var robotY = 25
    private var lastFingerprint: String? = null

    suspend fun updateFromSensors(state: RobotSensorState) = withContext(Dispatchers.IO) {
        val delta = ((state.leftEncoderTicks + state.rightEncoderTicks) / 80f).roundToInt()
        robotX = (robotX + delta).coerceIn(0, 49)
        robotY = robotY.coerceIn(0, 49)
        if (state.distanceMm in 1..450) {
            val obstacleX = (robotX + (state.distanceMm / 100)).coerceIn(0, 49)
            grid[obstacleX][robotY] = 1f
            database.spatialCellDao().upsert(SpatialCell(x = obstacleX, y = robotY, occupancy = 1f, label = "distance_obstacle", updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun markObject(label: String, normalizedX: Float, normalizedY: Float) = withContext(Dispatchers.IO) {
        val x = (normalizedX.coerceIn(0f, 1f) * 49).roundToInt()
        val y = (normalizedY.coerceIn(0f, 1f) * 49).roundToInt()
        grid[x][y] = 0.85f
        database.spatialCellDao().upsert(SpatialCell(x = x, y = y, occupancy = 0.85f, label = label, updatedAt = System.currentTimeMillis()))
    }

    suspend fun scanEnvironment(labels: List<String>, dominantColors: List<Int>, brightness: Float): String? = withContext(Dispatchers.IO) {
        val fingerprint = fingerprint(labels, dominantColors, brightness)
        val known = database.environmentDao().all()
        val matched = known.firstOrNull { it.roomFingerprint == fingerprint }
        lastFingerprint = fingerprint
        if (matched == null) {
            database.environmentDao().upsert(EnvironmentProfile(roomFingerprint = fingerprint, knownObjects = labels.joinToString(","), lastScanned = System.currentTimeMillis(), roomName = "room-${known.size + 1}"))
            "Are we somewhere new?"
        } else {
            database.environmentDao().upsert(matched.copy(knownObjects = labels.joinToString(","), lastScanned = System.currentTimeMillis()))
            null
        }
    }

    fun state(): SpatialMindState = SpatialMindState(grid = grid, roomName = lastFingerprint?.take(8) ?: "home")

    fun renderMiniMap(size: Int = 140): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cell = (size / 50f).coerceAtLeast(1f)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        canvas.drawColor(Color.argb(90, 0, 0, 0))
        for (x in 0 until 50) for (y in 0 until 50) {
            val occ = grid[x][y]
            if (occ > 0f) {
                paint.color = Color.argb((80 + occ * 160).toInt(), 255, 255, 255)
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
            }
        }
        paint.color = Color.CYAN
        canvas.drawCircle(robotX * cell, robotY * cell, cell * 1.8f, paint)
        return bitmap
    }

    private fun fingerprint(labels: List<String>, colors: List<Int>, brightness: Float): String {
        val raw = labels.sorted().joinToString("|") + colors.take(4).joinToString("|") + brightness.roundToInt()
        return MessageDigest.getInstance("SHA-1").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
