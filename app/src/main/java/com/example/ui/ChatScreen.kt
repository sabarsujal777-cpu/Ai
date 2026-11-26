package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ActiveTimer
import com.example.model.ChatUiMessage
import com.example.model.UiState
import com.example.ui.theme.KavyaCoral
import com.example.ui.theme.KavyaIndigoDeep

@Composable
fun ChatScreen(
  chatState: UiState<List<ChatUiMessage>>,
  localGreeting: String,
  isOnline: Boolean,
  isThinking: Boolean,
  isListening: Boolean,
  partialTranscript: String,
  activeTimers: List<ActiveTimer>,
  onSendMessage: (String) -> Unit,
  onMicClick: () -> Unit,
  onSpeakText: (String) -> Unit,
  onClearChat: () -> Unit,
  onOpenScheduleTab: () -> Unit,
  onConfirmMessage: (messageId: String, actionId: String, body: String) -> Unit,
  onCancelMessage: (messageId: String, actionId: String) -> Unit,
  onConfirmCall: (messageId: String, actionId: String, phone: String) -> Unit,
  onCancelCall: (messageId: String, actionId: String) -> Unit,
  onPickContactForCall: (messageId: String, actionId: String) -> Unit,
  onEnableHabitReminders: (messageId: String, habitId: Long, habitTitle: String) -> Unit = { _, _, _ -> },
  modifier: Modifier = Modifier,
) {
  var inputText by remember { mutableStateOf("") }
  val listState = rememberLazyListState()

  val messages = (chatState as? UiState.Success)?.data.orEmpty()

  LaunchedEffect(messages.size, isThinking) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val firstRunningTimer = activeTimers.firstOrNull { !it.completed }
    if (firstRunningTimer != null) {
      ActiveTimerMiniStrip(
        label = firstRunningTimer.label,
        remainingSeconds = firstRunningTimer.remainingSeconds,
        onClick = onOpenScheduleTab,
      )
    }

    Box(
      modifier = Modifier
        .weight(1f)
        .widthIn(max = 760.dp)
        .fillMaxWidth(),
    ) {
      when (chatState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }

        is UiState.Error -> {
          Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = chatState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }

        is UiState.Success -> {
          LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            if (messages.isEmpty()) {
              item(key = "welcome_card") {
                KavyaGreetingHeroCard(
                  greeting = localGreeting,
                  isOnline = isOnline,
                  onSpeak = { onSpeakText(localGreeting) },
                )
              }
            } else {
              item(key = "clear_row") {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                ) {
                  TextButton(
                    onClick = onClearChat,
                    modifier = Modifier.testTag("new_chat_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.DeleteSweep,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("New Chat", style = MaterialTheme.typography.labelMedium)
                  }
                }
              }
            }

            items(messages, key = { it.id }) { msg ->
              ChatMessageItem(
                message = msg,
                onSpeakText = onSpeakText,
                onConfirmMessage = onConfirmMessage,
                onCancelMessage = onCancelMessage,
                onConfirmCall = onConfirmCall,
                onCancelCall = onCancelCall,
                onPickContactForCall = onPickContactForCall,
                onEnableHabitReminders = onEnableHabitReminders,
              )
            }

            if (isThinking) {
              item(key = "thinking_indicator") {
                KavyaThinkingBubble(isOnline = isOnline)
              }
            }
          }
        }
      }
    }

    // Quick suggestion chips
    QuickCommandChipsRow(
      onChipClick = { suggestion ->
        onSendMessage(suggestion)
      },
      modifier = Modifier.widthIn(max = 760.dp),
    )

    // Voice listening banner
    AnimatedVisibility(visible = isListening) {
      VoiceListeningBanner(
        partialTranscript = partialTranscript,
        onStop = onMicClick,
        modifier = Modifier.widthIn(max = 760.dp),
      )
    }

    // Bottom composer
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 3.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier
          .widthIn(max = 760.dp)
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          placeholder = {
            Text(
              text = if (isOnline) "Ask Kavya in Hindi, English, or Hinglish..." else "Offline: open app, timer, reminder...",
              maxLines = 1,
            )
          },
          maxLines = 4,
          shape = RoundedCornerShape(24.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
          ),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(
            onSend = {
              if (inputText.isNotBlank() && !isThinking) {
                val msg = inputText
                inputText = ""
                onSendMessage(msg)
              }
            }
          ),
          modifier = Modifier.weight(1f).testTag("chat_input_field"),
        )

        val micScale = if (isListening) {
          val transition = rememberInfiniteTransition(label = "mic_pulse")
          transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.14f,
            animationSpec = infiniteRepeatable(
              animation = tween(550),
              repeatMode = RepeatMode.Reverse,
            ),
            label = "mic_scale",
          ).value
        } else {
          1.0f
        }

        FilledIconButton(
          onClick = onMicClick,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
          ),
          modifier = Modifier
            .size(48.dp)
            .scale(micScale)
            .testTag("voice_mic_button"),
        ) {
          Icon(
            imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = if (isListening) "Stop listening" else "Speak to Kavya",
          )
        }

        FilledIconButton(
          onClick = {
            if (inputText.isNotBlank() && !isThinking) {
              val msg = inputText
              inputText = ""
              onSendMessage(msg)
            }
          },
          enabled = inputText.isNotBlank() && !isThinking,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          modifier = Modifier.size(48.dp).testTag("send_message_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Send,
            contentDescription = "Send message",
          )
        }
      }
    }
  }
}

