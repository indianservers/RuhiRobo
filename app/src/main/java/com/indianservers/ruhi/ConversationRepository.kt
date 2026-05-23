package com.indianservers.ruhi

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ConversationRepository(
    context: Context,
    private val apiKey: String = BuildConfig.CLAUDE_API_KEY
) {
    val appContext: Context = context.applicationContext
    private val database = RuhiDatabase.getInstance(context)
    private val api: ClaudeApiService

    init {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder().addInterceptor(logger).build()
        api = Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ClaudeApiService::class.java)
    }

    suspend fun send(
        systemPrompt: String,
        messages: List<ClaudeMessage>,
        tools: List<ClaudeTool>
    ): ClaudeResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            error("CLAUDE_API_KEY is missing")
        }
        api.sendMessage(
            apiKey = apiKey,
            request = ClaudeRequest(
                system = systemPrompt,
                messages = messages,
                tools = tools
            )
        )
    }

    suspend fun saveConversation(userText: String, ruhiResponse: String, emotion: String) {
        withContext(Dispatchers.IO) {
            database.conversationDao().insert(
                ConversationEntry(
                    timestamp = System.currentTimeMillis(),
                    userText = userText,
                    ruhiResponse = ruhiResponse,
                    detectedEmotion = emotion
                )
            )
        }
    }

    suspend fun conversationCount(): Int = withContext(Dispatchers.IO) {
        database.conversationDao().count()
    }

    suspend fun saveMoodSnapshot(moodState: MoodState) {
        withContext(Dispatchers.IO) {
            database.moodDao().insert(
                MoodSnapshot(
                    timestamp = System.currentTimeMillis(),
                    happiness = moodState.happiness,
                    energy = moodState.energy,
                    curiosity = moodState.curiosity
                )
            )
        }
    }

    suspend fun latestMoodSnapshot(): MoodSnapshot? = withContext(Dispatchers.IO) {
        database.moodDao().latest()
    }

    suspend fun saveMemoryFromConversation(userText: String, ruhiResponse: String, emotion: String) {
        val summary = summarizeMemory(userText, ruhiResponse)
        withContext(Dispatchers.IO) {
            database.memoryFragmentDao().insert(
                MemoryFragment(
                    summary = summary,
                    embedding = keywordVector(summary + " " + userText + " " + ruhiResponse),
                    emotionTag = emotion,
                    timestamp = System.currentTimeMillis(),
                    importance = if (emotion in listOf("SAD", "WORRIED", "LOVE")) 5 else 3
                )
            )
        }
    }

    suspend fun topMemories(): List<MemoryFragment> = withContext(Dispatchers.IO) {
        database.memoryFragmentDao().topRecent(5)
    }

    suspend fun relevantMemories(query: String, limit: Int = 5): List<MemoryFragment> = withContext(Dispatchers.IO) {
        val queryTerms = terms(query)
        if (queryTerms.isEmpty()) return@withContext database.memoryFragmentDao().topRecent(limit)
        database.memoryFragmentDao().recent(80)
            .map { memory ->
                val memoryTerms = terms(memory.summary + " " + memory.embedding)
                val overlap = queryTerms.count { it in memoryTerms }
                val score = overlap * 10 + memory.importance + ((System.currentTimeMillis() - memory.timestamp) / 86_400_000L).let { -it.coerceAtMost(30L).toInt() }
                memory to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    suspend fun personalityProfile(): PersonalityProfile = withContext(Dispatchers.IO) {
        database.personalityProfileDao().get() ?: PersonalityProfile(
            openness = 0.55f,
            warmth = 0.7f,
            playfulness = 0.65f,
            seriousness = 0.35f,
            updatedAt = System.currentTimeMillis()
        ).also { database.personalityProfileDao().upsert(it) }
    }

    suspend fun updatePersonality(profile: PersonalityProfile) = withContext(Dispatchers.IO) {
        database.personalityProfileDao().upsert(profile)
    }

    suspend fun faceProfiles(): List<FaceProfile> = withContext(Dispatchers.IO) {
        database.faceProfileDao().all()
    }

    suspend fun saveFaceProfile(profile: FaceProfile): Long = withContext(Dispatchers.IO) {
        database.faceProfileDao().insert(profile)
    }

    suspend fun updateFaceSeen(profile: FaceProfile) = withContext(Dispatchers.IO) {
        database.faceProfileDao().updateSeen(
            id = profile.id,
            score = (profile.relationshipScore + 0.02f).coerceAtMost(1f),
            interactions = profile.totalInteractions + 1,
            lastSeen = System.currentTimeMillis()
        )
    }

    suspend fun achievements(): List<Achievement> = withContext(Dispatchers.IO) {
        database.achievementDao().all()
    }

    suspend fun upsertAchievement(achievement: Achievement) = withContext(Dispatchers.IO) {
        database.achievementDao().upsert(achievement)
    }

    private fun summarizeMemory(userText: String, ruhiResponse: String): String {
        val cleanUser = userText.trim().take(90)
        val cleanRuhi = ruhiResponse.trim().take(90)
        return "User said '$cleanUser' and Ruhi responded '$cleanRuhi'."
    }

    private fun keywordVector(text: String): String = terms(text).distinct().take(24).joinToString(",")

    private fun terms(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length > 3 }
            .toSet()
    }
}
