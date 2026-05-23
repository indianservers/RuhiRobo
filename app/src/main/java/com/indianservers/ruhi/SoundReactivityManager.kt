package com.indianservers.ruhi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sqrt

class SoundReactivityManager(private val context: Context) {
    @Volatile
    var currentAmplitude: Float = 0f
        private set

    @Volatile
    var isBeatDetected: Boolean = false
        private set

    @Volatile
    var bpm: Float = 0f
        private set

    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private var running = false
    private var lastAmplitude = 0f
    private val beatTimestamps = CopyOnWriteArrayList<Long>()

    fun start(onSample: (amplitude: Float, beat: Boolean, bpm: Float) -> Unit) {
        if (running) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        running = true
        worker = Thread({
            runAudioLoop(onSample)
        }, "RuhiSoundReactivity").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        audioRecord?.runCatching {
            stop()
            release()
        }
        audioRecord = null
        currentAmplitude = 0f
        isBeatDetected = false
    }

    @SuppressLint("MissingPermission")
    private fun runAudioLoop(onSample: (amplitude: Float, beat: Boolean, bpm: Float) -> Unit) {
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            running = false
            return
        }

        val bufferSize = minBuffer.coerceAtLeast(sampleRate / 10)
        val buffer = ShortArray(bufferSize)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        audioRecord = recorder

        runCatching { recorder.startRecording() }.onFailure {
            running = false
            recorder.release()
            return
        }

        while (running && !Thread.currentThread().isInterrupted) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) {
                val amplitude = normalizeAmplitude(buffer, read)
                currentAmplitude = amplitude
                val beat = amplitude > 0.6f && lastAmplitude < 0.2f
                isBeatDetected = beat
                if (beat) {
                    recordBeat(System.currentTimeMillis())
                }
                lastAmplitude = amplitude
                onSample(amplitude, beat, bpm)
            }
            runCatching { Thread.sleep(100L) }
        }
    }

    private fun normalizeAmplitude(buffer: ShortArray, read: Int): Float {
        var sum = 0.0
        for (index in 0 until read) {
            val normalized = buffer[index] / Short.MAX_VALUE.toDouble()
            sum += normalized * normalized
        }
        return sqrt(sum / read).toFloat().coerceIn(0f, 1f)
    }

    private fun recordBeat(now: Long) {
        beatTimestamps.add(now)
        val cutoff = now - 8_000L
        beatTimestamps.removeAll { it < cutoff }
        if (beatTimestamps.size < 2) return

        val intervals = beatTimestamps.zipWithNext { first, second -> second - first }
            .filter { it > 0L }
        if (intervals.isEmpty()) return

        val averageInterval = intervals.average()
        val estimate = (60_000.0 / averageInterval).toFloat()
        bpm = if (estimate in 60f..180f) estimate else 0f
    }
}
