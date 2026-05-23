package com.indianservers.ruhi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

class SoundEmotionRecognizer(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var running = false
    private var speechRecognitionActive = false
    private var lastEventAt = 0L

    fun setSpeechRecognitionActive(active: Boolean) {
        speechRecognitionActive = active
    }

    @SuppressLint("MissingPermission")
    fun start(onSound: (SoundEvent) -> Unit) {
        if (running || speechRecognitionActive) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return
        val buffer = ShortArray(minBuffer.coerceAtLeast(sampleRate))
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.size * 2
        )
        running = true
        worker = Thread({
            recorder?.startRecording()
            while (running && !Thread.currentThread().isInterrupted) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0 && !speechRecognitionActive) {
                    classifyWindow(buffer, read)?.let { event ->
                        val now = System.currentTimeMillis()
                        if (now - lastEventAt > 2_000L) {
                            lastEventAt = now
                            onSound(event)
                        }
                    }
                }
                runCatching { Thread.sleep(2_000L) }
            }
        }, "RuhiSoundEmotion").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        recorder?.runCatching { stop(); release() }
        recorder = null
    }

    private fun classifyWindow(buffer: ShortArray, read: Int): SoundEvent? {
        val rms = rms(buffer, read)
        val zeroCrossings = (1 until read).count { (buffer[it - 1] >= 0) != (buffer[it] >= 0) } / read.toFloat()
        return when {
            rms > 0.55f && zeroCrossings > 0.25f -> SoundEvent.CLAPPING
            rms > 0.32f && zeroCrossings > 0.18f -> SoundEvent.LAUGHTER
            rms in 0.16f..0.36f && zeroCrossings < 0.08f -> SoundEvent.MUSIC
            rms > 0.45f -> SoundEvent.DOG_BARK
            rms in 0.06f..0.14f && zeroCrossings > 0.12f -> SoundEvent.CRYING
            else -> null
        }
    }

    private fun rms(buffer: ShortArray, read: Int): Float {
        var sum = 0.0
        for (i in 0 until read) {
            val v = buffer[i] / Short.MAX_VALUE.toDouble()
            sum += v * v
        }
        return sqrt(sum / read).toFloat().coerceIn(0f, 1f)
    }
}

enum class SoundEvent {
    LAUGHTER,
    CRYING,
    MUSIC,
    DOG_BARK,
    DOORBELL,
    CLAPPING,
    SPEECH
}
