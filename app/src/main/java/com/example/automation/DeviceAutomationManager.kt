package com.example.automation

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.model.KavyaActionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceAutomationManager(private val context: Context) {

  private data class KnownApp(
    val displayName: String,
    val packageNames: List<String>,
    val fallbackIntent: (() -> Intent)? = null,
  )

  private val knownApps = listOf(
    KnownApp("YouTube", listOf("com.google.android.youtube")),
    KnownApp("WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b")),
    KnownApp("Chrome", listOf("com.android.chrome")),
    KnownApp("Google Maps", listOf("com.google.android.apps.maps")),
    KnownApp("Maps", listOf("com.google.android.apps.maps")),
    KnownApp("Telegram", listOf("org.telegram.messenger", "org.telegram.plus")),
    KnownApp("Gmail", listOf("com.google.android.gm")),
    KnownApp("Spotify", listOf("com.spotify.music")),
    KnownApp("Instagram", listOf("com.instagram.android")),
    KnownApp("Play Store", listOf("com.android.vending")),
    KnownApp("Clock", listOf("com.google.android.deskclock")),
    KnownApp("Calendar", listOf("com.google.android.calendar")),
    KnownApp("Calculator", listOf("com.google.android.calculator")),
    KnownApp(
      "Settings",
      emptyList(),
      fallbackIntent = { Intent(Settings.ACTION_SETTINGS) },
    ),
    KnownApp(
      "Camera",
      emptyList(),
      fallbackIntent = { Intent(MediaStore.ACTION_IMAGE_CAPTURE) },
    ),
    KnownApp(
      "Browser",
      listOf("com.android.chrome"),
      fallbackIntent = { Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")) },
    ),
    KnownApp(
      "Phone",
      emptyList(),
      fallbackIntent = { Intent(Intent.ACTION_DIAL) },
    ),
    KnownApp(
      "Messages",
      emptyList(),
      fallbackIntent = { Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")) },
    ),
  )

  fun openApp(rawAppName: String): KavyaActionCard.AppLaunch {
    val query = rawAppName.trim()
    val pm = context.packageManager

    val matched = knownApps.firstOrNull { app ->
      app.displayName.equals(query, ignoreCase = true) ||
        query.contains(app.displayName, ignoreCase = true)
    }

    if (matched != null) {
      for (pkg in matched.packageNames) {
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
          return launchIntentSafely(launchIntent, matched.displayName, pkg)
        }
      }
      val fallback = matched.fallbackIntent?.invoke()
      if (fallback != null && fallback.resolveActivity(pm) != null) {
        return launchIntentSafely(fallback, matched.displayName, null)
      }
      return KavyaActionCard.AppLaunch(
        appName = matched.displayName,
        packageName = matched.packageNames.firstOrNull(),
        succeeded = false,
        detail = "${matched.displayName} is not installed or available on this device.",
      )
    }

    return KavyaActionCard.AppLaunch(
      appName = query.ifBlank { "App" },
      packageName = null,
      succeeded = false,
      detail = "Could not find \"$query\" among installed supported apps on this device.",
    )
  }

  private fun launchIntentSafely(
    intent: Intent,
    displayName: String,
    packageName: String?,
  ): KavyaActionCard.AppLaunch {
    return try {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
      KavyaActionCard.AppLaunch(
        appName = displayName,
        packageName = packageName,
        succeeded = true,
        detail = "Launched $displayName successfully.",
      )
    } catch (e: Exception) {
      KavyaActionCard.AppLaunch(
        appName = displayName,
        packageName = packageName,
        succeeded = false,
        detail = "Unable to open $displayName: ${e.localizedMessage ?: "Launch error"}",
      )
    }
  }

  fun tryStartSystemTimer(durationSeconds: Int, label: String): Boolean {
    return try {
      val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
        putExtra(AlarmClock.EXTRA_MESSAGE, label)
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
        true
      } else {
        false
      }
    } catch (_: Exception) {
      false
    }
  }

  fun scheduleReminderNotification(reminderId: String, title: String, dueAtMillis: Long): Boolean {
    return try {
      if (dueAtMillis <= System.currentTimeMillis()) return false
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val intent = Intent(context, KavyaReminderReceiver::class.java).apply {
        putExtra(KavyaReminderReceiver.EXTRA_REMINDER_TITLE, "Reminder: $title")
        putExtra(KavyaReminderReceiver.EXTRA_REMINDER_BODY, "Kavya reminder: $title")
        putExtra(KavyaReminderReceiver.EXTRA_NOTIFICATION_ID, reminderId.hashCode())
      }
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        reminderId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtMillis, pendingIntent)
      } else {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtMillis, pendingIntent)
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  fun scheduleDailyHabitReminders(
    habitId: Long,
    habitTitle: String,
    slots: List<Pair<Int, Int>>,
  ): Boolean {
    return try {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      slots.forEachIndexed { index, (hour24, minute) ->
        val cal = java.util.Calendar.getInstance().apply {
          set(java.util.Calendar.HOUR_OF_DAY, hour24.coerceIn(0, 23))
          set(java.util.Calendar.MINUTE, minute.coerceIn(0, 59))
          set(java.util.Calendar.SECOND, 0)
          if (timeInMillis <= System.currentTimeMillis()) {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
          }
        }
        val reqCode = ("habit_${habitId}_$index").hashCode()
        val intent = Intent(context, KavyaReminderReceiver::class.java).apply {
          putExtra(KavyaReminderReceiver.EXTRA_REMINDER_TITLE, "Habit Check-in: $habitTitle")
          putExtra(
            KavyaReminderReceiver.EXTRA_REMINDER_BODY,
            "Hey! \"$habitTitle\" complete karne ka time ho gaya hai!",
          )
          putExtra(KavyaReminderReceiver.EXTRA_NOTIFICATION_ID, reqCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
          context,
          reqCode,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setInexactRepeating(
          AlarmManager.RTC_WAKEUP,
          cal.timeInMillis,
          AlarmManager.INTERVAL_DAY,
          pendingIntent,
        )
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  fun sendPreparedMessage(
    platform: String,
    recipient: String,
    messageBody: String,
  ): Pair<Boolean, String> {
    val pm = context.packageManager
    val normalized = platform.trim().lowercase()
    return try {
      when {
        normalized.contains("whatsapp") -> {
          val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
            pm.getLaunchIntentForPackage(it) != null
          }
          if (pkg == null) {
            return false to "WhatsApp is not installed on this device."
          }
          val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(pkg)
            putExtra(Intent.EXTRA_TEXT, messageBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          context.startActivity(intent)
          true to "Opened WhatsApp with your message for $recipient. Tap Send in WhatsApp to finish."
        }

        normalized.contains("telegram") -> {
          val pkg = listOf("org.telegram.messenger", "org.telegram.plus").firstOrNull {
            pm.getLaunchIntentForPackage(it) != null
          }
          if (pkg == null) {
            return false to "Telegram is not installed on this device."
          }
          val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(pkg)
            putExtra(Intent.EXTRA_TEXT, messageBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          context.startActivity(intent)
          true to "Opened Telegram with your prepared message for $recipient."
        }

        normalized.contains("email") || normalized.contains("gmail") || normalized.contains("mail") -> {
          val emailUri = if (recipient.contains("@")) {
            Uri.parse("mailto:${Uri.encode(recipient)}")
          } else {
            Uri.parse("mailto:")
          }
          val intent = Intent(Intent.ACTION_SENDTO, emailUri).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Message from Kavya AI")
            putExtra(Intent.EXTRA_TEXT, messageBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          if (intent.resolveActivity(pm) == null) {
            return false to "No email app is available on this device."
          }
          context.startActivity(intent)
          true to "Opened your email composer for $recipient."
        }

        else -> {
          val digits = recipient.filter { it.isDigit() || it == '+' }
          val smsUri = if (digits.isNotBlank()) Uri.parse("smsto:$digits") else Uri.parse("smsto:")
          val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", messageBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          if (intent.resolveActivity(pm) == null) {
            return false to "No SMS messaging app is available on this device."
          }
          context.startActivity(intent)
          true to "Opened SMS composer with your prepared message."
        }
      }
    } catch (e: Exception) {
      false to "Could not launch messaging app: ${e.localizedMessage ?: "Unknown error"}"
    }
  }

  fun initiateCall(
    platform: String,
    recipient: String,
    phoneNumber: String,
    callType: String,
  ): Pair<Boolean, String> {
    val pm = context.packageManager
    val normalizedPlatform = platform.trim().lowercase()
    val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }

    return try {
      if (normalizedPlatform.contains("whatsapp") || callType.equals("video", ignoreCase = true)) {
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull {
          pm.getLaunchIntentForPackage(it) != null
        }
        if (pkg != null && normalizedPlatform.contains("whatsapp")) {
          if (cleanNumber.isNotBlank()) {
            val digitsOnly = cleanNumber.trimStart('+')
            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digitsOnly")).apply {
              setPackage(pkg)
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(waIntent)
            return true to "Opened WhatsApp chat for $recipient ($cleanNumber) to start your $callType call."
          } else {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)!!
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return true to "Opened WhatsApp so you can start your $callType call with $recipient."
          }
        }
        if (callType.equals("video", ignoreCase = true) && normalizedPlatform.contains("whatsapp")) {
          return false to "WhatsApp is not installed on this device for a WhatsApp video call."
        }
      }

      val hasCallPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CALL_PHONE,
      ) == PackageManager.PERMISSION_GRANTED

      val telUri = if (cleanNumber.isNotBlank()) Uri.parse("tel:$cleanNumber") else Uri.parse("tel:")
      if (cleanNumber.isNotBlank() && hasCallPermission) {
        val callIntent = Intent(Intent.ACTION_CALL, telUri).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (callIntent.resolveActivity(pm) != null) {
          context.startActivity(callIntent)
          return true to "Initiated phone call to $recipient ($cleanNumber)."
        }
      }

      val dialIntent = Intent(Intent.ACTION_DIAL, telUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      if (dialIntent.resolveActivity(pm) != null) {
        context.startActivity(dialIntent)
        val suffix = if (cleanNumber.isNotBlank()) " ($cleanNumber)" else ""
        true to "Opened Phone Dialer for $recipient$suffix."
      } else {
        false to "No calling or dialer app is available on this device."
      }
    } catch (e: Exception) {
      false to "Could not initiate call: ${e.localizedMessage ?: "Unknown error"}"
    }
  }

  fun hasCallPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
      PackageManager.PERMISSION_GRANTED

  fun currentTimeInfo(): KavyaActionCard.TimeReport {
    val now = Date()
    val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val dateFmt = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
    return KavyaActionCard.TimeReport(
      formattedTime = timeFmt.format(now),
      formattedDate = dateFmt.format(now),
    )
  }
}
