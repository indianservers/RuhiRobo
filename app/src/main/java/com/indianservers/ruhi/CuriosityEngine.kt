package com.indianservers.ruhi

import com.indianservers.ruhi.hardware.RobotHardwareController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CuriosityTarget(val topic: String, val question: String, val canTeach: Boolean)

class CuriosityEngine(
    private val database: RuhiDatabase,
    private val hardware: RobotHardwareController? = null
) {
    suspend fun absorbConversation(text: String, source: String = "conversation"): CuriosityTarget? = withContext(Dispatchers.IO) {
        val topics = extractTopics(text)
        topics.forEach { topic ->
            val existing = database.knowledgeDao().findTopic(topic)
            database.knowledgeDao().upsertTopic(
                existing?.copy(
                    knowledgeLevel = (existing.knowledgeLevel + 0.08f).coerceAtMost(1f),
                    interestLevel = (existing.interestLevel + 0.04f).coerceAtMost(1f),
                    learnedAt = System.currentTimeMillis()
                ) ?: KnowledgeTopic(topic = topic, knowledgeLevel = 0.18f, interestLevel = 0.72f, source = source, learnedAt = System.currentTimeMillis())
            )
        }
        topics.zipWithNext().forEach { (a, b) -> database.knowledgeDao().insertLink(KnowledgeLink(topicA = a, topicB = b, relationship = "mentioned_near")) }
        selectTarget()
    }

    suspend fun onUserAnswer(topic: String, answer: String) = withContext(Dispatchers.IO) {
        val clean = topic.lowercase()
        val existing = database.knowledgeDao().findTopic(clean)
        database.knowledgeDao().upsertTopic(
            existing?.copy(
                knowledgeLevel = (existing.knowledgeLevel + 0.2f).coerceAtMost(1f),
                interestLevel = (existing.interestLevel + 0.08f).coerceAtMost(1f),
                learnedAt = System.currentTimeMillis()
            ) ?: KnowledgeTopic(topic = clean, knowledgeLevel = 0.35f, interestLevel = 0.78f, source = "answer", learnedAt = System.currentTimeMillis())
        )
        extractTopics(answer).take(2).forEach { sub ->
            database.knowledgeDao().upsertTopic(KnowledgeTopic(topic = "$clean:$sub", knowledgeLevel = 0.05f, interestLevel = 0.75f, source = "follow_up", learnedAt = System.currentTimeMillis()))
            database.knowledgeDao().insertLink(KnowledgeLink(topicA = clean, topicB = "$clean:$sub", relationship = "follow_up"))
        }
    }

    suspend fun selectTarget(): CuriosityTarget? = withContext(Dispatchers.IO) {
        val target = database.knowledgeDao().curiosityTargets(1).firstOrNull() ?: return@withContext null
        CuriosityTarget(
            topic = target.topic,
            question = "I keep wondering about ${target.topic}. What should I understand about it?",
            canTeach = database.knowledgeDao().masteredCount(target.topic.substringBefore(":")) >= 10
        )
    }

    suspend fun investigateObject(label: String) {
        hardware?.lookAt(0f, 0f)
        hardware?.driveForward(8f, 0.2f)
        absorbConversation(label, "vision")
    }

    private fun extractTopics(text: String): List<String> {
        val stop = setOf("this", "that", "with", "from", "have", "what", "when", "where", "your", "about", "there", "their", "would", "could", "should")
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length in 4..22 && it !in stop }
            .distinct()
            .take(8)
    }
}
