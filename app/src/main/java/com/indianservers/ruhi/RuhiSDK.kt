package com.indianservers.ruhi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.indianservers.ruhi.ui.MainActivity

object RuhiSDK {
    const val ACTION_COMMAND = "com.indianservers.ruhi.COMMAND"
    const val ACTION_EMOTION_CHANGED = "com.indianservers.ruhi.EMOTION_CHANGED"
    const val ACTION_FACE_RECOGNIZED = "com.indianservers.ruhi.FACE_RECOGNIZED"
    const val ACTION_OBJECT_DETECTED = "com.indianservers.ruhi.OBJECT_DETECTED"
    const val ACTION_BATTERY_LOW = "com.indianservers.ruhi.BATTERY_LOW"
    const val ACTION_TOUCH_EVENT = "com.indianservers.ruhi.TOUCH_EVENT"
    const val ACTION_MOOD_RESULT = "com.indianservers.ruhi.MOOD_RESULT"

    const val COMMAND_SPEAK = "RUHI_SPEAK"
    const val COMMAND_SET_EMOTION = "RUHI_SET_EMOTION"
    const val COMMAND_DRIVE = "RUHI_DRIVE"
    const val COMMAND_QUERY = "RUHI_QUERY"
    const val COMMAND_REGISTER_FACE = "RUHI_REGISTER_FACE"
    const val COMMAND_GET_MOOD = "RUHI_GET_MOOD"

    fun broadcastEmotion(context: Context, expression: RobotFaceView.Expression, mood: MoodState) {
        context.sendBroadcast(Intent(ACTION_EMOTION_CHANGED).apply {
            putExtra("expression", expression.name)
            putExtra("happiness", mood.happiness)
            putExtra("energy", mood.energy)
        })
    }

    fun broadcastFace(context: Context, nickname: String, confidence: Float) {
        context.sendBroadcast(Intent(ACTION_FACE_RECOGNIZED).apply {
            putExtra("nickname", nickname)
            putExtra("confidence", confidence)
        })
    }

    fun broadcastObject(context: Context, label: String, confidence: Float) {
        context.sendBroadcast(Intent(ACTION_OBJECT_DETECTED).apply {
            putExtra("label", label)
            putExtra("confidence", confidence)
        })
    }

    fun broadcastBatteryLow(context: Context, level: Int) {
        context.sendBroadcast(Intent(ACTION_BATTERY_LOW).apply { putExtra("level", level) })
    }
}

class RuhiCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RuhiSDK.ACTION_COMMAND) return
        val command = intent.getStringExtra("command") ?: return
        when (command) {
            RuhiSDK.COMMAND_GET_MOOD -> {
                context.sendBroadcast(Intent(RuhiSDK.ACTION_MOOD_RESULT).apply {
                    putExtra("happiness", 0.55f)
                    putExtra("energy", 0.72f)
                    putExtra("curiosity", 0.6f)
                })
            }
            RuhiSDK.COMMAND_REGISTER_FACE,
            RuhiSDK.COMMAND_QUERY,
            RuhiSDK.COMMAND_SPEAK,
            RuhiSDK.COMMAND_SET_EMOTION,
            RuhiSDK.COMMAND_DRIVE -> {
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtras(intent)
                })
            }
        }
    }
}
