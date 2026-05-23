package com.indianservers.ruhi

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticPattern { PURR, HEARTBEAT, STARTLED, HAPPY_BUZZ, SAD_PULSE, GROWL, SNORE, NOTIFICATION_TAP }

class HapticEngine(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun play(pattern: HapticPattern, repeat: Boolean = false) {
        val (timings, amplitudes) = when (pattern) {
            HapticPattern.PURR -> longArrayOf(0, 30, 20, 30, 20, 30, 40, 30, 20, 30, 20, 30, 40, 30, 20, 30) to intArrayOf(0, 80, 0, 60, 0, 40, 0, 80, 0, 60, 0, 40, 0, 80, 0, 40)
            HapticPattern.HEARTBEAT -> longArrayOf(0, 100, 80, 100) to intArrayOf(0, 200, 0, 200)
            HapticPattern.STARTLED -> longArrayOf(0, 5, 10, 5) to intArrayOf(0, 255, 0, 200)
            HapticPattern.HAPPY_BUZZ -> longArrayOf(0, 20, 10, 20, 10) to intArrayOf(0, 120, 0, 150, 0)
            HapticPattern.SAD_PULSE -> longArrayOf(0, 500, 300) to intArrayOf(0, 60, 0)
            HapticPattern.GROWL -> longArrayOf(0, 40, 20, 40, 20, 40) to intArrayOf(0, 200, 0, 180, 0, 160)
            HapticPattern.SNORE -> longArrayOf(0, 300, 500, 300) to intArrayOf(0, 30, 0, 40)
            HapticPattern.NOTIFICATION_TAP -> longArrayOf(0, 35, 45, 70, 45, 35) to intArrayOf(0, 180, 0, 120, 0, 180)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, if (repeat) 1 else -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(timings, if (repeat) 1 else -1)
        }
    }

    fun forExpression(expression: RobotFaceView.Expression) {
        when (expression) {
            RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.GRIN -> play(HapticPattern.HAPPY_BUZZ)
            RobotFaceView.Expression.LOVE, RobotFaceView.Expression.HEARTS -> play(HapticPattern.HEARTBEAT)
            RobotFaceView.Expression.SURPRISED, RobotFaceView.Expression.SHOCK, RobotFaceView.Expression.NERVOUS -> play(HapticPattern.STARTLED)
            RobotFaceView.Expression.SAD, RobotFaceView.Expression.CRYING -> play(HapticPattern.SAD_PULSE)
            RobotFaceView.Expression.ANGRY, RobotFaceView.Expression.EVIL -> play(HapticPattern.GROWL)
            RobotFaceView.Expression.SLEEP -> play(HapticPattern.SNORE)
            else -> Unit
        }
    }

    fun cancel() {
        vibrator?.cancel()
    }
}
