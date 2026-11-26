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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.habits.HabitEntity
import com.example.habits.HabitRepository
import com.example.ui.theme.KavyaAmber
import com.example.ui.theme.KavyaMint

@Composable
fun HabitsScreen(
  habits: List<HabitEntity>,
  onToggleHabitToday: (HabitEntity) -> Unit,
  onAddHabit: (title: String, enableReminders: Boolean, slots: List<String>) -> Unit,
  onUpdateHabitReminders: (habit: HabitEntity, enabled: Boolean, slots: List<String>) -> Unit,
  onDeleteHabit: (Long) -> Unit,
  onAskKavyaToTrackHabits: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val todayKey = remember { HabitRepository.todayDateKey() }
  val completedCount = habits.count { it.lastCompletedDate == todayKey }
  var showAddDialog by remember { mutableStateOf(false) }
  var reminderEditTarget by remember { mutableStateOf<HabitEntity?>(null) }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
    ) {
      item(key = "habits_progress_card") {
        val progress = if (habits.isNotEmpty()) completedCount.toFloat() / habits.size.toFloat() else 0f
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)),
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.fillMaxWidth().testTag("habits_progress_card"),
        ) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Daily Habits",
                  style = MaterialTheme.typography.headlineSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                  text = if (habits.isEmpty()) "Ready to build your routine" else "$completedCount of ${habits.size} completed today",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(50),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = KavyaMint,
                    modifier = Modifier.size(15.dp),
                  )
                  Text(
                    text = "Local Record",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }

            LinearProgressIndicator(
              progress = { progress.coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth().height(8.dp),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = onAskKavyaToTrackHabits,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).testTag("ask_kavya_habits_button"),
              ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ask Kavya", maxLines = 1)
              }
              Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).testTag("add_habit_button"),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Habit", maxLines = 1)
              }
            }
          }
        }
      }

      if (habits.isEmpty()) {
        item(key = "habits_empty") {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.TaskAlt,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
                )
                Text(
                  text = "No daily habits tracked yet",
                  style = MaterialTheme.typography.titleMedium,
                )
              }
              Text(
                text = "Tap \"Ask Kavya\" or tell her in chat: \"Track my daily habits\" — she'll ask what tasks you want to monitor, save them locally on your device, and offer to remind you throughout the day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(habits, key = { it.id }) { habit ->
          val doneToday = habit.lastCompletedDate == todayKey
          HabitRowCard(
            habit = habit,
            doneToday = doneToday,
            onToggle = { onToggleHabitToday(habit) },
            onConfigureReminders = { reminderEditTarget = habit },
            onDelete = { onDeleteHabit(habit.id) },
          )
        }
      }
    }
  }

  if (showAddDialog) {
    AddHabitDialog(
      onDismiss = { showAddDialog = false },
      onSave = { title, enableReminders, slots ->
        showAddDialog = false
        onAddHabit(title, enableReminders, slots)
      },
    )
  }

  val targetHabit = reminderEditTarget
  if (targetHabit != null) {
    HabitReminderScheduleDialog(
      habit = targetHabit,
      onDismiss = { reminderEditTarget = null },
      onSave = { enabled, slots ->
        reminderEditTarget = null
        onUpdateHabitReminders(targetHabit, enabled, slots)
      },
    )
  }
}

