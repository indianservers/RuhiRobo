package com.indianservers.ruhi

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

class LipSyncEngine(
    private val faceView: RobotFaceView,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private val stopRunnable = Runnable {
        faceView.setMouthOpenRatio(0f)
        faceView.setSpeaking(false)
    }

    fun attach(tts: TextToSpeech) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                handler.post(stopRunnable)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post(stopRunnable)
            }
        })
    }

    fun speakStarted(text: String) {
        handler.removeCallbacks(stopRunnable)
        faceView.setSpeaking(true)
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        var offset = 0L
        words.forEach { word ->
            val syllables = (word.length / 2.5f).toInt().coerceAtLeast(1)
            val step = (180L / syllables).coerceAtLeast(60L)
            repeat(syllables) {
                handler.postDelayed({ faceView.setMouthOpenRatio(1f) }, offset)
                handler.postDelayed({ faceView.setMouthOpenRatio(0.45f) }, offset + step / 2)
                handler.postDelayed({ faceView.setMouthOpenRatio(0.05f) }, offset + step)
                offset += step
            }
            offset += 50L
        }
        handler.postDelayed(stopRunnable, offset + 200L)
    }
}
