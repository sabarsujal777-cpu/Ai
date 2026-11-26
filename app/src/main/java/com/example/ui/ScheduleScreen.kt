package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.model.ActiveTimer
import com.example.model.ReminderItem
import com.example.model.UiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KavyaMint

@Composable
fun ScheduleScreen(
  activeTimers: List<ActiveTimer>,
  remindersState: UiState<List<ReminderItem>>,
  onStartTimer: (seconds: Int, label: String) -> Unit,
  onToggleTimer: (String) -> Unit,
  onRemoveTimer: (String) -> Unit,
  onAddReminder: (title: String, dueTimeText: String, dueAtMillis: Long) -> Unit,
  onToggleReminder: (id: String, completed: Boolean) -> Unit,
  onDeleteReminder: (id: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCustomTimerDialog by remember { mutableStateOf(false) }
  var showAddReminderDialog by remember { mutableStateOf(false) }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
    ) {
      item(key = "timers_header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Countdown Timers", style = MaterialTheme.typography.headlineSmall)
            Text(
              "Start quick focus or break timers anytime",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          FilledTonalButton(
            onClick = { showCustomTimerDialog = true },
            modifier = Modifier.testTag("custom_timer_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Custom")
          }
        }
      }

      item(key = "timer_presets") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(
            60 to "1 min",
            300 to "5 min",
            600 to "10 min",
            1500 to "25 min",
          ).forEach { (secs, label) ->
            OutlinedButton(
              onClick = { onStartTimer(secs, "$label Timer") },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).testTag("preset_timer_${secs}"),
            ) {
              Text(label, style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }

      if (activeTimers.isEmpty()) {
        item(key = "timers_empty") {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
              )
              Text(
                text = "No active timers. Tap a preset above or ask Kavya: \"Set a 10-minute timer.\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(activeTimers, key = { it.id }) { timer ->
          ActiveTimerCard(
            timer = timer,
            onToggle = { onToggleTimer(timer.id) },
            onRemove = { onRemoveTimer(timer.id) },
          )
        }
      }

      item(key = "reminders_header") {
        Spacer(Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Reminders", style = MaterialTheme.typography.headlineSmall)
            Text(
              "Synced across devices with notifications",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Button(
            onClick = { showAddReminderDialog = true },
            modifier = Modifier.testTag("add_reminder_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add")
          }
        }
      }

      when (remindersState) {
        is UiState.Loading -> {
          item(key = "reminders_loading") {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
        }

        is UiState.Error -> {
          item(key = "reminders_error") {
            Text(
              text = remindersState.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }

        is UiState.Success -> {
          val list = remindersState.data
          if (list.isEmpty()) {
            item(key = "reminders_empty") {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp),
                  )
                  Text(
                    text = "No reminders yet. Tap Add or tell Kavya: \"Remind me to study at 7 PM.\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            items(list, key = { it.id }) { reminder ->
              ReminderRowCard(
                reminder = reminder,
                onToggle = { onToggleReminder(reminder.id, !reminder.completed) },
                onDelete = { onDeleteReminder(reminder.id) },
              )
            }
          }
        }
      }
    }
  }

  if (showCustomTimerDialog) {
    CustomTimerDialog(
      onDismiss = { showCustomTimerDialog = false },
      onConfirm = { minutes, label ->
        showCustomTimerDialog = false
        onStartTimer((minutes * 60).coerceAtLeast(10), label)
      },
    )
  }

  if (showAddReminderDialog) {
    AddReminderDialog(
      onDismiss = { showAddReminderDialog = false },
      onConfirm = { title, timeText, delayMins ->
        showAddReminderDialog = false
        val dueAt = System.currentTimeMillis() + delayMins * 60_000L
        onAddReminder(title, timeText, dueAt)
      },
    )
  }
}

@Composable
private fun ActiveTimerCard(
  timer: ActiveTimer,
  onToggle: () -> Unit,
  onRemove: () -> Unit,
) {
  val progress = if (timer.totalSeconds > 0) {
    timer.remainingSeconds.toFloat() / timer.totalSeconds.toFloat()
  } else 0f

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(20.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(timer.label, style = MaterialTheme.typography.titleMedium)
          Text(
            text = if (timer.completed) "Time's up!" else if (timer.running) "Running" else "Paused",
            style = MaterialTheme.typography.labelSmall,
            color = if (timer.completed) KavyaMint else MaterialTheme.colorScheme.primary,
          )
        }
        Text(
          text = KavyaViewModel.formatDuration(timer.remainingSeconds),
          fontFamily = JetBrainsMonoFontFamily,
          style = MaterialTheme.typography.headlineMedium,
          color = if (timer.completed) KavyaMint else MaterialTheme.colorScheme.onSurface,
        )
      }

      LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        if (!timer.completed) {
          IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
            Icon(
              imageVector = if (timer.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription = if (timer.running) "Pause timer" else "Resume timer",
            )
          }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Rounded.Close, contentDescription = "Remove timer")
        }
      }
    }
  }
}

@Composable
private fun ReminderRowCard(
  reminder: ReminderItem,
  onToggle: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth().testTag("reminder_card_${reminder.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(
        checked = reminder.completed,
        onCheckedChange = { onToggle() },
        modifier = Modifier.testTag("reminder_checkbox_${reminder.id}"),
      )
      Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
        Text(
          text = reminder.title,
          style = MaterialTheme.typography.titleMedium,
          textDecoration = if (reminder.completed) TextDecoration.LineThrough else TextDecoration.None,
          color = if (reminder.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
          shape = RoundedCornerShape(50),
          modifier = Modifier.padding(top = 4.dp),
        ) {
          Text(
            text = reminder.dueTimeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(48.dp).testTag("delete_reminder_${reminder.id}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Delete reminder",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun CustomTimerDialog(
  onDismiss: () -> Unit,
  onConfirm: (minutes: Int, label: String) -> Unit,
) {
  var minutesText by remember { mutableStateOf("10") }
  var labelText by remember { mutableStateOf("Study Session") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Start Custom Timer") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = labelText,
          onValueChange = { labelText = it },
          label = { Text("Label") },
          singleLine = true,
        )
        OutlinedTextField(
          value = minutesText,
          onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
          label = { Text("Minutes") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val mins = minutesText.toIntOrNull()?.coerceIn(1, 720) ?: 5
          onConfirm(mins, labelText.ifBlank { "$mins min Timer" })
        }
      ) {
        Text("Start")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun AddReminderDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, timeText: String, delayMinutes: Long) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var delayText by remember { mutableStateOf("30") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Reminder") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("What should Kavya remind you?") },
          singleLine = true,
          modifier = Modifier.testTag("new_reminder_title_input"),
        )
        OutlinedTextField(
          value = delayText,
          onValueChange = { delayText = it.filter { ch -> ch.isDigit() } },
          label = { Text("Remind in (minutes)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.testTag("new_reminder_minutes_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val mins = delayText.toLongOrNull()?.coerceIn(1L, 10080L) ?: 30L
          val cleanTitle = title.trim()
          if (cleanTitle.isNotEmpty()) {
            onConfirm(cleanTitle, "In $mins min", mins)
          }
        },
        enabled = title.isNotBlank(),
        modifier = Modifier.testTag("save_reminder_confirm_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
