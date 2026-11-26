package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ConfirmationStatus
import com.example.model.KavyaActionCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KavyaAmber
import com.example.ui.theme.KavyaCoral
import com.example.ui.theme.KavyaMint

@Composable
fun KavyaTopHeader(
  isOnline: Boolean,
  isSpeaking: Boolean,
  preferredLanguage: String,
  onStopSpeaking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val statusColor by animateColorAsState(
    targetValue = if (isOnline) KavyaMint else KavyaAmber,
    label = "connectivity_color",
  )

  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    tonalElevation = 2.dp,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box {
          Image(
            painter = painterResource(id = R.drawable.img_kavya_avatar),
            contentDescription = "Kavya avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .border(2.dp, KavyaCoral, CircleShape),
          )
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .size(12.dp)
              .clip(CircleShape)
              .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
              .let { mod ->
                mod.padding(0.dp)
              },
          ) {
            Surface(color = statusColor, modifier = Modifier.size(12.dp), shape = CircleShape) {}
          }
        }

        Column {
          Text(
            text = "Kavya AI",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = if (isOnline) "Online • $preferredLanguage" else "Offline Mode • Local Skills",
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (isSpeaking) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(50),
            onClick = onStopSpeaking,
            modifier = Modifier.testTag("stop_speaking_pill"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.VolumeOff,
                contentDescription = "Stop Kavya speaking",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Mute",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }

        Surface(
          color = statusColor.copy(alpha = 0.14f),
          shape = RoundedCornerShape(50),
          modifier = Modifier.testTag("connectivity_status_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Icon(
              imageVector = if (isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
              contentDescription = if (isOnline) "Online mode" else "Offline mode",
              tint = statusColor,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = if (isOnline) "Online" else "Offline",
              style = MaterialTheme.typography.labelSmall,
              color = statusColor,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

@Composable
fun ActionResultCard(
  messageId: String,
  card: KavyaActionCard,
  onConfirmMessage: (messageId: String, actionId: String, body: String) -> Unit,
  onCancelMessage: (messageId: String, actionId: String) -> Unit,
  onConfirmCall: (messageId: String, actionId: String, phone: String) -> Unit,
  onCancelCall: (messageId: String, actionId: String) -> Unit,
  onPickContactForCall: (messageId: String, actionId: String) -> Unit,
  onEnableHabitReminders: (messageId: String, habitId: Long, habitTitle: String) -> Unit = { _, _, _ -> },
) {
  when (card) {
    is KavyaActionCard.AppLaunch -> {
      StatusActionBanner(
        icon = Icons.AutoMirrored.Rounded.Launch,
        title = "App Control • ${card.appName}",
        subtitle = card.detail,
        succeeded = card.succeeded,
      )
    }

    is KavyaActionCard.TimerStarted -> {
      StatusActionBanner(
        icon = Icons.Rounded.Timer,
        title = "Timer • ${card.label}",
        subtitle = card.detail,
        succeeded = card.succeeded,
      )
    }

    is KavyaActionCard.ReminderSaved -> {
      StatusActionBanner(
        icon = Icons.Rounded.Alarm,
        title = "Reminder • ${card.title}",
        subtitle = card.detail,
        succeeded = card.succeeded,
      )
    }

    is KavyaActionCard.MemoryUpdate -> {
      StatusActionBanner(
        icon = Icons.Rounded.Psychology,
        title = "Memory ${card.operation} • ${card.label}",
        subtitle = if (card.value.isNotBlank()) "${card.value} (${card.detail})" else card.detail,
        succeeded = card.succeeded,
      )
    }

    is KavyaActionCard.TimeReport -> {
      StatusActionBanner(
        icon = Icons.Rounded.AccessTime,
        title = card.formattedTime,
        subtitle = card.formattedDate,
        succeeded = true,
      )
    }

    is KavyaActionCard.PendingMessage -> {
      PendingMessageConfirmationCard(
        card = card,
        onConfirm = { body -> onConfirmMessage(messageId, card.actionId, body) },
        onCancel = { onCancelMessage(messageId, card.actionId) },
      )
    }

    is KavyaActionCard.PendingCall -> {
      PendingCallConfirmationCard(
        card = card,
        onConfirm = { phone -> onConfirmCall(messageId, card.actionId, phone) },
        onCancel = { onCancelCall(messageId, card.actionId) },
        onPickContact = { onPickContactForCall(messageId, card.actionId) },
      )
    }

    is KavyaActionCard.HabitSaved -> {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, KavyaMint.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
          .testTag("habit_saved_card_${card.habitId}"),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              color = KavyaMint.copy(alpha = 0.14f),
              shape = CircleShape,
              modifier = Modifier.size(36.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.TaskAlt,
                  contentDescription = null,
                  tint = KavyaMint,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Local Habit • ${card.title}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = card.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = "Saved locally",
              tint = KavyaMint,
              modifier = Modifier.size(20.dp),
            )
          }
          if (card.reminderOfferPending) {
            OutlinedButton(
              onClick = { onEnableHabitReminders(messageId, card.habitId, card.title) },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("enable_habit_reminders_${card.habitId}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text("Remind me throughout the day (9 AM, 2 PM, 8 PM)")
            }
          }
        }
      }
    }

    is KavyaActionCard.HabitCompleted -> {
      StatusActionBanner(
        icon = Icons.Rounded.LocalFireDepartment,
        title = "Habit Done • ${card.title}",
        subtitle = card.detail,
        succeeded = card.succeeded,
      )
    }

    is KavyaActionCard.HabitRemindersScheduled -> {
      StatusActionBanner(
        icon = Icons.Rounded.NotificationsActive,
        title = "Daily Habit Reminders",
        subtitle = card.detail,
        succeeded = card.succeeded,
      )
    }
  }
}

@Composable
private fun StatusActionBanner(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  succeeded: Boolean,
) {
  val accent = if (succeeded) KavyaMint else MaterialTheme.colorScheme.error
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface,
    ),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        color = accent.copy(alpha = 0.14f),
        shape = CircleShape,
        modifier = Modifier.size(36.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = if (succeeded) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
        contentDescription = if (succeeded) "Success" else "Unavailable",
        tint = accent,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun PendingMessageConfirmationCard(
  card: KavyaActionCard.PendingMessage,
  onConfirm: (String) -> Unit,
  onCancel: () -> Unit,
) {
  var editableBody by remember(card.actionId, card.messageBody) { mutableStateOf(card.messageBody) }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        1.5.dp,
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
        RoundedCornerShape(18.dp),
      )
      .testTag("pending_message_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "${card.platform} Message • To ${card.recipient}",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.secondary,
        )
        StatusChip(card.status)
      }

      if (card.status == ConfirmationStatus.PENDING) {
        OutlinedTextField(
          value = editableBody,
          onValueChange = { editableBody = it },
          label = { Text("Message preview (editable)") },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("pending_message_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).testTag("cancel_message_button"),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Cancel")
          }
          Button(
            onClick = { onConfirm(editableBody) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).testTag("confirm_send_message_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Confirm & Send")
          }
        }
      } else {
        Text(
          text = "\"${card.messageBody}\"",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = card.statusDetail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun PendingCallConfirmationCard(
  card: KavyaActionCard.PendingCall,
  onConfirm: (String) -> Unit,
  onCancel: () -> Unit,
  onPickContact: () -> Unit,
) {
  var phoneText by remember(card.actionId, card.phoneNumber) { mutableStateOf(card.phoneNumber) }
  val isVideo = card.callType.equals("video", ignoreCase = true)

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        1.5.dp,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        RoundedCornerShape(18.dp),
      )
      .testTag("pending_call_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "${card.platform} ${card.callType.replaceFirstChar { it.uppercaseChar() }} Call • ${card.recipient}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        StatusChip(card.status)
      }

      if (card.status == ConfirmationStatus.PENDING) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = phoneText,
            onValueChange = { phoneText = it },
            label = { Text("Phone number (optional)") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).testTag("pending_call_phone_input"),
          )
          IconButton(
            onClick = onPickContact,
            modifier = Modifier.size(48.dp).testTag("pick_contact_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Contacts,
              contentDescription = "Pick saved contact",
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).testTag("cancel_call_button"),
          ) {
            Text("Cancel")
          }
          Button(
            onClick = { onConfirm(phoneText) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).testTag("confirm_call_button"),
          ) {
            Text("Confirm Call")
          }
        }
      } else {
        Text(
          text = card.statusDetail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun StatusChip(status: ConfirmationStatus) {
  val (label, color) = when (status) {
    ConfirmationStatus.PENDING -> "Confirm?" to KavyaAmber
    ConfirmationStatus.CONFIRMED -> "Done" to KavyaMint
    ConfirmationStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
    ConfirmationStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
  }
  Surface(
    color = color.copy(alpha = 0.16f),
    shape = RoundedCornerShape(50),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun SpeakBubbleButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(
    onClick = onClick,
    modifier = modifier.size(36.dp),
  ) {
    Icon(
      imageVector = Icons.Rounded.VolumeUp,
      contentDescription = "Read Kavya reply aloud",
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
fun ActiveTimerMiniStrip(
  label: String,
  remainingSeconds: Int,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .testTag("active_timer_strip"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Timer,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
      Text(
        text = KavyaViewModel.formatDuration(remainingSeconds),
        fontFamily = JetBrainsMonoFontFamily,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
