package com.indianservers.ruhi

import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager {
    fun play(soundName: String): String {
        val tone = when (soundName.lowercase()) {
            "happy", "beep", "sparkle" -> ToneGenerator.TONE_PROP_ACK
            "sad", "oops" -> ToneGenerator.TONE_PROP_NACK
            "alert", "doorbell" -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        ToneGenerator(AudioManager.STREAM_MUSIC, 70).startTone(tone, 180)
        return "Played sound '$soundName'."
    }
}
