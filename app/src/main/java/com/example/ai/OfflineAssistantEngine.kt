package com.example.ai

import com.example.habits.HabitEntity
import com.example.habits.HabitRepository
import com.example.model.KavyaLanguage
import com.example.model.MemoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfflineAssistantEngine {

  fun processOffline(
    input: String,
    preferredLanguage: KavyaLanguage,
    displayName: String,
    memories: List<MemoryItem>,
    habits: List<HabitEntity> = emptyList(),
    lastKavyaMessage: String = "",
  ): KavyaAiResult {
    val trimmed = input.trim()
    val lower = trimmed.lowercase(Locale.ROOT)
    val commands = mutableListOf<ParsedKavyaCommand>()

    // 0. Habit tracking offline flow
    val kavyaAskedForHabits = lastKavyaMessage.contains("which tasks", ignoreCase = true) ||
      lastKavyaMessage.contains("kaun si habits", ignoreCase = true) ||
      lastKavyaMessage.contains("tasks to monitor", ignoreCase = true)

    val kavyaOfferedReminders = lastKavyaMessage.contains("remind you", ignoreCase = true) ||
      (lastKavyaMessage.contains("reminders", ignoreCase = true) &&
        (lastKavyaMessage.contains("bhejoon", ignoreCase = true) ||
          lastKavyaMessage.contains("set kar", ignoreCase = true) ||
          lastKavyaMessage.contains("9 AM", ignoreCase = true))) ||
      lastKavyaMessage.contains("throughout the day", ignoreCase = true)

    if (lower.contains("track my daily habits") ||
      lower.contains("track habits") ||
      lower.contains("daily habits track") ||
      lower.contains("habit tracker")
    ) {
      val reply = when (preferredLanguage) {
        KavyaLanguage.Hindi ->
          "Bilkul! Main tumhari daily habits track kar sakti hoon! Batao kaun si habits monitor karni hain — jaise paani peena, workout, ya padhai?"
        KavyaLanguage.English ->
          "I'd love to track your daily habits! Tell me which tasks you'd like me to monitor — for example, drinking water, reading, or working out?"
        KavyaLanguage.Hinglish ->
          "Bilkul $displayName! Main tumhari daily habits local record mein track kar sakti hoon. Batao kaun si habits monitor karni hain — jaise drink water, workout, ya study?"
      }
      return KavyaAiResult(reply = reply, detectedLanguage = preferredLanguage.label, commands = emptyList())
    }

    if (kavyaOfferedReminders && habits.isNotEmpty() &&
      (lower.startsWith("yes") || lower.startsWith("haan") || lower.startsWith("ha ") ||
        lower == "ha" || lower.contains("remind me") || lower.contains("sure") || lower.contains("ok"))
    ) {
      val defaultSlots = listOf("09:00", "14:00", "20:00")
      return KavyaAiResult(
        reply = "Done! Main tumhari tracked habits ke liye din bhar (9:00 AM, 2:00 PM, aur 8:00 PM) daily reminders set kar rahi hoon!",
        detectedLanguage = preferredLanguage.label,
        commands = listOf(ParsedKavyaCommand.ScheduleHabitReminders(null, defaultSlots)),
      )
    }

    if (kavyaAskedForHabits && !lower.contains("cancel") && !lower.contains("no")) {
      val items = trimmed.split(Regex("[,;\\n]+|\\band\\b|\\baur\\b"))
        .map { it.trim().trimStart('-', '*', '•', ' ') }
        .filter { it.isNotEmpty() }
      if (items.isNotEmpty()) {
        val cmds = items.map { ParsedKavyaCommand.AddHabit(it) }
        return KavyaAiResult(
          reply = "Maine ${items.joinToString(", ")} ko local habit record mein add karne ke liye bhej diya hai! Kya main tumhe din bhar (9 AM, 2 PM, 8 PM) inke reminders bhi bhejoon?",
          detectedLanguage = preferredLanguage.label,
          commands = cmds,
        )
      }
    }

    if (lower.startsWith("add habit ") || lower.startsWith("track habit ")) {
      val title = trimmed.substringAfter("habit ", "").trim()
      if (title.isNotEmpty()) {
        return KavyaAiResult(
          reply = "\"$title\" ko local habit tracker mein save kar rahi hoon! Kya main iske liye din bhar reminders set kar doon?",
          detectedLanguage = preferredLanguage.label,
          commands = listOf(ParsedKavyaCommand.AddHabit(title)),
        )
      }
    }

    if (lower.contains("habit") && (lower.contains("done") || lower.contains("complete") || lower.contains("finished"))) {
      val matched = habits.firstOrNull { lower.contains(it.title.lowercase(Locale.ROOT)) }
      if (matched != null) {
        return KavyaAiResult(
          reply = "Awesome! \"${matched.title}\" ko aaj ke liye complete mark kar rahi hoon!",
          detectedLanguage = preferredLanguage.label,
          commands = listOf(ParsedKavyaCommand.CompleteHabit(matched.title)),
        )
      }
    }

    if (lower.contains("my habits") || lower.contains("habit status")) {
      val todayKey = HabitRepository.todayDateKey()
      val reply = if (habits.isEmpty()) {
        "Abhi koi daily habit track nahi ho rahi. Bolo \"track my daily habits\" aur shuru karte hain!"
      } else {
        "Aaj ki local habits:\n" + habits.joinToString("\n") { h ->
          val mark = if (h.lastCompletedDate == todayKey) "Done" else "Pending"
          "• ${h.title} ($mark, ${h.currentStreak}d streak)"
        }
      }
      return KavyaAiResult(reply = reply, detectedLanguage = preferredLanguage.label, commands = emptyList())
    }

    // 1. Check multi-task or app launch commands
    val supportedApps = mapOf(
      "youtube" to "YouTube",
      "whatsapp" to "WhatsApp",
      "chrome" to "Chrome",
      "settings" to "Settings",
      "google maps" to "Google Maps",
      "maps" to "Maps",
      "telegram" to "Telegram",
      "gmail" to "Gmail",
      "spotify" to "Spotify",
      "instagram" to "Instagram",
      "clock" to "Clock",
      "calendar" to "Calendar",
      "calculator" to "Calculator",
      "play store" to "Play Store",
      "camera" to "Camera",
      "browser" to "Browser",
      "messages" to "Messages",
      "phone" to "Phone",
    )
    if (lower.contains("open ") || lower.contains("launch ") || lower.contains("kholo") || lower.contains("start ")) {
      for ((key, canonical) in supportedApps) {
        if (lower.contains(key)) {
          commands.add(ParsedKavyaCommand.OpenApp(canonical))
          break
        }
      }
    }

    // 2. Check timer commands
    val timerMatch = Regex("(\\d+)\\s*(sec|second|seconds|min|minute|minutes|hour|hours|hr|hrs|ghanta)").find(lower)
    if (timerMatch != null && (lower.contains("timer") || lower.contains("countdown") || lower.contains("laga"))) {
      val amount = timerMatch.groupValues[1].toIntOrNull() ?: 0
      val unit = timerMatch.groupValues[2]
      val seconds = when {
        unit.startsWith("sec") -> amount
        unit.startsWith("hour") || unit.startsWith("hr") || unit.startsWith("ghanta") -> amount * 3600
        else -> amount * 60
      }
      if (seconds > 0) {
        commands.add(ParsedKavyaCommand.SetTimer(seconds, "$amount $unit timer"))
      }
    }

    // 3. Check time/date query
    if (lower.contains("what time") || lower.contains("time is it") || lower.contains("kitne baje") ||
      lower.contains("samay") || lower.contains("date") || lower.contains("tarikh")
    ) {
      commands.add(ParsedKavyaCommand.GetCurrentTime)
    }

    // 4. Check reminder
    if (lower.contains("remind me") || lower.contains("reminder") || lower.contains("yaad dila")) {
      val cleanTitle = trimmed
        .replace(Regex("(?i)remind me to|remind me about|set a reminder to|set reminder|yaad dilana"), "")
        .trim()
        .ifBlank { "Task reminder" }
      commands.add(
        ParsedKavyaCommand.SetReminder(
          title = cleanTitle.take(120),
          timeText = "In 1 hour (Offline schedule)",
          delayMinutes = 60,
          hour24 = null,
          minute = null,
          dayOffset = 0,
        )
      )
    }

    // 5. Simple arithmetic offline
    val mathMatch = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\??\\s*$").find(trimmed)
    if (mathMatch != null && commands.isEmpty()) {
      val a = mathMatch.groupValues[1].toDoubleOrNull()
      val op = mathMatch.groupValues[2]
      val b = mathMatch.groupValues[3].toDoubleOrNull()
      if (a != null && b != null) {
        val result = when (op) {
          "+" -> a + b
          "-" -> a - b
          "*" -> a * b
          "/" -> if (b != 0.0) a / b else null
          else -> null
        }
        if (result != null) {
          val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else String.format(Locale.US, "%.2f", result)
          return KavyaAiResult(
            reply = "Offline calculation: $a $op $b = $formatted",
            detectedLanguage = preferredLanguage.label,
            commands = emptyList(),
          )
        }
      }
    }

    // 6. Memory recall offline
    if (lower.contains("remember") || lower.contains("memory") || lower.contains("kya yaad")) {
      val summary = if (memories.isEmpty()) {
        "Abhi mere paas koi saved memory nahi hai."
      } else {
        "Offline cache mein mujhe yeh yaad hai:\n" +
          memories.take(5).joinToString("\n") { "• ${it.label}: ${it.value}" }
      }
      return KavyaAiResult(
        reply = summary,
        detectedLanguage = preferredLanguage.label,
        commands = commands,
      )
    }

    // 7. Build response
    if (commands.isNotEmpty()) {
      val parts = mutableListOf<String>()
      commands.forEach { cmd ->
        when (cmd) {
          is ParsedKavyaCommand.OpenApp -> parts.add("${cmd.appName} open karne ki koshish kar rahi hoon!")
          is ParsedKavyaCommand.SetTimer -> parts.add("${cmd.durationSeconds / 60} min / ${cmd.durationSeconds}s ka timer start kar rahi hoon!")
          is ParsedKavyaCommand.SetReminder -> parts.add("Reminder save kar rahi hoon: \"${cmd.title}\".")
          is ParsedKavyaCommand.GetCurrentTime -> {
            val now = SimpleDateFormat("hh:mm a, EEEE dd MMM", Locale.getDefault()).format(Date())
            parts.add("Abhi time hai $now.")
          }
          else -> {}
        }
      }
      return KavyaAiResult(
        reply = "(Offline Mode) ${parts.joinToString(" ")}",
        detectedLanguage = preferredLanguage.label,
        commands = commands,
      )
    }

    // 8. Greetings / identity offline
    if (lower.matches(Regex(".*\\b(hi|hello|hey|namaste|kaise ho|who are you|kaun ho|what can you do)\\b.*"))) {
      val reply = when (preferredLanguage) {
        KavyaLanguage.Hindi ->
          "नमस्ते $displayName! मैं काव्या हूँ। अभी इंटरनेट उपलब्ध नहीं है, इसलिए मैं ऑफ़लाइन मोड में हूँ — ऐप खोलना, टाइमर, रिमाइंडर, डेली हैबिट ट्रैकिंग और सेव की गई मेमोरी अभी भी काम कर रही हैं!"
        KavyaLanguage.English ->
          "Hey $displayName! I'm Kavya. Internet is currently unavailable so I'm in Offline Mode — local app launching, timers, reminders, daily habit tracking, and saved memories still work!"
        KavyaLanguage.Hinglish ->
          "Hey $displayName! Main Kavya hoon. Abhi internet connection nahi mil raha, isliye main offline mode mein hoon — par app open karna, timer, reminder, daily habits aur saved memories abhi bhi kaam kar rahi hain!"
      }
      return KavyaAiResult(reply = reply, detectedLanguage = preferredLanguage.label, commands = emptyList())
    }

    val fallback = when (preferredLanguage) {
      KavyaLanguage.Hindi ->
        "अभी इंटरनेट कनेक्शन नहीं मिल रहा, इसलिए मैं ऑफ़लाइन मोड में हूँ। लाइव ऑनलाइन जानकारी या विस्तृत AI चैट के लिए इंटरनेट की ज़रूरत होगी, लेकिन आप ऐप खोलने, टाइमर, रिमाइंडर या डेली हैबिट्स ट्रैक कर सकते हैं।"
      KavyaLanguage.English ->
        "I'm currently in Offline Mode because there's no internet connection. Live online answers aren't available offline, but I can still open apps, start timers, save reminders, track daily habits, and check cached memories!"
      KavyaLanguage.Hinglish ->
        "Abhi internet connection nahi mil raha. Main offline mode mein continue karti hoon — live internet info abhi unavailable hai, par tum mujhse apps open karne, timer lagane, reminder set karne ya daily habits track karne ko bol sakte ho!"
    }
    return KavyaAiResult(reply = fallback, detectedLanguage = preferredLanguage.label, commands = emptyList())
  }
}
