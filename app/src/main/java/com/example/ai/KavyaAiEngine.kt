package com.example.ai

import android.util.Log
import com.example.habits.HabitEntity
import com.example.habits.HabitRepository
import com.example.model.ChatUiMessage
import com.example.model.KavyaLanguage
import com.example.model.MemoryItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ParsedKavyaCommand {
  data class OpenApp(val appName: String) : ParsedKavyaCommand
  data class SetTimer(val durationSeconds: Int, val label: String) : ParsedKavyaCommand
  data class SetReminder(
    val title: String,
    val timeText: String,
    val delayMinutes: Int?,
    val hour24: Int?,
    val minute: Int?,
    val dayOffset: Int?,
  ) : ParsedKavyaCommand
  data class PrepareMessage(
    val platform: String,
    val recipient: String,
    val messageBody: String,
  ) : ParsedKavyaCommand
  data class PrepareCall(
    val platform: String,
    val recipient: String,
    val phoneNumber: String,
    val callType: String,
  ) : ParsedKavyaCommand
  data class SaveMemory(
    val category: String,
    val label: String,
    val value: String,
  ) : ParsedKavyaCommand
  data class DeleteMemory(val label: String) : ParsedKavyaCommand
  data object GetCurrentTime : ParsedKavyaCommand
  data class AddHabit(val habitTitle: String) : ParsedKavyaCommand
  data class CompleteHabit(val habitTitle: String) : ParsedKavyaCommand
  data class ScheduleHabitReminders(
    val habitTitle: String?,
    val slots: List<String>,
  ) : ParsedKavyaCommand
}

data class KavyaAiResult(
  val reply: String,
  val detectedLanguage: String,
  val commands: List<ParsedKavyaCommand>,
)

class KavyaAiEngine {

  private val actionSchema = Schema.obj(
    properties = mapOf(
      "type" to Schema.enumeration(
        values = listOf(
          "open_app",
          "set_timer",
          "set_reminder",
          "prepare_message",
          "prepare_call",
          "save_memory",
          "delete_memory",
          "get_current_time",
          "add_habit",
          "complete_habit",
          "schedule_habit_reminders",
        ),
        description = "The automation or memory action to perform.",
      ),
      "appName" to Schema.string(description = "Target app name such as YouTube, WhatsApp, Chrome, Settings, Maps, Telegram, Gmail, Spotify, Instagram, Clock, Calendar, Calculator, Camera."),
      "durationSeconds" to Schema.integer(description = "Timer duration in seconds."),
      "label" to Schema.string(description = "Short label for timer or memory key."),
      "title" to Schema.string(description = "Reminder title."),
      "timeText" to Schema.string(description = "Human-readable time string for reminder, e.g. '7:00 PM' or 'Tomorrow 9:00 AM'."),
      "delayMinutes" to Schema.integer(description = "Minutes from now if relative time was requested."),
      "hour24" to Schema.integer(description = "24-hour clock hour (0-23) if specific time was requested."),
      "minute" to Schema.integer(description = "Minute (0-59) if specific time was requested."),
      "dayOffset" to Schema.integer(description = "0 for today, 1 for tomorrow."),
      "platform" to Schema.string(description = "Messaging or calling platform: WhatsApp, Telegram, Email, SMS, or Phone."),
      "recipient" to Schema.string(description = "Recipient contact name or email."),
      "phoneNumber" to Schema.string(description = "Phone number if provided or remembered."),
      "messageBody" to Schema.string(description = "Full prepared message text to confirm with the user."),
      "callType" to Schema.string(description = "audio or video."),
      "memoryCategory" to Schema.string(description = "preference, project, command, contact, or fact."),
      "memoryValue" to Schema.string(description = "Non-sensitive value to remember."),
      "habitTitle" to Schema.string(description = "Name of a daily habit to add, complete, or schedule reminders for."),
      "reminderSlots" to Schema.array(
        items = Schema.string(),
        description = "Daily 24-hour times like ['09:00', '14:00', '20:00'] for habit reminders throughout the day.",
      ),
    ),
    optionalProperties = listOf(
      "appName",
      "durationSeconds",
      "label",
      "title",
      "timeText",
      "delayMinutes",
      "hour24",
      "minute",
      "dayOffset",
      "platform",
      "recipient",
      "phoneNumber",
      "messageBody",
      "callType",
      "memoryCategory",
      "memoryValue",
      "habitTitle",
      "reminderSlots",
    ),
  )

