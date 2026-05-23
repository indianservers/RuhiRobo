package com.indianservers.ruhi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.indianservers.ruhi.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProactiveEngine(
    private val context: Context,
    private val conversationManager: ConversationManager,
    private val scope: CoroutineScope,
    private val onMessage: suspend (String) -> Unit
) {
    private var job: Job? = null
    private var lastInteraction = System.currentTimeMillis()
    private var backgrounded = false

    fun markInteraction() {
        lastInteraction = System.currentTimeMillis()
    }

    fun setBackgrounded(value: Boolean) {
        backgrounded = value
    }

    fun start(moodProvider: () -> MoodState, blendProvider: () -> EmotionalBlend) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(10 * 60_000L)
                val idleFor = System.currentTimeMillis() - lastInteraction
                val mood = moodProvider()
                val prompt = when {
                    mood.happiness < 0.3f -> "The user seems quiet or low. Say one caring AI pet check-in."
                    mood.energy > 0.8f && idleFor > 10 * 60_000L -> "Ruhi has high energy and no interaction. Say a playful joke or fun fact invitation."
                    DailyRoutineManager.timeOfDay() == "morning" -> "Say a personalized morning greeting as Ruhi."
                    DailyRoutineManager.timeOfDay() == "evening" -> "Ask about the user's day and offer relaxation mode."
                    idleFor > 2L * 24L * 60L * 60L * 1000L -> "Say a short I miss you message for a returning user."
                    else -> null
                }
                if (prompt != null) {
                    val message = conversationManager.reply(prompt, mood, blendProvider())
                    onMessage(message)
                    if (backgrounded) notify(message)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun notify(message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Ruhi", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ruhi")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(2001, notification)
    }

    private companion object {
        const val CHANNEL_ID = "ruhi_proactive"
    }
}
