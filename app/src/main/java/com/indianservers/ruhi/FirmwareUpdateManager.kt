package com.indianservers.ruhi

import android.content.Context
import com.indianservers.ruhi.hardware.BleRobotManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File

data class FirmwareRelease(val tag: String, val downloadUrl: String)
data class FirmwareProgress(val percent: Int = 0, val status: String = "idle")

class FirmwareUpdateManager(
    private val context: Context,
    private val ble: BleRobotManager,
    private val ownerRepo: String = "indianservers/RuhiRobo"
) {
    private val client = OkHttpClient()
    private val _progress = MutableStateFlow(FirmwareProgress())
    val progress: StateFlow<FirmwareProgress> = _progress

    suspend fun checkLatestRelease(): FirmwareRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.github.com/repos/$ownerRepo/releases").build()
        val body = client.newCall(request).execute().body?.string() ?: return@withContext null
        val first = JSONArray(body).optJSONObject(0) ?: return@withContext null
        val assets = first.optJSONArray("assets") ?: return@withContext null
        val bin = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".bin") } ?: return@withContext null
        FirmwareRelease(first.optString("tag_name"), bin.optString("browser_download_url"))
    }

    suspend fun download(release: FirmwareRelease): File = withContext(Dispatchers.IO) {
        _progress.value = FirmwareProgress(0, "downloading")
        val request = Request.Builder().url(release.downloadUrl).build()
        val bytes = client.newCall(request).execute().body?.bytes() ?: ByteArray(0)
        File(context.filesDir, "ruhi-${release.tag}.bin").apply {
            writeBytes(bytes)
        }
    }

    suspend fun transfer(file: File) = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        val chunks = bytes.toList().chunked(512).map { it.toByteArray() }
        chunks.forEachIndexed { index, chunk ->
            var attempts = 0
            while (attempts < 3) {
                ble.sendOtaChunk(index, crc16(chunk), chunk)
                attempts++
                break
            }
            _progress.value = FirmwareProgress(((index + 1) * 100f / chunks.size).toInt(), "transferring")
        }
        ble.sendOtaChunk(0xFFFF, 0, byteArrayOf(0xFF.toByte()))
        _progress.value = FirmwareProgress(100, "complete")
    }

    fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        data.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}