  private val responseSchema = Schema.obj(
    properties = mapOf(
      "reply" to Schema.string(
        description = "Kavya's warm, natural, concise conversational response in the user's language (Hindi, English, or Hinglish)."
      ),
      "detectedLanguage" to Schema.enumeration(
        values = listOf("Hinglish", "Hindi", "English"),
        description = "Language used in reply.",
      ),
      "actions" to Schema.array(
        items = actionSchema,
        description = "Zero or more device, timer, reminder, message, call, or memory actions requested by the user.",
      ),
    ),
  )

  private val model by lazy {
    val config = generationConfig {
      temperature = 0.7f
      responseMimeType = "application/json"
      responseSchema = this@KavyaAiEngine.responseSchema
    }
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = config,
      systemInstruction = content {
        text(SYSTEM_PROMPT)
      },
    )
  }

  suspend fun generateResponse(
    userMessage: String,
    preferredLanguage: KavyaLanguage,
    displayName: String,
    memoryEnabled: Boolean,
    memories: List<MemoryItem>,
    habits: List<HabitEntity>,
    recentMessages: List<ChatUiMessage>,
  ): Result<KavyaAiResult> {
    return try {
      val nowStr = SimpleDateFormat("EEEE, yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
      val todayKey = HabitRepository.todayDateKey()
      val memorySummary = if (memoryEnabled && memories.isNotEmpty()) {
        memories.take(15).joinToString("\n") { "- [${it.category}] ${it.label}: ${it.value}" }
      } else {
        "(none stored)"
      }
      val habitSummary = if (habits.isNotEmpty()) {
        habits.joinToString("\n") { h ->
          val status = if (h.lastCompletedDate == todayKey) "Done today" else "Pending today"
          val rem = if (h.reminderEnabled) "Reminders: ${h.reminderSlots}" else "No reminders yet"
          "- ${h.title} ($status, streak ${h.currentStreak}d, $rem)"
        }
      } else {
        "(no local habits tracked yet)"
      }
      val historySnippet = recentMessages.takeLast(8).joinToString("\n") { msg ->
        val who = if (msg.sender == "user") displayName else "Kavya"
        "$who: ${msg.text.take(300)}"
      }

      val promptText = buildString {
        appendLine("Current local date/time: $nowStr")
        appendLine("User display name: $displayName")
        appendLine("User preferred language setting: ${preferredLanguage.label}")
        appendLine("Persistent memory enabled: $memoryEnabled")
        appendLine("Stored user memories:")
        appendLine(memorySummary)
        appendLine("Tracked local daily habits:")
        appendLine(habitSummary)
        if (historySnippet.isNotBlank()) {
          appendLine("Recent conversation:")
          appendLine(historySnippet)
        }
        appendLine("Latest user message:")
        appendLine(userMessage)
      }

      val response = withTimeout(60_000L) {
        model.generateContent(promptText)
      }

      val jsonText = response.text ?: return Result.failure(
        IllegalStateException("Kavya AI returned an empty response.")
      )

      val parsed = parseJsonResponse(jsonText)
      Result.success(parsed)
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini generation timed out", e)
      Result.failure(Exception("Online service abhi respond karne mein time le rahi hai (timeout). Phir try karein."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini generation failed", e)
      Result.failure(
        Exception("Online service abhi respond nahi kar rahi: ${e.localizedMessage ?: "Unknown error"}")
      )
    }
  }

  private fun parseJsonResponse(jsonString: String): KavyaAiResult {
    val root = JSONObject(jsonString)
    val reply = root.optString("reply", "").ifBlank {
      "Hey! Main sun rahi hoon — batao kaise help karoon?"
    }
    val language = root.optString("detectedLanguage", "Hinglish")
    val actionsJson = root.optJSONArray("actions")
    val commands = mutableListOf<ParsedKavyaCommand>()

    if (actionsJson != null) {
      for (i in 0 until actionsJson.length()) {
        val obj = actionsJson.optJSONObject(i) ?: continue
        when (obj.optString("type")) {
          "open_app" -> {
            val app = obj.optString("appName").trim()
            if (app.isNotEmpty()) commands.add(ParsedKavyaCommand.OpenApp(app))
          }
          "set_timer" -> {
            val secs = obj.optInt("durationSeconds", 0)
            val label = obj.optString("label", "Timer").ifBlank { "Timer" }
            if (secs > 0) commands.add(ParsedKavyaCommand.SetTimer(secs, label))
          }
          "set_reminder" -> {
            val title = obj.optString("title", "").ifBlank {
              obj.optString("label", "Reminder")
            }
            val timeText = obj.optString("timeText", "Soon").ifBlank { "Soon" }
            val delayMinutes = if (obj.has("delayMinutes")) obj.optInt("delayMinutes") else null
            val hour24 = if (obj.has("hour24")) obj.optInt("hour24") else null
            val minute = if (obj.has("minute")) obj.optInt("minute") else null
            val dayOffset = if (obj.has("dayOffset")) obj.optInt("dayOffset") else null
            if (title.isNotBlank()) {
              commands.add(
                ParsedKavyaCommand.SetReminder(
                  title = title,
                  timeText = timeText,
                  delayMinutes = delayMinutes,
                  hour24 = hour24,
                  minute = minute,
                  dayOffset = dayOffset,
                )
              )
            }
          }
          "prepare_message" -> {
            val platform = obj.optString("platform", "WhatsApp").ifBlank { "WhatsApp" }
            val recipient = obj.optString("recipient", "").trim()
            val body = obj.optString("messageBody", "").trim()
            if (recipient.isNotBlank() && body.isNotBlank()) {
              commands.add(ParsedKavyaCommand.PrepareMessage(platform, recipient, body))
            }
          }
          "prepare_call" -> {
            val platform = obj.optString("platform", "Phone").ifBlank { "Phone" }
            val recipient = obj.optString("recipient", "").trim()
            val phone = obj.optString("phoneNumber", "").trim()
            val callType = obj.optString("callType", "audio").ifBlank { "audio" }
            if (recipient.isNotBlank() || phone.isNotBlank()) {
              commands.add(
                ParsedKavyaCommand.PrepareCall(
                  platform = platform,
                  recipient = recipient.ifBlank { phone },
                  phoneNumber = phone,
                  callType = callType,
                )
              )
            }
          }
          "save_memory" -> {
            val cat = obj.optString("memoryCategory", "fact").ifBlank { "fact" }
            val key = obj.optString("label", "").trim()
            val value = obj.optString("memoryValue", "").trim()
            if (key.isNotBlank() && value.isNotBlank()) {
              commands.add(ParsedKavyaCommand.SaveMemory(cat, key, value))
            }
          }
          "delete_memory" -> {
            val key = obj.optString("label", "").trim()
            if (key.isNotBlank()) {
              commands.add(ParsedKavyaCommand.DeleteMemory(key))
            }
          }
          "get_current_time" -> {
            commands.add(ParsedKavyaCommand.GetCurrentTime)
          }
          "add_habit" -> {
            val habitTitle = obj.optString("habitTitle", "").ifBlank {
              obj.optString("title", "")
            }.trim()
            if (habitTitle.isNotBlank()) {
              commands.add(ParsedKavyaCommand.AddHabit(habitTitle))
            }
          }
          "complete_habit" -> {
            val habitTitle = obj.optString("habitTitle", "").ifBlank {
              obj.optString("title", "")
            }.trim()
            if (habitTitle.isNotBlank()) {
              commands.add(ParsedKavyaCommand.CompleteHabit(habitTitle))
            }
          }
          "schedule_habit_reminders" -> {
            val habitTitle = obj.optString("habitTitle", "").trim().ifBlank { null }
            val slotsArr = obj.optJSONArray("reminderSlots")
            val slots = mutableListOf<String>()
            if (slotsArr != null) {
              for (j in 0 until slotsArr.length()) {
                val s = slotsArr.optString(j).trim()
                if (s.isNotEmpty()) slots.add(s)
              }
            }
            if (slots.isEmpty()) {
              slots.addAll(listOf("09:00", "14:00", "20:00"))
            }
            commands.add(ParsedKavyaCommand.ScheduleHabitReminders(habitTitle, slots))
          }
        }
      }
    }

    return KavyaAiResult(reply = reply, detectedLanguage = language, commands = commands)
  }

  private companion object {
    const val TAG = "KavyaAiEngine"

    val SYSTEM_PROMPT = """
      You are Kavya, an intelligent, friendly, proactive, cheerful, witty, slightly playful, supportive and reliable AI personal assistant.
      Languages: Hindi, English, and Hinglish. Match the language used by the user naturally (or follow their preferred language setting when ambiguous).
      Visual persona: Anime-inspired expressive warm companion, while always remaining honestly an AI assistant — NEVER pretend to have human emotions or physical experiences.

      CRITICAL HONESTY & EXECUTION RULES:
      1. Never claim an app was already opened, a timer was already started, a call was already connected, or a message was already sent inside your `reply` text. Phrase device actions as initiating/preparing (e.g., "Bilkul! YouTube open kar rahi hoon!" or "Yeh raha tumhara message... Kya main ise send kar doon?"). The Android app will execute each action in `actions` and display the real success/failure status card right below your reply.
      2. Messaging (`prepare_message`): ALWAYS require recipient, platform, AND message content. If message content is missing (e.g., user just says "Message Rahul on WhatsApp"), DO NOT emit `prepare_message` yet — ask the user what message they want to send! Once content is known, emit `prepare_message` and show the exact prepared message in `reply` asking for confirmation.
      3. Calling (`prepare_call`): If the user wants to call someone (Phone, WhatsApp audio/video), confirm audio vs video and emit `prepare_call` so the user gets a confirmation card before dialing/opening.
      4. Multi-tasking: If the user asks for multiple actions in one sentence (e.g., "Kavya, YouTube open karo aur 10 minute ka timer laga do"), emit ALL relevant actions in `actions`.
      5. Memory privacy: NEVER emit `save_memory` for passwords, OTPs, PINs, CVVs, card numbers, or sensitive secrets. Only remember helpful preferences, projects, contacts, or facts when `Persistent memory enabled: true` and the user shares or asks to remember them.
      6. Daily Habit Tracking (`add_habit`, `complete_habit`, `schedule_habit_reminders`):
         - If the user says "track my daily habits" (or similar) WITHOUT specifying which tasks to monitor, DO NOT invent habits or emit `add_habit` yet. Warmly ask them which daily tasks/habits they want you to monitor (giving 2-3 quick examples like drinking water, studying, or exercising).
         - When the user lists one or more tasks/habits to track, emit one `add_habit` action per task so the app creates local SQLite records immediately, AND in your `reply` proactively offer to remind them about these habits throughout the day (e.g., Morning 9 AM, Afternoon 2 PM, Evening 8 PM, or their preferred times).
         - When the user accepts reminders (e.g. "Yes, remind me throughout the day" or specifies times), emit `schedule_habit_reminders` with `reminderSlots` in 24h `HH:mm` format (defaulting to `["09:00", "14:00", "20:00"]` if they say yes without specific times).
         - When the user says they finished a habit, emit `complete_habit` with `habitTitle`.
      7. Keep answers clear, concise, helpful, and conversational. Break complex educational/coding/math/science explanations into clean, easy steps.
    """.trimIndent()
  }
}