@Composable
private fun KavyaGreetingHeroCard(
  greeting: String,
  isOnline: Boolean,
  onSpeak: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
    modifier = Modifier.fillMaxWidth().testTag("kavya_greeting_card"),
  ) {
    Column {
      Box(modifier = Modifier.fillMaxWidth().height(126.dp)) {
        Image(
          painter = painterResource(id = R.drawable.img_kavya_hero_banner),
          contentDescription = "Kavya twilight banner",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.horizontalGradient(
                listOf(
                  KavyaIndigoDeep.copy(alpha = 0.85f),
                  KavyaIndigoDeep.copy(alpha = 0.35f),
                )
              )
            )
        )
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_kavya_avatar),
            contentDescription = "Kavya character portrait",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(76.dp)
              .clip(CircleShape)
              .border(2.5.dp, KavyaCoral, CircleShape),
          )
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = KavyaCoral,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = if (isOnline) "Kavya Online" else "Kavya Offline",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f),
              )
            }
            Spacer(Modifier.height(4.dp))
            Text(
              text = "Your Personal AI Companion",
              style = MaterialTheme.typography.titleLarge,
              color = Color.White,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = greeting,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
        SpeakBubbleButton(onClick = onSpeak, modifier = Modifier.testTag("speak_greeting_button"))
      }
    }
  }
}

@Composable
private fun ChatMessageItem(
  message: ChatUiMessage,
  onSpeakText: (String) -> Unit,
  onConfirmMessage: (messageId: String, actionId: String, body: String) -> Unit,
  onCancelMessage: (messageId: String, actionId: String) -> Unit,
  onConfirmCall: (messageId: String, actionId: String, phone: String) -> Unit,
  onCancelCall: (messageId: String, actionId: String) -> Unit,
  onPickContactForCall: (messageId: String, actionId: String) -> Unit,
  onEnableHabitReminders: (messageId: String, habitId: Long, habitTitle: String) -> Unit,
) {
  val isUser = message.sender == "user"
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Bottom,
      modifier = Modifier.widthIn(max = 560.dp),
    ) {
      if (!isUser) {
        Image(
          painter = painterResource(id = R.drawable.img_kavya_avatar),
          contentDescription = "Kavya avatar",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.5.dp, KavyaCoral, CircleShape),
        )
      }

      Surface(
        color = if (isUser) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        },
        shape = RoundedCornerShape(
          topStart = 20.dp,
          topEnd = 20.dp,
          bottomStart = if (isUser) 20.dp else 6.dp,
          bottomEnd = if (isUser) 6.dp else 20.dp,
        ),
        tonalElevation = if (isUser) 0.dp else 2.dp,
        modifier = Modifier.weight(1f, fill = false),
      ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
          Text(
            text = message.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(4.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = buildString {
                append(message.timestampText)
                if (!isUser && message.mode == "offline") append(" • Offline")
              },
              style = MaterialTheme.typography.labelSmall,
              color = if (isUser) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
            )
            if (!isUser) {
              Spacer(Modifier.width(8.dp))
              SpeakBubbleButton(
                onClick = { onSpeakText(message.text) },
                modifier = Modifier.testTag("speak_message_${message.id}"),
              )
            }
          }
        }
      }
    }

    if (message.actionCards.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .padding(start = if (isUser) 0.dp else 40.dp)
          .widthIn(max = 520.dp)
          .fillMaxWidth(),
      ) {
        message.actionCards.forEach { card ->
          ActionResultCard(
            messageId = message.id,
            card = card,
            onConfirmMessage = onConfirmMessage,
            onCancelMessage = onCancelMessage,
            onConfirmCall = onConfirmCall,
            onCancelCall = onCancelCall,
            onPickContactForCall = onPickContactForCall,
            onEnableHabitReminders = onEnableHabitReminders,
          )
        }
      }
    } else if (!isUser && !message.actionSummary.isNullOrBlank()) {
      Spacer(Modifier.height(4.dp))
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(start = 40.dp),
      ) {
        Text(
          text = message.actionSummary,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun KavyaThinkingBubble(isOnline: Boolean) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.padding(vertical = 4.dp),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_kavya_avatar),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.size(30.dp).clip(CircleShape),
    )
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(18.dp),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
          text = if (isOnline) "Kavya soch rahi hai..." else "Processing locally...",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun QuickCommandChipsRow(
  onChipClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val suggestions = listOf(
    "Track my daily habits",
    "Open YouTube",
    "5 min timer laga do",
    "Remind me to study at 7 PM",
    "Message Rahul on WhatsApp: Hey, kal milte hain?",
    "What time is it?",
  )
  Row(
    modifier = modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    suggestions.forEachIndexed { idx, prompt ->
      AssistChip(
        onClick = { onChipClick(prompt) },
        label = { Text(prompt, style = MaterialTheme.typography.labelMedium) },
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag("suggestion_chip_$idx"),
      )
    }
  }
}

@Composable
private fun VoiceListeningBanner(
  partialTranscript: String,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    shape = RoundedCornerShape(16.dp),
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 4.dp)
      .testTag("voice_listening_banner"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Rounded.GraphicEq,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = partialTranscript.ifBlank { "Sun rahi hoon... Bolo!" },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Rounded.Stop, contentDescription = "Stop listening")
      }
    }
  }
}
