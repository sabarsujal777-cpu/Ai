package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.KavyaAiEngine
import com.example.ai.OfflineAssistantEngine
import com.example.ai.ParsedKavyaCommand
import com.example.automation.DeviceAutomationManager
import com.example.automation.KavyaVoiceManager
import com.example.data.ConnectivityObserver
import com.example.data.KavyaRepository
import com.example.habits.HabitEntity
import com.example.habits.HabitRepository
import com.example.model.ActiveTimer
import com.example.model.ChatMessageEntity
import com.example.model.ChatUiMessage
import com.example.model.ConfirmationStatus
import com.example.model.KavyaActionCard
import com.example.model.KavyaLanguage
import com.example.model.MemoryCategory
import com.example.model.MemoryItem
import com.example.model.ReminderItem
import com.example.model.UiState
import com.example.model.UserPreference
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class KavyaViewModel(
  private val repository: KavyaRepository,
  private val habitRepository: HabitRepository,
  private val aiEngine: KavyaAiEngine,
  private val offlineEngine: OfflineAssistantEngine,
  private val automationManager: DeviceAutomationManager,
  private val voiceManager: KavyaVoiceManager,
  private val connectivityObserver: ConnectivityObserver,
  private val currentUserId: String,
  private val initialDisplayName: String,
  val userEmail: String,
  private val onNotifyTimerFinished: (String) -> Unit,
) : ViewModel() {

  val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
  val isListening: StateFlow<Boolean> = voiceManager.isListening
  val isSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking
  val partialTranscript: StateFlow<String> = voiceManager.partialTranscript
  val voiceError: StateFlow<String?> = voiceManager.voiceError
  val selectedVoiceLabel: StateFlow<String> = voiceManager.selectedVoiceLabel

  val preferencesState: StateFlow<UiState<UserPreference>> =
    repository.observePreferences(currentUserId)
      .map<UserPreference?, UiState<UserPreference>> { pref ->
        UiState.Success(
          pref ?: UserPreference(
            userId = currentUserId,
            displayName = initialDisplayName.ifBlank { "Friend" },
            preferredLanguage = KavyaLanguage.Hinglish.label,
            speechSpeed = 1.0,
            autoSpeak = true,
            memoryEnabled = true,
          )
        )
      }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing preferences", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load preferences"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val memoriesState: StateFlow<UiState<List<MemoryItem>>> =
    repository.observeMemories(currentUserId)
      .map<List<MemoryItem>, UiState<List<MemoryItem>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing memories", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load memories"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val remindersState: StateFlow<UiState<List<ReminderItem>>> =
    repository.observeReminders(currentUserId)
      .map<List<ReminderItem>, UiState<List<ReminderItem>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing reminders", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load reminders"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val habitsState: StateFlow<List<HabitEntity>> =
    habitRepository.observeHabits(currentUserId)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  private val _actionCardsByMessageKey = MutableStateFlow<Map<String, List<KavyaActionCard>>>(emptyMap())
  private val _localGreeting = MutableStateFlow(pickVariedGreeting(null))
  val localGreeting: StateFlow<String> = _localGreeting.asStateFlow()

  private val _isThinking = MutableStateFlow(false)
  val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

  private val _bannerMessage = MutableStateFlow<String?>(null)
  val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

  private val _activeTimers = MutableStateFlow<List<ActiveTimer>>(emptyList())
  val activeTimers: StateFlow<List<ActiveTimer>> = _activeTimers.asStateFlow()

  private val persistedMessagesFlow =
    repository.observeMessages(currentUserId)
      .map<List<ChatMessageEntity>, UiState<List<ChatMessageEntity>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing messages", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load chat history"))
      }

  val chatUiState: StateFlow<UiState<List<ChatUiMessage>>> =
    combine(persistedMessagesFlow, _actionCardsByMessageKey) { msgState, cardsMap ->
      when (msgState) {
        is UiState.Loading -> UiState.Loading
        is UiState.Error -> msgState
        is UiState.Success -> {
          val fmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
          val uiList = msgState.data.map { entity ->
            val timeStr = (entity.createdAt ?: Timestamp.now()).toDate().let { fmt.format(it) }
            ChatUiMessage(
              id = entity.id,
              sender = entity.sender,
              text = entity.text,
              mode = entity.mode,
              timestampText = timeStr,
              actionSummary = entity.actionSummary,
              actionCards = cardsMap[entity.id].orEmpty(),
            )
          }
          UiState.Success(uiList)
        }
      }
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading,
    )

  private var timerLoopJob: Job? = null
  private var lastGreetingIndex = -1

  init {
    viewModelScope.launch {
      repository.ensureDefaultPreferences(currentUserId, initialDisplayName)
    }
    viewModelScope.launch {
      preferencesState.collect { state ->
        if (state is UiState.Success) {
          val pref = state.data
          voiceManager.updateVoiceSettings(
            language = KavyaLanguage.fromRaw(pref.preferredLanguage),
            speechSpeed = pref.speechSpeed.toFloat(),
          )
        }
      }
    }
  }

  private fun currentPreference(): UserPreference {
    return (preferencesState.value as? UiState.Success)?.data
      ?: UserPreference(
        userId = currentUserId,
        displayName = initialDisplayName.ifBlank { "Friend" },
        preferredLanguage = KavyaLanguage.Hinglish.label,
        speechSpeed = 1.0,
        autoSpeak = true,
        memoryEnabled = true,
      )
  }

  private fun currentMemories(): List<MemoryItem> =
    (memoriesState.value as? UiState.Success)?.data.orEmpty()

  private fun currentChatMessages(): List<ChatUiMessage> =
    (chatUiState.value as? UiState.Success)?.data.orEmpty()

  fun sendMessage(rawInput: String) {
    val prompt = rawInput.trim()
    if (prompt.isEmpty() || _isThinking.value) return

    val pref = currentPreference()
    val lang = KavyaLanguage.fromRaw(pref.preferredLanguage)
    val online = isOnline.value
    val modeStr = if (online) "online" else "offline"

    viewModelScope.launch {
      _isThinking.value = true
      _bannerMessage.value = null

      repository.addMessage(
        userId = currentUserId,
        sender = "user",
        text = prompt,
        mode = modeStr,
      ).onFailure { err ->
        _bannerMessage.value = err.localizedMessage ?: "Failed to save message."
      }

      val currentHabits = habitsState.value
      val recentMsgs = currentChatMessages()
      val lastKavyaMsg = recentMsgs.lastOrNull { it.sender == "kavya" }?.text.orEmpty()

      val result = if (online) {
        aiEngine.generateResponse(
          userMessage = prompt,
          preferredLanguage = lang,
          displayName = pref.displayName,
          memoryEnabled = pref.memoryEnabled,
          memories = currentMemories(),
          habits = currentHabits,
          recentMessages = recentMsgs,
        )
      } else {
        Result.success(
          offlineEngine.processOffline(
            input = prompt,
            preferredLanguage = lang,
            displayName = pref.displayName,
            memories = currentMemories(),
            habits = currentHabits,
            lastKavyaMessage = lastKavyaMsg,
          )
        )
      }

      result.fold(
        onSuccess = { aiResult ->
          val executedCards = executeCommands(aiResult.commands, pref)
          val summary = buildActionSummary(executedCards)
          val addRes = repository.addMessage(
            userId = currentUserId,
            sender = "kavya",
            text = aiResult.reply,
            mode = modeStr,
            actionSummary = summary,
          )
          addRes.onSuccess { newDocId ->
            if (executedCards.isNotEmpty()) {
              _actionCardsByMessageKey.update { it + (newDocId to executedCards) }
            }
          }.onFailure { err ->
            _bannerMessage.value = err.localizedMessage ?: "Failed to save Kavya's reply."
          }

          if (pref.autoSpeak) {
            voiceManager.speak(aiResult.reply)
          }
        },
        onFailure = { error ->
          val errReply = error.localizedMessage
            ?: "Online service abhi respond nahi kar rahi. Thodi der baad try karte hain."
          repository.addMessage(
            userId = currentUserId,
            sender = "kavya",
            text = errReply,
            mode = modeStr,
            actionSummary = "Error reported",
          )
          _bannerMessage.value = errReply
        },
      )

      _isThinking.value = false
    }
  }

  private suspend fun executeCommands(
    commands: List<ParsedKavyaCommand>,
    pref: UserPreference,
  ): List<KavyaActionCard> {
    val cards = mutableListOf<KavyaActionCard>()
    for (cmd in commands) {
      when (cmd) {
        is ParsedKavyaCommand.OpenApp -> {
          cards.add(automationManager.openApp(cmd.appName))
        }

        is ParsedKavyaCommand.SetTimer -> {
          val timerId = startCountdownTimer(cmd.durationSeconds, cmd.label)
          val systemStarted = automationManager.tryStartSystemTimer(cmd.durationSeconds, cmd.label)
          val detail = if (systemStarted) {
            "In-app countdown & Clock timer started for ${formatDuration(cmd.durationSeconds)}"
          } else {
            "Live countdown started in Kavya for ${formatDuration(cmd.durationSeconds)}"
          }
          cards.add(
            KavyaActionCard.TimerStarted(
              timerId = timerId,
              label = cmd.label,
              durationSeconds = cmd.durationSeconds,
              succeeded = true,
              detail = detail,
            )
          )
        }

        is ParsedKavyaCommand.SetReminder -> {
          val dueMillis = computeReminderMillis(cmd)
          val saveResult = repository.addReminder(
            userId = currentUserId,
            title = cmd.title,
            dueTimeText = cmd.timeText,
            dueAtMillis = dueMillis,
          )
          saveResult.fold(
            onSuccess = { reminderId ->
              val alarmSet = automationManager.scheduleReminderNotification(reminderId, cmd.title, dueMillis)
              val detail = if (alarmSet) {
                "Saved & scheduled notification for ${cmd.timeText}"
              } else {
                "Saved to your Reminders list (${cmd.timeText})"
              }
              cards.add(
                KavyaActionCard.ReminderSaved(
                  title = cmd.title,
                  dueTimeText = cmd.timeText,
                  succeeded = true,
                  detail = detail,
                )
              )
            },
            onFailure = { err ->
              cards.add(
                KavyaActionCard.ReminderSaved(
                  title = cmd.title,
                  dueTimeText = cmd.timeText,
                  succeeded = false,
                  detail = err.localizedMessage ?: "Failed to save reminder",
                )
              )
            },
          )
        }

        is ParsedKavyaCommand.PrepareMessage -> {
          cards.add(
            KavyaActionCard.PendingMessage(
              actionId = UUID.randomUUID().toString(),
              platform = cmd.platform,
              recipient = cmd.recipient,
              messageBody = cmd.messageBody,
              status = ConfirmationStatus.PENDING,
              statusDetail = "Review message & tap Confirm to open ${cmd.platform}",
            )
          )
        }

        is ParsedKavyaCommand.PrepareCall -> {
          val rememberedPhone = cmd.phoneNumber.ifBlank {
            currentMemories()
              .firstOrNull {
                it.category == MemoryCategory.CONTACT.wireValue &&
                  it.label.contains(cmd.recipient, ignoreCase = true)
              }
              ?.value
              .orEmpty()
          }
          cards.add(
            KavyaActionCard.PendingCall(
              actionId = UUID.randomUUID().toString(),
              platform = cmd.platform,
              recipient = cmd.recipient,
              phoneNumber = rememberedPhone,
              callType = cmd.callType,
              status = ConfirmationStatus.PENDING,
              statusDetail = "Confirm ${cmd.callType} call via ${cmd.platform}",
            )
          )
        }

        is ParsedKavyaCommand.SaveMemory -> {
          if (!pref.memoryEnabled) {
            cards.add(
              KavyaActionCard.MemoryUpdate(
                operation = "Skipped",
                label = cmd.label,
                value = cmd.value,
                succeeded = false,
                detail = "Memory saving is currently turned off in Memory settings.",
              )
            )
          } else {
            val cat = MemoryCategory.fromRaw(cmd.category)
            val res = repository.addMemory(currentUserId, cat, cmd.label, cmd.value)
            cards.add(
              res.fold(
                onSuccess = {
                  KavyaActionCard.MemoryUpdate(
                    operation = "Remembered",
                    label = cmd.label,
                    value = cmd.value,
                    succeeded = true,
                    detail = "Saved to your Memory Vault",
                  )
                },
                onFailure = { err ->
                  KavyaActionCard.MemoryUpdate(
                    operation = "Blocked",
                    label = cmd.label,
                    value = "Protected",
                    succeeded = false,
                    detail = err.localizedMessage ?: "Could not save memory",
                  )
                },
              )
            )
          }
        }

        is ParsedKavyaCommand.DeleteMemory -> {
          val target = currentMemories().firstOrNull {
            it.label.equals(cmd.label, ignoreCase = true) ||
              it.label.contains(cmd.label, ignoreCase = true)
          }
          if (target == null) {
            cards.add(
              KavyaActionCard.MemoryUpdate(
                operation = "Delete",
                label = cmd.label,
                value = "",
                succeeded = false,
                detail = "No matching memory found for \"${cmd.label}\".",
              )
            )
          } else {
            val res = repository.deleteMemory(currentUserId, target.id)
            cards.add(
              KavyaActionCard.MemoryUpdate(
                operation = "Deleted",
                label = target.label,
                value = target.value,
                succeeded = res.isSuccess,
                detail = if (res.isSuccess) "Removed from Memory Vault" else "Delete failed",
              )
            )
          }
        }

        is ParsedKavyaCommand.GetCurrentTime -> {
          cards.add(automationManager.currentTimeInfo())
        }

        is ParsedKavyaCommand.AddHabit -> {
          val saved = habitRepository.addHabit(currentUserId, cmd.habitTitle)
          cards.add(
            KavyaActionCard.HabitSaved(
              habitId = saved.id,
              title = saved.title,
              reminderOfferPending = !saved.reminderEnabled,
              detail = if (saved.reminderEnabled) {
                "Local habit record active • Reminders: ${saved.reminderSlots}"
              } else {
                "Saved locally on device • Daily check-in ready"
              },
            )
          )
        }

        is ParsedKavyaCommand.CompleteHabit -> {
          val updated = habitRepository.markHabitDoneByTitle(currentUserId, cmd.habitTitle)
          if (updated != null) {
            cards.add(
              KavyaActionCard.HabitCompleted(
                title = updated.title,
                streak = updated.currentStreak,
                succeeded = true,
                detail = "Marked complete for today (${updated.currentStreak}d streak)",
              )
            )
          } else {
            cards.add(
              KavyaActionCard.HabitCompleted(
                title = cmd.habitTitle,
                streak = 0,
                succeeded = false,
                detail = "Could not find a tracked habit matching \"${cmd.habitTitle}\".",
              )
            )
          }
        }

        is ParsedKavyaCommand.ScheduleHabitReminders -> {
          val parsedSlots = parseSlotPairs(cmd.slots)
          val slotsLabel = parsedSlots.joinToString(", ") { (h, m) -> formatTime12h(h, m) }
          val slotsWire = parsedSlots.joinToString(", ") { (h, m) ->
            String.format(Locale.US, "%02d:%02d", h, m)
          }
          val allHabits = habitRepository.getHabitsSnapshot(currentUserId)
          val targets = if (!cmd.habitTitle.isNullOrBlank()) {
            allHabits.filter { it.title.contains(cmd.habitTitle, ignoreCase = true) }
          } else {
            allHabits
          }
          if (targets.isEmpty()) {
            cards.add(
              KavyaActionCard.HabitRemindersScheduled(
                habitTitles = emptyList(),
                slotsLabel = slotsLabel,
                succeeded = false,
                detail = "No tracked habits found to schedule reminders for.",
              )
            )
          } else {
            targets.forEach { habit ->
              habitRepository.updateHabitReminders(habit, true, slotsWire)
              automationManager.scheduleDailyHabitReminders(habit.id, habit.title, parsedSlots)
            }
            cards.add(
              KavyaActionCard.HabitRemindersScheduled(
                habitTitles = targets.map { it.title },
                slotsLabel = slotsLabel,
                succeeded = true,
                detail = "Daily reminders set for ${targets.size} habit(s) at $slotsLabel",
              )
            )
          }
        }
      }
    }
    return cards
  }

  private fun computeReminderMillis(cmd: ParsedKavyaCommand.SetReminder): Long {
    val now = Calendar.getInstance()
    if (cmd.delayMinutes != null && cmd.delayMinutes > 0) {
      return now.timeInMillis + cmd.delayMinutes * 60_000L
    }
    if (cmd.hour24 != null) {
      val target = Calendar.getInstance().apply {
        if ((cmd.dayOffset ?: 0) > 0) add(Calendar.DAY_OF_YEAR, cmd.dayOffset!!)
        set(Calendar.HOUR_OF_DAY, cmd.hour24.coerceIn(0, 23))
        set(Calendar.MINUTE, (cmd.minute ?: 0).coerceIn(0, 59))
        set(Calendar.SECOND, 0)
      }
      if (target.timeInMillis <= now.timeInMillis && (cmd.dayOffset ?: 0) == 0) {
        target.add(Calendar.DAY_OF_YEAR, 1)
      }
      return target.timeInMillis
    }
    return now.timeInMillis + 3_600_000L
  }

  private fun buildActionSummary(cards: List<KavyaActionCard>): String? {
    if (cards.isEmpty()) return null
    return cards.joinToString(" • ") { card ->
      when (card) {
        is KavyaActionCard.AppLaunch ->
          if (card.succeeded) "Opened ${card.appName}" else "${card.appName} unavailable"
        is KavyaActionCard.TimerStarted -> "Timer: ${card.label}"
        is KavyaActionCard.ReminderSaved ->
          if (card.succeeded) "Reminder: ${card.title}" else "Reminder failed"
        is KavyaActionCard.PendingMessage -> "Message draft for ${card.recipient}"
        is KavyaActionCard.PendingCall -> "Call draft: ${card.recipient}"
        is KavyaActionCard.MemoryUpdate -> "${card.operation}: ${card.label}"
        is KavyaActionCard.TimeReport -> card.formattedTime
        is KavyaActionCard.HabitSaved -> "Habit saved: ${card.title}"
        is KavyaActionCard.HabitCompleted -> "Habit done: ${card.title}"
        is KavyaActionCard.HabitRemindersScheduled -> "Habit reminders: ${card.slotsLabel}"
      }
    }.take(500)
  }

  fun confirmMessageAction(messageId: String, actionId: String, editedBody: String) {
    updatePendingCard(messageId, actionId) { card ->
      if (card !is KavyaActionCard.PendingMessage) return@updatePendingCard card
      val body = editedBody.trim().ifBlank { card.messageBody }
      val (ok, detail) = automationManager.sendPreparedMessage(card.platform, card.recipient, body)
      card.copy(
        messageBody = body,
        status = if (ok) ConfirmationStatus.CONFIRMED else ConfirmationStatus.FAILED,
        statusDetail = detail,
      )
    }
  }

  fun cancelMessageAction(messageId: String, actionId: String) {
    updatePendingCard(messageId, actionId) { card ->
      if (card !is KavyaActionCard.PendingMessage) return@updatePendingCard card
      card.copy(
        status = ConfirmationStatus.CANCELLED,
        statusDetail = "Cancelled — message was not sent.",
      )
    }
  }

  fun updatePendingCallPhone(messageId: String, actionId: String, recipientName: String, phone: String) {
    updatePendingCard(messageId, actionId) { card ->
      if (card !is KavyaActionCard.PendingCall) return@updatePendingCard card
      card.copy(
        recipient = recipientName.ifBlank { card.recipient },
        phoneNumber = phone,
      )
    }
  }

  fun confirmCallAction(messageId: String, actionId: String, phoneOverride: String) {
    updatePendingCard(messageId, actionId) { card ->
      if (card !is KavyaActionCard.PendingCall) return@updatePendingCard card
      val number = phoneOverride.trim().ifBlank { card.phoneNumber }
      val (ok, detail) = automationManager.initiateCall(
        platform = card.platform,
        recipient = card.recipient,
        phoneNumber = number,
        callType = card.callType,
      )
      card.copy(
        phoneNumber = number,
        status = if (ok) ConfirmationStatus.CONFIRMED else ConfirmationStatus.FAILED,
        statusDetail = detail,
      )
    }
  }

  fun cancelCallAction(messageId: String, actionId: String) {
    updatePendingCard(messageId, actionId) { card ->
      if (card !is KavyaActionCard.PendingCall) return@updatePendingCard card
      card.copy(
        status = ConfirmationStatus.CANCELLED,
        statusDetail = "Cancelled — call was not placed.",
      )
    }
  }

  private fun updatePendingCard(
    messageId: String,
    actionId: String,
    transform: (KavyaActionCard) -> KavyaActionCard,
  ) {
    _actionCardsByMessageKey.update { currentMap ->
      val list = currentMap[messageId] ?: return@update currentMap
      val updated = list.map { card ->
        val matches = when (card) {
          is KavyaActionCard.PendingMessage -> card.actionId == actionId
          is KavyaActionCard.PendingCall -> card.actionId == actionId
          else -> false
        }
        if (matches) transform(card) else card
      }
      currentMap + (messageId to updated)
    }
  }

  fun startCountdownTimer(durationSeconds: Int, label: String): String {
    val safeSeconds = durationSeconds.coerceIn(1, 86400)
    val id = UUID.randomUUID().toString()
    val timer = ActiveTimer(
      id = id,
      label = label.ifBlank { "Kavya Timer" },
      totalSeconds = safeSeconds,
      remainingSeconds = safeSeconds,
      running = true,
      completed = false,
    )
    _activeTimers.update { listOf(timer) + it }
    ensureTimerLoopRunning()
    return id
  }

  fun toggleTimerRunning(timerId: String) {
    _activeTimers.update { timers ->
      timers.map { t ->
        if (t.id == timerId && !t.completed) t.copy(running = !t.running) else t
      }
    }
    ensureTimerLoopRunning()
  }

  fun removeTimer(timerId: String) {
    _activeTimers.update { timers -> timers.filterNot { it.id == timerId } }
  }

  private fun ensureTimerLoopRunning() {
    if (timerLoopJob?.isActive == true) return
    timerLoopJob = viewModelScope.launch {
      while (true) {
        delay(1000L)
        val hasRunning = _activeTimers.value.any { it.running && !it.completed }
        if (!hasRunning) break
        val finishedLabels = mutableListOf<String>()
        _activeTimers.update { timers ->
          timers.map { timer ->
            if (!timer.running || timer.completed) {
              timer
            } else {
              val next = (timer.remainingSeconds - 1).coerceAtLeast(0)
              if (next == 0) {
                finishedLabels.add(timer.label)
                timer.copy(remainingSeconds = 0, running = false, completed = true)
              } else {
                timer.copy(remainingSeconds = next)
              }
            }
          }
        }
        finishedLabels.forEach { label ->
          onNotifyTimerFinished(label)
          if (currentPreference().autoSpeak) {
            voiceManager.speak("Time up! Tumhara $label complete ho gaya.")
          }
        }
      }
    }
  }

  fun addManualReminder(title: String, dueTimeText: String, dueAtMillis: Long) {
    viewModelScope.launch {
      repository.addReminder(currentUserId, title, dueTimeText, dueAtMillis)
        .onSuccess { id ->
          automationManager.scheduleReminderNotification(id, title, dueAtMillis)
        }
        .onFailure { err ->
          _bannerMessage.value = err.localizedMessage ?: "Failed to add reminder."
        }
    }
  }

  fun toggleReminderCompleted(reminderId: String, completed: Boolean) {
    viewModelScope.launch {
      repository.toggleReminderCompleted(currentUserId, reminderId, completed)
        .onFailure { err ->
          _bannerMessage.value = err.localizedMessage ?: "Failed to update reminder."
        }
    }
  }

  fun deleteReminder(reminderId: String) {
    viewModelScope.launch {
      repository.deleteReminder(currentUserId, reminderId)
        .onFailure { err ->
          _bannerMessage.value = err.localizedMessage ?: "Failed to delete reminder."
        }
    }
  }

  fun addManualMemory(category: MemoryCategory, label: String, value: String) {
    viewModelScope.launch {
      repository.addMemory(currentUserId, category, label, value)
        .onFailure { err ->
          _bannerMessage.value = err.localizedMessage ?: "Failed to save memory."
        }
    }
  }

  fun updateManualMemory(memoryId: String, category: MemoryCategory, label: String, value: String) {
    viewModelScope.launch {
      repository.updateMemory(currentUserId, memoryId, category, label, value)
        .onFailure { err ->
          _bannerMessage.value = err.localizedMessage ?: "Failed to update memory."
        }
    }
  }

  fun deleteMemory(memoryId: String) {
    viewModelScope.launch {
      repository.deleteMemory(currentUserId, memoryId)
        .onFailure { err ->
          _bannerMessage.value = err.localizedMessage ?: "Failed to delete memory."
        }
    }
  }

  fun updatePreferences(
    displayName: String,
    language: KavyaLanguage,
    speechSpeed: Float,
    autoSpeak: Boolean,
    memoryEnabled: Boolean,
  ) {
    viewModelScope.launch {
      repository.updatePreferences(
        userId = currentUserId,
        displayName = displayName,
        preferredLanguage = language,
        speechSpeed = speechSpeed.toDouble(),
        autoSpeak = autoSpeak,
        memoryEnabled = memoryEnabled,
      ).onFailure { err ->
        _bannerMessage.value = err.localizedMessage ?: "Failed to save preferences."
      }
    }
  }

  fun clearConversation() {
    viewModelScope.launch {
      repository.clearMessages(currentUserId)
      _actionCardsByMessageKey.value = emptyMap()
      _localGreeting.value = pickVariedGreeting(lastGreetingIndex)
    }
  }

  fun startVoiceInput() {
    val lang = KavyaLanguage.fromRaw(currentPreference().preferredLanguage)
    voiceManager.startListening(lang) { recognizedText ->
      sendMessage(recognizedText)
    }
  }

  fun stopVoiceInput() {
    voiceManager.stopListening()
  }

  fun speakText(text: String) {
    voiceManager.speak(text)
  }

  fun stopSpeaking() {
    voiceManager.stopSpeaking()
  }

  fun clearVoiceError() {
    voiceManager.clearError()
  }

  fun clearBanner() {
    _bannerMessage.value = null
  }

  fun hasCallPermission(): Boolean = automationManager.hasCallPermission()

  fun toggleHabitToday(habit: HabitEntity) {
    viewModelScope.launch {
      habitRepository.toggleHabitToday(habit)
    }
  }

  fun addManualHabit(title: String, enableReminders: Boolean, slots: List<String>) {
    viewModelScope.launch {
      val parsedSlots = parseSlotPairs(slots)
      val slotsWire = parsedSlots.joinToString(", ") { (h, m) ->
        String.format(Locale.US, "%02d:%02d", h, m)
      }
      val saved = habitRepository.addHabit(
        userId = currentUserId,
        title = title,
        reminderSlots = if (enableReminders) slotsWire else "",
        reminderEnabled = enableReminders,
      )
      if (enableReminders) {
        automationManager.scheduleDailyHabitReminders(saved.id, saved.title, parsedSlots)
      }
    }
  }

  fun updateHabitReminders(habit: HabitEntity, enabled: Boolean, slots: List<String>) {
    viewModelScope.launch {
      val parsedSlots = parseSlotPairs(slots)
      val slotsWire = parsedSlots.joinToString(", ") { (h, m) ->
        String.format(Locale.US, "%02d:%02d", h, m)
      }
      val updated = habitRepository.updateHabitReminders(habit, enabled, slotsWire)
      if (enabled) {
        automationManager.scheduleDailyHabitReminders(updated.id, updated.title, parsedSlots)
      }
    }
  }

  fun deleteHabit(habitId: Long) {
    viewModelScope.launch {
      habitRepository.deleteHabit(habitId)
    }
  }

  fun enableHabitRemindersFromCard(messageId: String, habitId: Long, habitTitle: String) {
    viewModelScope.launch {
      val defaultSlots = listOf("09:00", "14:00", "20:00")
      val parsed = parseSlotPairs(defaultSlots)
      val slotsWire = "09:00, 14:00, 20:00"
      val target = habitRepository.getHabitsSnapshot(currentUserId).firstOrNull { it.id == habitId }
      if (target != null) {
        habitRepository.updateHabitReminders(target, true, slotsWire)
        automationManager.scheduleDailyHabitReminders(target.id, target.title, parsed)
      }
      _actionCardsByMessageKey.update { map ->
        val cards = map[messageId] ?: return@update map
        val updatedCards = cards.map { card ->
          if (card is KavyaActionCard.HabitSaved && card.habitId == habitId) {
            card.copy(
              reminderOfferPending = false,
              detail = "Reminders scheduled daily at 9:00 AM, 2:00 PM & 8:00 PM",
            )
          } else {
            card
          }
        }
        map + (messageId to updatedCards)
      }
      _bannerMessage.value = "Daily reminders enabled for \"$habitTitle\" (9 AM, 2 PM, 8 PM)."
    }
  }

  private fun parseSlotPairs(slots: List<String>): List<Pair<Int, Int>> {
    val parsed = slots.mapNotNull { slot ->
      val match = Regex("(\\d{1,2}):(\\d{2})").find(slot)
      if (match != null) {
        val h = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 9
        val m = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
        h to m
      } else {
        null
      }
    }
    return parsed.ifEmpty { listOf(9 to 0, 14 to 0, 20 to 0) }
  }

  private fun formatTime12h(hour24: Int, minute: Int): String {
    val amPm = if (hour24 >= 12) "PM" else "AM"
    val h12 = when {
      hour24 == 0 -> 12
      hour24 > 12 -> hour24 - 12
      else -> hour24
    }
    return String.format(Locale.US, "%d:%02d %s", h12, minute, amPm)
  }

  private fun pickVariedGreeting(previousIndex: Int?): String {
    val greetings = listOf(
      "Hey! Main Kavya hoon, tumhari personal AI assistant! Aaj kya karna hai? Batao, main help karti hoon!",
      "Namaste! Kya haal hai? Study, coding, reminders ya phone controls — aaj kis mission par chalein?",
      "Hey there! Kavya ready hai! Koi app kholna ho, timer lagana ho, ya kuch naya seekhna ho — bas bol do!",
      "Hello! Main Kavya hoon. Aaj tumhara din easy aur productive banane ke liye ready hoon! Kya plan hai?",
      "Hi! Kaise ho? Hindi, English ya Hinglish — jis mein comfortable ho baat karo, main sun rahi hoon!",
    )
    val candidates = greetings.indices.filter { it != previousIndex }
    val nextIdx = candidates.random()
    lastGreetingIndex = nextIdx
    return greetings[nextIdx]
  }

  override fun onCleared() {
    super.onCleared()
    timerLoopJob?.cancel()
  }

  companion object {
    private const val TAG = "KavyaViewModel"
    private const val STOP_TIMEOUT_MILLIS = 5000L

    fun formatDuration(totalSeconds: Int): String {
      val hrs = totalSeconds / 3600
      val mins = (totalSeconds % 3600) / 60
      val secs = totalSeconds % 60
      return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
      } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
      }
    }
  }
}
