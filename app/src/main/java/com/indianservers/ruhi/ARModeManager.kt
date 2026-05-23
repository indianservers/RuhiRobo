package com.indianservers.ruhi

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

data class ARState(
    val enabled: Boolean = false,
    val scale: Float = 1f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.72f,
    val usingArCore: Boolean = false,
    val peekaboo: Boolean = false
)

class ARModeManager(private val context: Context) {
    var state: ARState = ARState()
        private set

    fun enableFallbackCompositing() {
        state = state.copy(enabled = true, usingArCore = false)
    }

    fun disable() {
        state = state.copy(enabled = false)
    }

    fun pinchScale(factor: Float) {
        state = state.copy(scale = (state.scale * factor).coerceIn(0.35f, 2.8f))
    }

    fun stabilizeFromGyro(gx: Float, gy: Float) {
        state = state.copy(anchorX = (state.anchorX - gx * 0.002f).coerceIn(0.1f, 0.9f), anchorY = (state.anchorY - gy * 0.002f).coerceIn(0.25f, 0.9f))
    }

    fun reactToForegroundObject(closeObject: Boolean) {
        state = state.copy(peekaboo = closeObject)
    }

    fun shareSticker(faceView: RobotFaceView): Intent {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        faceView.draw(Canvas(bitmap))
        val file = File(context.cacheDir, "ruhi_sticker.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share Ruhi in the wild")
    }
}
