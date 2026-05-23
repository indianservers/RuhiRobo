package com.indianservers.ruhi

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

data class ClaudeMessage(
    val role: String,
    val content: List<ClaudeContentBlock>
)

data class ClaudeContentBlock(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: Map<String, Any?>? = null,
    val tool_use_id: String? = null,
    val content: String? = null
)

data class ClaudeTool(
    val name: String,
    val description: String,
    val input_schema: Map<String, Any?>
)

data class ClaudeRequest(
    val model: String = "claude-haiku-4-5-20251001",
    val max_tokens: Int = 200,
    val system: String,
    val messages: List<ClaudeMessage>,
    val tools: List<ClaudeTool>
)

data class ClaudeResponse(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val content: List<ClaudeContentBlock> = emptyList(),
    val stop_reason: String? = null
)

interface ClaudeApiService {
    @Headers("anthropic-version: 2023-06-01")
    @POST("v1/messages")
    suspend fun sendMessage(
        @Header("x-api-key") apiKey: String,
        @Body request: ClaudeRequest
    ): ClaudeResponse
}
