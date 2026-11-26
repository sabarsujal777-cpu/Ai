package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.KavyaAiEngine
import com.example.ai.OfflineAssistantEngine
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.automation.DeviceAutomationManager
import com.example.automation.KavyaReminderReceiver
import com.example.automation.KavyaVoiceManager
import com.example.data.ConnectivityObserver
import com.example.data.KavyaRepository
import com.example.habits.HabitRepository
import com.example.habits.KavyaLocalDatabase
import com.example.model.KavyaLanguage
import com.example.model.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private enum class KavyaTab(
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  CHAT("Kavya", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline),
  HABITS("Habits", Icons.Rounded.TaskAlt, Icons.Outlined.TaskAlt),
  SCHEDULE("Schedule", Icons.Rounded.Alarm, Icons.Outlined.Alarm),
  MEMORY("Memory", Icons.Rounded.Psychology, Icons.Outlined.Psychology),
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun KavyaApp(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      KavyaAuthenticatedShell(
        currentUserId = user.uid,
        initialDisplayName = user.displayName ?: "Friend",
        userEmail = user.email.orEmpty(),
      )
    }
  }
}

@Composable
private fun KavyaAuthenticatedShell(
  currentUserId: String,
  initialDisplayName: String,
  userEmail: String,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val voiceManager = remember(currentUserId) { KavyaVoiceManager(context) }
  val connectivityObserver = remember(currentUserId) { ConnectivityObserver(context) }
  val automationManager = remember(currentUserId) { DeviceAutomationManager(context) }

  DisposableEffect(voiceManager, connectivityObserver) {
    onDispose {
      voiceManager.release()
      connectivityObserver.release()
    }
  }

  val viewModel: KavyaViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val dbId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(dbId)
        val localDb = KavyaLocalDatabase.getInstance(app)
        KavyaViewModel(
          repository = KavyaRepository(db),
          habitRepository = HabitRepository(localDb.habitDao()),
          aiEngine = KavyaAiEngine(),
          offlineEngine = OfflineAssistantEngine(),
          automationManager = automationManager,
          voiceManager = voiceManager,
          connectivityObserver = connectivityObserver,
          currentUserId = currentUserId,
          initialDisplayName = initialDisplayName,
          userEmail = userEmail,
          onNotifyTimerFinished = { label ->
            KavyaReminderReceiver.showKavyaNotification(
              context = app,
              channelId = KavyaReminderReceiver.CHANNEL_TIMERS,
              channelName = "Kavya Timers",
              title = "Timer Complete!",
              body = "Tumhara $label complete ho gaya hai!",
              notificationId = label.hashCode(),
            )
          },
        )
      }
    },
  )

  val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
  val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
  val isListening by viewModel.isListening.collectAsStateWithLifecycle()
  val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
  val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
  val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
  val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
  val selectedVoiceLabel by viewModel.selectedVoiceLabel.collectAsStateWithLifecycle()
  val localGreeting by viewModel.localGreeting.collectAsStateWithLifecycle()

  val preferencesState by viewModel.preferencesState.collectAsStateWithLifecycle()
  val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
  val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()
  val habits by viewModel.habitsState.collectAsStateWithLifecycle()
  val chatState by viewModel.chatUiState.collectAsStateWithLifecycle()
  val activeTimers by viewModel.activeTimers.collectAsStateWithLifecycle()

  var currentTab by rememberSaveable { mutableStateOf(KavyaTab.CHAT) }
  val snackbarHostState = remember { SnackbarHostState() }

  BackHandler(enabled = currentTab != KavyaTab.CHAT) {
    currentTab = KavyaTab.CHAT
  }

  // Runtime permission launchers
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { }

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      viewModel.startVoiceInput()
    } else {
      viewModel.clearVoiceError()
      // Show explicit permission message per Section 8/12
      viewModel.speakText("Mujhe is feature ke liye microphone permission chahiye.")
    }
  }

  var pendingCallTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
  val callPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    // Proceed cleanly either with CALL_PHONE or Dialer fallback
    val pending = pendingCallTarget
    pendingCallTarget = null
    if (pending != null) {
      viewModel.confirmCallAction(pending.first, pending.second, pending.third)
    }
  }

  var contactPickerTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
  val contactPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    val target = contactPickerTarget
    contactPickerTarget = null
    if (target != null && result.resultCode == Activity.RESULT_OK) {
      val dataUri = result.data?.data
      if (dataUri != null) {
        try {
          context.contentResolver.query(
            dataUri,
            arrayOf(
              ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
              ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
          )?.use { cursor ->
            if (cursor.moveToFirst()) {
              val name = cursor.getString(0).orEmpty()
              val phone = cursor.getString(1).orEmpty()
              viewModel.updatePendingCallPhone(target.first, target.second, name, phone)
            }
          }
        } catch (_: Exception) {
        }
      }
    }
  }

  LaunchedEffect(voiceError, bannerMessage) {
    val msg = voiceError ?: bannerMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearVoiceError()
      viewModel.clearBanner()
    }
  }

  val preferredLanguage = (preferencesState as? UiState.Success)?.data?.preferredLanguage ?: "Hinglish"

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        KavyaTopHeader(
          isOnline = isOnline,
          isSpeaking = isSpeaking,
          preferredLanguage = preferredLanguage,
          onStopSpeaking = { viewModel.stopSpeaking() },
        )
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            KavyaTab.entries.forEach { tab ->
              val selected = currentTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("nav_tab_${tab.name}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            KavyaTab.entries.forEach { tab ->
              val selected = currentTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("nav_rail_${tab.name}"),
              )
            }
          }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
          when (currentTab) {
            KavyaTab.CHAT -> {
              ChatScreen(
                chatState = chatState,
                localGreeting = localGreeting,
                isOnline = isOnline,
                isThinking = isThinking,
                isListening = isListening,
                partialTranscript = partialTranscript,
                activeTimers = activeTimers,
                onSendMessage = { viewModel.sendMessage(it) },
                onMicClick = {
                  if (isListening) {
                    viewModel.stopVoiceInput()
                  } else {
                    val granted = ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                      viewModel.startVoiceInput()
                    } else {
                      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                  }
                },
                onSpeakText = { viewModel.speakText(it) },
                onClearChat = { viewModel.clearConversation() },
                onOpenScheduleTab = { currentTab = KavyaTab.SCHEDULE },
                onConfirmMessage = { msgId, actId, body ->
                  viewModel.confirmMessageAction(msgId, actId, body)
                },
                onCancelMessage = { msgId, actId ->
                  viewModel.cancelMessageAction(msgId, actId)
                },
                onConfirmCall = { msgId, actId, phone ->
                  if (phone.isNotBlank() && !viewModel.hasCallPermission()) {
                    pendingCallTarget = Triple(msgId, actId, phone)
                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                  } else {
                    viewModel.confirmCallAction(msgId, actId, phone)
                  }
                },
                onCancelCall = { msgId, actId ->
                  viewModel.cancelCallAction(msgId, actId)
                },
                onPickContactForCall = { msgId, actId ->
                  contactPickerTarget = msgId to actId
                  val pickIntent = Intent(
                    Intent.ACTION_PICK,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                  )
                  contactPickerLauncher.launch(pickIntent)
                },
                onEnableHabitReminders = { msgId, habitId, habitTitle ->
                  viewModel.enableHabitRemindersFromCard(msgId, habitId, habitTitle)
                },
              )
            }

            KavyaTab.HABITS -> {
              HabitsScreen(
                habits = habits,
                onToggleHabitToday = { viewModel.toggleHabitToday(it) },
                onAddHabit = { title, enableReminders, slots ->
                  viewModel.addManualHabit(title, enableReminders, slots)
                },
                onUpdateHabitReminders = { habit, enabled, slots ->
                  viewModel.updateHabitReminders(habit, enabled, slots)
                },
                onDeleteHabit = { viewModel.deleteHabit(it) },
                onAskKavyaToTrackHabits = {
                  currentTab = KavyaTab.CHAT
                  viewModel.sendMessage("Track my daily habits")
                },
              )
            }

            KavyaTab.SCHEDULE -> {
              ScheduleScreen(
                activeTimers = activeTimers,
                remindersState = remindersState,
                onStartTimer = { secs, label -> viewModel.startCountdownTimer(secs, label) },
                onToggleTimer = { viewModel.toggleTimerRunning(it) },
                onRemoveTimer = { viewModel.removeTimer(it) },
                onAddReminder = { title, timeText, dueMillis ->
                  viewModel.addManualReminder(title, timeText, dueMillis)
                },
                onToggleReminder = { id, done -> viewModel.toggleReminderCompleted(id, done) },
                onDeleteReminder = { viewModel.deleteReminder(it) },
              )
            }

            KavyaTab.MEMORY -> {
              MemoryScreen(
                preferencesState = preferencesState,
                memoriesState = memoriesState,
                selectedVoiceLabel = selectedVoiceLabel,
                userEmail = userEmail,
                onSavePreferences = { name, lang, speed, autoSpeak, memEnabled ->
                  viewModel.updatePreferences(name, lang, speed, autoSpeak, memEnabled)
                },
                onTestVoice = { lang ->
                  val sample = when (lang) {
                    KavyaLanguage.Hindi -> "नमस्ते! मैं काव्या हूँ, आपकी पर्सनल एआई असिस्टेंट।"
                    KavyaLanguage.English -> "Hey there! I'm Kavya, your personal AI assistant."
                    KavyaLanguage.Hinglish -> "Hey! Main Kavya hoon, tumhari personal AI assistant!"
                  }
                  viewModel.speakText(sample)
                },
                onAddMemory = { cat, label, value -> viewModel.addManualMemory(cat, label, value) },
                onUpdateMemory = { id, cat, label, value ->
                  viewModel.updateManualMemory(id, cat, label, value)
                },
                onDeleteMemory = { viewModel.deleteMemory(it) },
                onSignOut = {
                  signOut(credentialManager = credentialManager, scope = scope)
                },
              )
            }
          }
        }
      }
    }
  }
}
