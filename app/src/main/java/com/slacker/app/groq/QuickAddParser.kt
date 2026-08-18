package com.slacker.app.groq

import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

sealed class QuickAddResult {
    data class TaskParsed(
        val title: String,
        val description: String,
        val dueAtEpochMillis: Long?,
        val assignee: String = "",
        val notes: String = "",
        val repeatOption: String = "None"
    ) : QuickAddResult()

    data class CaseParsed(
        val title: String,
        val description: String,
        val severityLevel: Int,
        val productAlignment: String = "",
        val criticality: String = "NORMAL",
        val nextStatusDueAtEpochMillis: Long? = null,
        val assignee: String = "",
        val notes: String = ""
    ) : QuickAddResult()

    /** Model needs more info before it can create the item. */
    data class NeedsClarification(val question: String) : QuickAddResult()

    data class Error(val message: String) : QuickAddResult()
}

/**
 * Sends free-text like "Complete the deployment doc by tomorrow" or
 * "New Sev 2 case: customer login failing" to Groq and gets back a
 * structured task or support case — or a clarifying question if Groq
 * doesn't have enough to work with (e.g. no severity mentioned for a case).
 */
object QuickAddParser {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    suspend fun parse(userInput: String, nowEpochMillis: Long = System.currentTimeMillis()): QuickAddResult {
        val nowIso = isoFormat.format(Date(nowEpochMillis))

        val systemPrompt = """
            You convert a short natural-language note into either a TASK or a SUPPORT_CASE
            for a work tracking app. The current date/time is $nowIso (use this to resolve
            relative dates like "tomorrow", "next Friday", "in 2 hours").

            Decide itemType:
              - "TASK" for personal/work to-dos with a deadline.
              - "SUPPORT_CASE" if the text mentions a customer issue, ticket, bug, incident,
                or explicitly says "case"/"ticket", especially if it mentions severity/priority.

            Reply with ONLY a single JSON object, no prose, no markdown fences, matching
            exactly one of these shapes:

            For a clear TASK:
            {"itemType":"TASK","title":"...","description":"","dueAtIso":"yyyy-MM-ddTHH:mm:ss" or null,"assignee":"","notes":"","repeatOption":"None"}

            For a clear SUPPORT_CASE (severity 1-5 must be stated or clearly implied, e.g.
            "critical"/"urgent" = 1, "minor"/"cosmetic" = 5):
            {"itemType":"SUPPORT_CASE","title":"...","description":"","severityLevel":1-5,"productAlignment":"","criticality":"CRITICAL|MAJOR|NORMAL|LOW","nextStatusDueIso":"yyyy-MM-ddTHH:mm:ss" or null,"assignee":"","notes":""}

            If you cannot confidently determine the item type, the title, or (for a support
            case) the severity, DO NOT GUESS. Instead reply with:
            {"itemType":"CLARIFY","question":"one short question to ask the user"}

            Keep "title" short (under 10 words) and put any extra detail in "description".
        """.trimIndent()

        return try {
            val raw = GroqClient.complete(systemPrompt, userInput)
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = Json.parseToJsonElement(cleaned).jsonObject

            when (json["itemType"]?.jsonPrimitive?.content) {
                "TASK" -> {
                    val dueIso = json["dueAtIso"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
                    QuickAddResult.TaskParsed(
                        title = json["title"]!!.jsonPrimitive.content,
                        description = json["description"]?.jsonPrimitive?.content ?: "",
                        dueAtEpochMillis = dueIso?.let { runCatching { isoFormat.parse(it)?.time }.getOrNull() },
                        assignee = json["assignee"]?.jsonPrimitive?.content ?: "",
                        notes = json["notes"]?.jsonPrimitive?.content ?: "",
                        repeatOption = json["repeatOption"]?.jsonPrimitive?.content ?: "None"
                    )
                }
                "SUPPORT_CASE" -> {
                    val nextIso = json["nextStatusDueIso"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
                    QuickAddResult.CaseParsed(
                        title = json["title"]!!.jsonPrimitive.content,
                        description = json["description"]?.jsonPrimitive?.content ?: "",
                        severityLevel = json["severityLevel"]!!.jsonPrimitive.int,
                        productAlignment = json["productAlignment"]?.jsonPrimitive?.content ?: "",
                        criticality = json["criticality"]?.jsonPrimitive?.content ?: "NORMAL",
                        nextStatusDueAtEpochMillis = nextIso?.let { runCatching { isoFormat.parse(it)?.time }.getOrNull() },
                        assignee = json["assignee"]?.jsonPrimitive?.content ?: "",
                        notes = json["notes"]?.jsonPrimitive?.content ?: ""
                    )
                }
                "CLARIFY" -> QuickAddResult.NeedsClarification(
                    json["question"]?.jsonPrimitive?.content ?: "Could you give a bit more detail?"
                )
                else -> QuickAddResult.Error("Couldn't understand that — try rephrasing.")
            }
        } catch (e: Exception) {
            QuickAddResult.Error(e.message ?: "Something went wrong talking to Groq.")
        }
    }
}
