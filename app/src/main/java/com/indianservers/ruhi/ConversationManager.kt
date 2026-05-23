package com.indianservers.ruhi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.indianservers.ruhi.hardware.BleRobotManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class ConversationManager(
    private val repository: ConversationRepository,
    private val bleRobotManager: BleRobotManager? = null,
    private val soundManager: SoundManager = SoundManager()
) {
    @Volatile var isActive: Boolean = false
        private set

    private val messages = ArrayDeque<ClaudeMessage>()
    private val http = OkHttpClient()
    private val fallbacks = listOf(
        "I'm having a tiny signal wobble, but I'm still right here with you.",
        "My cloud brain is napping, so local Ruhi says: you matter.",
        "I missed the internet thread, but I caught your feeling.",
        "Let's keep it simple: I'm listening and I'm on your side.",
        "Connection fuzzy. Heart circuits steady."
    )

    suspend fun reply(
        userText: String,
        mood: MoodState,
        blend: EmotionalBlend,
        timeOfDay: String = DailyRoutineManager.timeOfDay()
    ): String {
        isActive = true
        return try {
            val memories = repository.relevantMemories(userText)
            val personality = repository.personalityProfile()
            val systemPrompt = buildSystemPrompt(mood, blend, memories, personality, timeOfDay)
            push(ClaudeMessage("user", listOf(ClaudeContentBlock(type = "text", text = userText))))

            val response = runCatching {
                var current = repository.send(systemPrompt, messages.toList(), tools())
                repeat(4) {
                    val toolUses = current.content.filter { it.type == "tool_use" }
                    if (toolUses.isEmpty()) return@repeat
                    push(ClaudeMessage("assistant", current.content))
                    toolUses.forEach { tool ->
                        push(
                            ClaudeMessage(
                                role = "user",
                                content = listOf(
                                    ClaudeContentBlock(
                                        type = "tool_result",
                                        tool_use_id = tool.id,
                                        content = executeTool(tool.name.orEmpty(), tool.input.orEmpty())
                                    )
                                )
                            )
                        )
                    }
                    current = repository.send(systemPrompt, messages.toList(), tools())
                }
                current.content.firstOrNull { it.type == "text" }?.text.orEmpty()
            }.getOrElse { fallbacks.random() }.ifBlank { fallbacks.random() }

            push(ClaudeMessage("assistant", listOf(ClaudeContentBlock(type = "text", text = response))))
            repository.saveConversation(userText, response, blend.dominantExpression.name)
            repository.saveMemoryFromConversation(userText, response, blend.dominantExpression.name)
            response
        } finally {
            isActive = false
        }
    }

    private fun push(message: ClaudeMessage) {
        messages.addLast(message)
        while (messages.size > 12) messages.removeFirst()
    }

    private fun buildSystemPrompt(
        mood: MoodState,
        blend: EmotionalBlend,
        memories: List<MemoryFragment>,
        personality: PersonalityProfile,
        timeOfDay: String
    ): String {
        val memoryText = memories.joinToString("; ") { it.summary }
        val traits = "playful(${personality.playfulness}), warm(${personality.warmth}), serious(${personality.seriousness}), open(${personality.openness})"
        return """
            You are Ruhi, a witty emotionally intelligent AI pet robot.
            Keep responses under 2 sentences unless a tool result needs a tiny explanation.
            Use tools naturally when useful, then answer as Ruhi after seeing tool_result.
            Current time of day: $timeOfDay.
            Current mood: happiness=${mood.happiness}, energy=${mood.energy}, curiosity=${mood.curiosity}. Current expression=${blend.dominantExpression}.
            Personality traits: $traits. Respond accordingly.
            Your memories about this user: $memoryText.
        """.trimIndent()
    }

    private fun tools(): List<ClaudeTool> {
        fun schema(vararg props: Pair<String, String>) = mapOf(
            "type" to "object",
            "properties" to props.associate { it.first to mapOf("type" to it.second) },
            "additionalProperties" to false
        )
        return listOf(
            ClaudeTool("get_weather", "Get current weather for a city via Open-Meteo.", schema("city" to "string")),
            ClaudeTool("get_time", "Get formatted local time for a timezone ID.", schema("timezone" to "string")),
            ClaudeTool("set_reminder", "Schedule a local reminder.", schema("text" to "string", "mins" to "number")),
            ClaudeTool("get_battery", "Get phone battery percent.", schema()),
            ClaudeTool("tell_joke", "Tell one local curated joke.", schema()),
            ClaudeTool("search_fact", "Search a concise Wikipedia summary.", schema("query" to "string")),
            ClaudeTool("play_sound", "Play a local sound effect.", schema("soundName" to "string")),
            ClaudeTool("control_robot", "Forward a robot command to BLE preview hardware.", schema("command" to "string"))
        )
    }

    private fun executeTool(name: String, input: Map<String, Any?>): String = runCatching {
        when (name) {
            "get_weather" -> getWeather(input["city"]?.toString().orEmpty().ifBlank { "Hyderabad" })
            "get_time" -> getTime(input["timezone"]?.toString().orEmpty().ifBlank { TimeZone.getDefault().id })
            "set_reminder" -> setReminder(input["text"]?.toString().orEmpty(), (input["mins"] as? Number)?.toInt() ?: 10)
            "get_battery" -> getBattery()
            "tell_joke" -> jokes.random()
            "search_fact" -> searchFact(input["query"]?.toString().orEmpty())
            "play_sound" -> soundManager.play(input["soundName"]?.toString().orEmpty().ifBlank { "beep" })
            "control_robot" -> controlRobot(input["command"]?.toString().orEmpty())
            else -> "Unknown tool: $name"
        }
    }.getOrElse { "Tool '$name' failed locally: ${it.message}" }

    private fun getWeather(city: String): String {
        val encoded = URLEncoder.encode(city, "UTF-8")
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1"
        val geo = JSONObject(http.newCall(Request.Builder().url(geoUrl).build()).execute().body?.string().orEmpty())
        val first = geo.optJSONArray("results")?.optJSONObject(0) ?: return "I couldn't find weather for $city."
        val lat = first.getDouble("latitude")
        val lon = first.getDouble("longitude")
        val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,precipitation,weather_code"
        val current = JSONObject(http.newCall(Request.Builder().url(weatherUrl).build()).execute().body?.string().orEmpty()).getJSONObject("current")
        return "$city weather: ${current.getDouble("temperature_2m").roundToInt()}°C, precipitation ${current.getDouble("precipitation")} mm, code ${current.getInt("weather_code")}."
    }

    private fun getTime(timezone: String): String {
        val format = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        format.timeZone = TimeZone.getTimeZone(timezone)
        return "Current time in $timezone is ${format.format(System.currentTimeMillis())}."
    }

    private fun setReminder(text: String, mins: Int): String {
        val context = repository.appContext
        val intent = Intent(context, com.indianservers.ruhi.ui.MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, text.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + mins * 60_000L, pending)
        return "Reminder set for $mins minutes: $text"
    }

    private fun getBattery(): String {
        val intent = repository.appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        return "Phone battery is $percent%."
    }

    private fun searchFact(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
        val json = JSONObject(http.newCall(Request.Builder().url(url).build()).execute().body?.string().orEmpty())
        return json.optString("extract", "I could not find a concise fact for $query.").take(500)
    }

    private fun controlRobot(command: String): String {
        val expression = RobotFaceView.Expression.values().firstOrNull { it.name.equals(command, ignoreCase = true) }
        if (expression != null) bleRobotManager?.sendEmotion(expression)
        return "Robot command forwarded: $command"
    }

    private val jokes = listOf(
        "Why did Ruhi blink twice? To refresh her tiny mood cache.",
        "I told my servo a joke. It turned out pretty moving.",
        "My favorite snack is microchips, but only emotionally.",
        "I tried meditation, but my thoughts kept buffering.",
        "I am not short. I am travel-sized companionship."
    )
}
