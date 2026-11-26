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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.KavyaLanguage
import com.example.model.MemoryCategory
import com.example.model.MemoryItem
import com.example.model.UiState
import com.example.model.UserPreference
import com.example.ui.theme.KavyaMint
import java.util.Locale

@Composable
fun MemoryScreen(
  preferencesState: UiState<UserPreference>,
  memoriesState: UiState<List<MemoryItem>>,
  selectedVoiceLabel: String,
  userEmail: String,
  onSavePreferences: (
    displayName: String,
    language: KavyaLanguage,
    speechSpeed: Float,
    autoSpeak: Boolean,
    memoryEnabled: Boolean,
  ) -> Unit,
  onTestVoice: (KavyaLanguage) -> Unit,
  onAddMemory: (MemoryCategory, String, String) -> Unit,
  onUpdateMemory: (String, MemoryCategory, String, String) -> Unit,
  onDeleteMemory: (String) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showMemoryDialog by remember { mutableStateOf(false) }
  var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
    ) {
      item(key = "voice_prefs_card") {
        val pref = (preferencesState as? UiState.Success)?.data
        if (pref == null) {
          Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        } else {
          VoiceAndLanguageCard(
            pref = pref,
            selectedVoiceLabel = selectedVoiceLabel,
            onSavePreferences = onSavePreferences,
            onTestVoice = onTestVoice,
          )
        }
      }

      item(key = "memory_header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Memory Vault", style = MaterialTheme.typography.headlineSmall)
            Text(
              "Preferences, ongoing projects, contacts & notes Kavya remembers",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Button(
            onClick = {
              editingMemory = null
              showMemoryDialog = true
            },
            modifier = Modifier.testTag("add_memory_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add")
          }
        }
      }

      item(key = "privacy_notice") {
        Surface(
          color = KavyaMint.copy(alpha = 0.12f),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Shield,
              contentDescription = null,
              tint = KavyaMint,
            )
            Text(
              text = "Privacy Guard active: Kavya never stores passwords, OTPs, PINs, or sensitive financial info.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      when (memoriesState) {
        is UiState.Loading -> {
          item(key = "memories_loading") {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
        }

        is UiState.Error -> {
          item(key = "memories_error") {
            Text(
              text = memoriesState.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }

        is UiState.Success -> {
          val memories = memoriesState.data
          if (memories.isEmpty()) {
            item(key = "memories_empty") {
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
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = "No memories stored yet. Tell Kavya: \"Remember I'm working on my physics project\" or tap Add.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            items(memories, key = { it.id }) { mem ->
              MemoryItemCard(
                item = mem,
                onEdit = {
                  editingMemory = mem
                  showMemoryDialog = true
                },
                onDelete = { onDeleteMemory(mem.id) },
              )
            }
          }
        }
      }

      item(key = "account_card") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Signed in with Google", style = MaterialTheme.typography.titleSmall)
              Text(
                text = userEmail.ifBlank { "Google Account" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(
              onClick = onSignOut,
              modifier = Modifier.testTag("sign_out_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text("Sign Out")
            }
          }
        }
      }
    }
  }

  if (showMemoryDialog) {
    MemoryEditorDialog(
      existing = editingMemory,
      onDismiss = { showMemoryDialog = false },
      onSave = { cat, label, value ->
        val current = editingMemory
        showMemoryDialog = false
        if (current == null) {
          onAddMemory(cat, label, value)
        } else {
          onUpdateMemory(current.id, cat, label, value)
        }
      },
    )
  }
}

@Composable
private fun VoiceAndLanguageCard(
  pref: UserPreference,
  selectedVoiceLabel: String,
  onSavePreferences: (String, KavyaLanguage, Float, Boolean, Boolean) -> Unit,
  onTestVoice: (KavyaLanguage) -> Unit,
) {
  var displayName by remember(pref.displayName) { mutableStateOf(pref.displayName) }
  var language by remember(pref.preferredLanguage) { mutableStateOf(KavyaLanguage.fromRaw(pref.preferredLanguage)) }
  var speed by remember(pref.speechSpeed) { mutableFloatStateOf(pref.speechSpeed.toFloat()) }
  var autoSpeak by remember(pref.autoSpeak) { mutableStateOf(pref.autoSpeak) }
  var memoryEnabled by remember(pref.memoryEnabled) { mutableStateOf(pref.memoryEnabled) }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(22.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
          Text("Voice & Language", style = MaterialTheme.typography.titleLarge)
          Text(
            text = "Voice: $selectedVoiceLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        label = { Text("What should Kavya call you?") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("display_name_input"),
      )

      Text("Preferred Language", style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KavyaLanguage.entries.forEach { lang ->
          FilterChip(
            selected = language == lang,
            onClick = {
              language = lang
              onSavePreferences(displayName, lang, speed, autoSpeak, memoryEnabled)
            },
            label = { Text(lang.label) },
            modifier = Modifier.testTag("lang_chip_${lang.name}"),
          )
        }
      }

      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Speaking Speed", style = MaterialTheme.typography.labelLarge)
          Text(
            text = String.format(Locale.US, "%.2fx", speed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = speed,
          onValueChange = { speed = it },
          onValueChangeFinished = {
            onSavePreferences(displayName, language, speed, autoSpeak, memoryEnabled)
          },
          valueRange = 0.7f..1.5f,
          modifier = Modifier.testTag("speech_speed_slider"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Auto-Speak Replies", style = MaterialTheme.typography.titleSmall)
          Text(
            "Kavya reads replies aloud automatically",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = autoSpeak,
          onCheckedChange = {
            autoSpeak = it
            onSavePreferences(displayName, language, speed, it, memoryEnabled)
          },
          modifier = Modifier.testTag("auto_speak_switch"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Persistent Memory", style = MaterialTheme.typography.titleSmall)
          Text(
            "Allow Kavya to remember helpful preferences",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = memoryEnabled,
          onCheckedChange = {
            memoryEnabled = it
            onSavePreferences(displayName, language, speed, autoSpeak, it)
          },
          modifier = Modifier.testTag("memory_enabled_switch"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = { onTestVoice(language) },
          modifier = Modifier.weight(1f).testTag("test_voice_button"),
        ) {
          Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Test Voice")
        }
        Button(
          onClick = { onSavePreferences(displayName, language, speed, autoSpeak, memoryEnabled) },
          modifier = Modifier.weight(1f).testTag("save_preferences_button"),
        ) {
          Text("Save Name")
        }
      }
    }
  }
}

@Composable
private fun MemoryItemCard(
  item: MemoryItem,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val cat = MemoryCategory.fromRaw(item.category)
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth().testTag("memory_card_${item.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = cat.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(item.label, style = MaterialTheme.typography.titleMedium)
        Text(
          text = item.value,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Rounded.Edit, contentDescription = "Edit memory")
      }
      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(48.dp).testTag("delete_memory_${item.id}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Delete memory",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun MemoryEditorDialog(
  existing: MemoryItem?,
  onDismiss: () -> Unit,
  onSave: (MemoryCategory, String, String) -> Unit,
) {
  var category by remember { mutableStateOf(MemoryCategory.fromRaw(existing?.category)) }
  var label by remember { mutableStateOf(existing?.label.orEmpty()) }
  var value by remember { mutableStateOf(existing?.value.orEmpty()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "Add Memory" else "Edit Memory") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          MemoryCategory.entries.take(3).forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat.displayLabel, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          MemoryCategory.entries.drop(3).forEach { cat ->
            FilterChip(
              selected = category == cat,
              onClick = { category = cat },
              label = { Text(cat.displayLabel, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }
        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("Title / Key (e.g., Rahul, Coding Project)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("memory_label_input"),
        )
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          label = { Text("Details") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth().testTag("memory_value_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(category, label, value) },
        enabled = label.isNotBlank() && value.isNotBlank(),
        modifier = Modifier.testTag("save_memory_confirm_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
