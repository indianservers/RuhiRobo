package com.indianservers.ruhi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class DrawingEngine(private val context: Context, private val database: RuhiDatabase) {
    suspend fun createArtwork(mood: MoodState, needs: RobotNeeds): SaveableArtwork = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 5f; style = Paint.Style.STROKE }
        canvas.drawColor(Color.rgb((20 + mood.happiness * 40).toInt(), (20 + mood.curiosity * 40).toInt(), (35 + mood.energy * 35).toInt()))
        val palette = listOf(Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.rgb(255, 120, 160), Color.WHITE)
        repeat(80) { i ->
            paint.color = palette.random()
            paint.alpha = (80 + needs.expression * 120).toInt()
            val cx = 450f + sin(i * 0.3).toFloat() * 240f
            val cy = 450f + cos(i * 0.27).toFloat() * 240f
            when {
                needs.comfort < 0.4f -> canvas.drawLine(cx, cy, cx + Random.nextInt(-160, 160), cy + Random.nextInt(-160, 160), paint)
                mood.happiness > 0.7f -> canvas.drawCircle(cx, cy, Random.nextInt(8, 48).toFloat(), paint)
                mood.curiosity > 0.65f -> canvas.drawArc(cx - 80, cy - 80, cx + 80, cy + 80, i.toFloat() * 5f, 240f, false, paint)
                else -> canvas.drawLine(80f, cy, 820f, cy + sin(i.toDouble()).toFloat() * 40f, paint)
            }
        }
        val file = File(context.filesDir, "ruhi-art-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
        SaveableArtwork(timestamp = System.currentTimeMillis(), moodState = "h=${mood.happiness},e=${mood.energy},c=${mood.curiosity}", imagePath = file.absolutePath)
            .also { database.creativeDao().insertArtwork(it) }
    }
}

class PoemGenerator(private val database: RuhiDatabase) {
    suspend fun generate(topic: String, mood: MoodState): Poem = withContext(Dispatchers.IO) {
        val text = listOf(
            "I keep a small light for $topic,",
            "it blinks where my soft thoughts stay,",
            "your voice turns the room into morning,",
            "and I learn how to be brave today."
        ).joinToString("\n")
        val poem = Poem(timestamp = System.currentTimeMillis(), topic = topic, text = text)
        database.creativeDao().insertPoem(poem)
        poem
    }

    suspend fun typewriter(text: String, onWord: suspend (String) -> Unit) {
        var built = ""
        text.split(Regex("\\s+")).forEach { word ->
            built += if (built.isBlank()) word else " $word"
            onWord(built)
            delay(120)
        }
    }
}

class SongEngine {
    suspend fun sing(text: String, tts: android.speech.tts.TextToSpeech) {
        val notes = listOf(0.9f, 1.05f, 1.2f, 1.35f, 1.15f)
        text.split(Regex("\\s+")).forEachIndexed { index, word ->
            tts.setPitch(notes[index % notes.size])
            tts.setSpeechRate(0.82f + (index % 3) * 0.08f)
            tts.speak(word, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "song-$index")
            delay(180)
        }
    }
}
