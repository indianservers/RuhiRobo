package com.indianservers.ruhi

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AmbientLayer { SERVO_HUM, THINKING_HUM, SLEEP_BREATH }
enum class ReactiveSound { BLINK_TICK, STEP_TICK, BOOT_SEQUENCE, SHUTDOWN, LEVEL_UP, NOTIFICATION_RECEIVE, ERROR_SOUND, KISS_SOUND, HAPPY_CHIRP, CURIOUS_BEEP, SIGH }

class AmbientSoundEngine(context: Context) {
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 24)
    private var ambientJob: Job? = null
    private var currentExpression: RobotFaceView.Expression = RobotFaceView.Expression.NEUTRAL
    private var happiness: Float = 0.5f

    fun start(scope: CoroutineScope) {
        play(ReactiveSound.BOOT_SEQUENCE)
        ambientJob?.cancel()
        ambientJob = scope.launch {
            while (isActive) {
                when (currentExpression) {
                    RobotFaceView.Expression.SLEEP -> tone.startTone(ToneGenerator.TONE_CDMA_DIAL_TONE_LITE, 120)
                    RobotFaceView.Expression.THINKING -> tone.startTone(ToneGenerator.TONE_CDMA_CALLDROP_LITE, 80)
                    else -> if (Random.nextFloat() < 0.2f) tone.startTone(ToneGenerator.TONE_PROP_ACK, 35)
                }
                if (happiness > 0.7f && Random.nextInt(30, 90) < 35) play(ReactiveSound.HAPPY_CHIRP)
                delay(4_000)
            }
        }
    }

    fun update(expression: RobotFaceView.Expression, happiness: Float) {
        currentExpression = expression
        this.happiness = happiness
    }

    fun play(sound: ReactiveSound) {
        val (toneType, duration) = when (sound) {
            ReactiveSound.BLINK_TICK -> ToneGenerator.TONE_PROP_BEEP to 20
            ReactiveSound.STEP_TICK -> ToneGenerator.TONE_PROP_ACK to 25
            ReactiveSound.BOOT_SEQUENCE -> ToneGenerator.TONE_PROP_PROMPT to 220
            ReactiveSound.SHUTDOWN -> ToneGenerator.TONE_PROP_NACK to 260
            ReactiveSound.LEVEL_UP -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD to 360
            ReactiveSound.NOTIFICATION_RECEIVE -> ToneGenerator.TONE_CDMA_ONE_MIN_BEEP to 180
            ReactiveSound.ERROR_SOUND -> ToneGenerator.TONE_SUP_ERROR to 180
            ReactiveSound.KISS_SOUND -> ToneGenerator.TONE_PROP_BEEP2 to 130
            ReactiveSound.HAPPY_CHIRP -> ToneGenerator.TONE_CDMA_PIP to 120
            ReactiveSound.CURIOUS_BEEP -> ToneGenerator.TONE_PROP_ACK to 70
            ReactiveSound.SIGH -> ToneGenerator.TONE_CDMA_ABBR_REORDER to 240
        }
        tone.startTone(toneType, duration)
    }

    fun stop() {
        play(ReactiveSound.SHUTDOWN)
        ambientJob?.cancel()
        tone.release()
    }
}
