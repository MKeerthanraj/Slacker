package com.slacker.app.groq

import com.slacker.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around Groq's OpenAI-compatible chat completions endpoint.
 * Free tier docs: https://console.groq.com/docs
 */
object GroqClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    // Fast + free-tier friendly model. Change here if Groq updates their lineup.
    private const val MODEL = "openai/gpt-oss-20b"

    /**
     * Sends a system + user prompt, returns the raw text of the model's reply.
     * Throws on network/HTTP failure so callers can show an error to the user.
     */
    suspend fun complete(systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", MODEL)
                put("temperature", 0.2)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    }
                }
            }.toString()

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    throw IllegalStateException("Groq API error ${response.code}: $responseBody")
                }
                val json = Json.parseToJsonElement(responseBody).jsonObject
                json["choices"]!!.jsonArray[0].jsonObject["message"]!!
                    .jsonObject["content"]!!.jsonPrimitive.content
            }
        }
}