@Composable
private fun HabitRowCard(
  habit: HabitEntity,
  doneToday: Boolean,
  onToggle: () -> Unit,
  onConfigureReminders: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(20.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth().testTag("habit_card_${habit.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(
        checked = doneToday,
        onCheckedChange = { onToggle() },
        modifier = Modifier.testTag("habit_checkbox_${habit.id}"),
      )

      Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
        Text(
          text = habit.title,
          style = MaterialTheme.typography.titleMedium,
          textDecoration = if (doneToday) TextDecoration.LineThrough else TextDecoration.None,
          color = if (doneToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            color = KavyaAmber.copy(alpha = 0.16f),
            shape = RoundedCornerShape(50),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = KavyaAmber,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "${habit.currentStreak}d streak",
                style = MaterialTheme.typography.labelSmall,
                color = KavyaAmber,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          Surface(
            color = if (habit.reminderEnabled) {
              KavyaMint.copy(alpha = 0.15f)
            } else {
              MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(50),
            onClick = onConfigureReminders,
          ) {
            Text(
              text = if (habit.reminderEnabled && habit.reminderSlots.isNotBlank()) {
                habit.reminderSlots
              } else {
                "Set reminders"
              },
              style = MaterialTheme.typography.labelSmall,
              color = if (habit.reminderEnabled) KavyaMint else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }

      IconButton(
        onClick = onConfigureReminders,
        modifier = Modifier.size(48.dp).testTag("habit_bell_${habit.id}"),
      ) {
        Icon(
          imageVector = if (habit.reminderEnabled) {
            Icons.Rounded.NotificationsActive
          } else {
            Icons.Rounded.NotificationsNone
          },
          contentDescription = "Configure daily habit reminders",
          tint = if (habit.reminderEnabled) KavyaMint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(48.dp).testTag("delete_habit_${habit.id}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Delete habit",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun AddHabitDialog(
  onDismiss: () -> Unit,
  onSave: (title: String, enableReminders: Boolean, slots: List<String>) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var enableReminders by remember { mutableStateOf(true) }
  var morning by remember { mutableStateOf(true) }
  var afternoon by remember { mutableStateOf(true) }
  var evening by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Daily Habit") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Task to monitor (e.g., Drink 8 glasses of water)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("habit_title_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Remind me throughout the day", style = MaterialTheme.typography.bodyMedium)
          Switch(checked = enableReminders, onCheckedChange = { enableReminders = it })
        }
        if (enableReminders) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
              selected = morning,
              onClick = { morning = !morning },
              label = { Text("9 AM") },
            )
            FilterChip(
              selected = afternoon,
              onClick = { afternoon = !afternoon },
              label = { Text("2 PM") },
            )
            FilterChip(
              selected = evening,
              onClick = { evening = !evening },
              label = { Text("8 PM") },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val slots = buildList {
            if (morning) add("09:00")
            if (afternoon) add("14:00")
            if (evening) add("20:00")
          }.ifEmpty { listOf("09:00", "20:00") }
          onSave(title.trim(), enableReminders, slots)
        },
        enabled = title.isNotBlank(),
        modifier = Modifier.testTag("save_habit_confirm_button"),
      ) {
        Text("Save Locally")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun HabitReminderScheduleDialog(
  habit: HabitEntity,
  onDismiss: () -> Unit,
  onSave: (enabled: Boolean, slots: List<String>) -> Unit,
) {
  var enabled by remember { mutableStateOf(true) }
  var morning by remember { mutableStateOf(habit.reminderSlots.contains("09:00") || !habit.reminderEnabled) }
  var afternoon by remember { mutableStateOf(habit.reminderSlots.contains("14:00") || !habit.reminderEnabled) }
  var evening by remember { mutableStateOf(habit.reminderSlots.contains("20:00") || !habit.reminderEnabled) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Reminders • ${habit.title}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Daily check-in notifications", style = MaterialTheme.typography.bodyMedium)
          Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        if (enabled) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = morning,
              onClick = { morning = !morning },
              label = { Text("09:00 AM") },
            )
            FilterChip(
              selected = afternoon,
              onClick = { afternoon = !afternoon },
              label = { Text("02:00 PM") },
            )
            FilterChip(
              selected = evening,
              onClick = { evening = !evening },
              label = { Text("08:00 PM") },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val slots = buildList {
            if (morning) add("09:00")
            if (afternoon) add("14:00")
            if (evening) add("20:00")
          }.ifEmpty { listOf("09:00", "20:00") }
          onSave(enabled, slots)
        },
      ) {
        Text("Update")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
