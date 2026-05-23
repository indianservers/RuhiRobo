package com.indianservers.ruhi

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.indianservers.ruhi.hardware.RobotSound
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import kotlin.random.Random

data class VoiceProfile(val pitch: Float, val speed: Float, val volume: Float, val prefix: String = "")

class VoicePersonalityEngine {
    fun profileFor(expression: RobotFaceView.Expression, needs: RobotNeeds, night: Boolean = false): VoiceProfile {
        return when {
            night -> VoiceProfile(1.0f, 0.85f, 0.3f, "Hmm... ")
            needs.energy < 0.35f -> VoiceProfile(0.8f, 0.7f, 0.45f, if (Random.nextFloat() < 0.25f) "...ugh, " else "")
            expression in listOf(RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.GRIN, RobotFaceView.Expression.LOVE) -> VoiceProfile(1.5f, 1.2f, 1f, if (Random.nextFloat() < 0.15f) "Oh! " else "")
            expression == RobotFaceView.Expression.ANGRY -> VoiceProfile(0.85f, 1.1f, 0.9f, "")
            expression in listOf(RobotFaceView.Expression.SAD, RobotFaceView.Expression.CRYING) -> VoiceProfile(0.9f, 0.8f, 0.7f, "Well... ")
            expression == RobotFaceView.Expression.THINKING -> VoiceProfile(1.1f, 0.85f, 0.8f, "Let me think... ")
            else -> VoiceProfile(1.3f, 0.95f, 0.9f, if (Random.nextFloat() < 0.15f) listOf("Hmm... ", "Actually, ", "You know what? ").random() else "")
        }
    }

    fun speak(tts: TextToSpeech, text: String, expression: RobotFaceView.Expression, needs: RobotNeeds, utteranceId: String = "ruhi") {
        val profile = profileFor(expression, needs)
        tts.setPitch(profile.pitch)
        tts.setSpeechRate(profile.speed)
        tts.speak(profile.prefix + text, TextToSpeech.QUEUE_FLUSH, Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, profile.volume)
        }, utteranceId)
    }
}

data class SoundIdentity(val chirps: Boolean = true, val whistles: Boolean = true, val clicks: Boolean = true, val hums: Boolean = true)

class VoiceManager(private val context: Context, private val database: RuhiDatabase) {
    private val http = OkHttpClient()
    var elevenLabsApiKey: String = ""
    var elevenLabsVoiceId: String = ""
    var soundIdentity: SoundIdentity = SoundIdentity()

    suspend fun cachedAudioFor(text: String): File? {
        val key = text.lowercase().take(80)
        return database.audioCacheDao().find(key)?.let { File(it.filePath).takeIf(File::exists) }
    }

    suspend fun generateElevenLabs(text: String): File? {
        if (elevenLabsApiKey.isBlank() || elevenLabsVoiceId.isBlank()) return null
        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$elevenLabsVoiceId/stream")
            .addHeader("xi-api-key", elevenLabsApiKey)
            .build()
        val bytes = runCatching { http.newCall(request).execute().body?.bytes() }.getOrNull() ?: return null
        val file = File(context.cacheDir, "voice-${text.hashCode()}.mp3").apply { writeBytes(bytes) }
        database.audioCacheDao().upsert(AudioCache(cacheKey = text.lowercase().take(80), filePath = file.absolutePath, createdAt = System.currentTimeMillis()))
        return file
    }

    fun play(file: File) {
        MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setDataSource(file.absolutePath)
            setOnCompletionListener { it.release() }
            prepare()
            start()
        }
    }

    fun soundFor(expression: RobotFaceView.Expression): RobotSound? = when (expression) {
        RobotFaceView.Expression.THINKING -> RobotSound.CURIOUS_HUM
        RobotFaceView.Expression.HAPPY, RobotFaceView.Expression.GRIN -> RobotSound.EXCITED_CHIRP
        RobotFaceView.Expression.CURIOUS -> RobotSound.BOOP
        RobotFaceView.Expression.LOVE -> RobotSound.PURR
        else -> null
    }
}
