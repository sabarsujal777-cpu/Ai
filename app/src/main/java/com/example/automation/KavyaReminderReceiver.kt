package com.example.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

class KavyaReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val title = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Kavya Reminder"
    val body = intent.getStringExtra(EXTRA_REMINDER_BODY) ?: "Hey! Yeh reminder check karne ka time ho gaya hai."
    val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, System.currentTimeMillis().toInt())

    showKavyaNotification(context, CHANNEL_REMINDERS, "Kavya Reminders", title, body, notifId)
  }

  companion object {
    const val CHANNEL_REMINDERS = "kavya_reminders"
    const val CHANNEL_TIMERS = "kavya_timers"
    const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
    const val EXTRA_REMINDER_BODY = "extra_reminder_body"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    fun showKavyaNotification(
      context: Context,
      channelId: String,
      channelName: String,
      title: String,
      body: String,
      notificationId: Int,
    ) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          channelId,
          channelName,
          NotificationManager.IMPORTANCE_HIGH,
        ).apply {
          description = "Notifications from Kavya AI Personal Assistant"
        }
        manager.createNotificationChannel(channel)
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
      }

      val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

      try {
        NotificationManagerCompat.from(context).notify(notificationId, notification)
      } catch (_: SecurityException) {
      }
    }
  }
}
