package com.indianservers.ruhi

import android.content.Context
import android.media.ToneGenerator
import android.media.AudioManager
import com.indianservers.ruhi.hardware.RobotSound

class SoundEffectManager(context: Context) {
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

    fun play(sound: RobotSound, volume: Float = 1f) {
        val toneType = when (sound) {
            RobotSound.PURR -> ToneGenerator.TONE_PROP_ACK
            RobotSound.BOOP -> ToneGenerator.TONE_PROP_BEEP
            RobotSound.GIGGLE -> ToneGenerator.TONE_PROP_BEEP2
            RobotSound.CURIOUS_HUM -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
            RobotSound.SAD_WHIMPER -> ToneGenerator.TONE_CDMA_ABBR_REORDER
            RobotSound.EXCITED_CHIRP -> ToneGenerator.TONE_CDMA_PIP
            RobotSound.BOOT -> ToneGenerator.TONE_PROP_PROMPT
            RobotSound.SHUTDOWN -> ToneGenerator.TONE_PROP_NACK
        }
        tone.startTone(toneType, (160 * volume.coerceIn(0.2f, 1f)).toInt())
    }

    fun release() {
        tone.release()
    }
}
