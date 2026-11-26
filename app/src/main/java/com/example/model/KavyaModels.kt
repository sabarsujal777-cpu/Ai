package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class KavyaLanguage(val label: String, val speechLocaleTag: String) {
  Hinglish("Hinglish", "en-IN"),
  Hindi("Hindi", "hi-IN"),
  English("English", "en-IN");

  companion object {
    fun fromRaw(raw: String?): KavyaLanguage =
      entries.firstOrNull { it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
        ?: Hinglish
  }
}

enum class MemoryCategory(val wireValue: String, val displayLabel: String) {
  PREFERENCE("preference", "Preference"),
  PROJECT("project", "Ongoing Project"),
  COMMAND("command", "Quick Command"),
  CONTACT("contact", "Contact"),
  FACT("fact", "Personal Note");

  companion object {
    fun fromRaw(raw: String?): MemoryCategory =
      entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
        ?: FACT
  }
}

@Keep
@IgnoreExtraProperties
data class UserPreference(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Friend",
  val preferredLanguage: String = "Hinglish",
  val speechSpeed: Double = 1.0,
  val autoSpeak: Boolean = true,
  val memoryEnabled: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class MemoryItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val category: String = "fact",
  val label: String = "",
  val value: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ReminderItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val dueTimeText: String = "",
  val dueAtMillis: Long = 0L,
  val completed: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatMessageEntity(
  @DocumentId val id: String = "",
  val userId: String = "",
  val sender: String = "kavya",
  val text: String = "",
  val mode: String = "online",
  val actionSummary: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class ConfirmationStatus {
  PENDING,
  CONFIRMED,
  CANCELLED,
  FAILED,
}

sealed interface KavyaActionCard {
  data class AppLaunch(
    val appName: String,
    val packageName: String?,
    val succeeded: Boolean,
    val detail: String,
  ) : KavyaActionCard

  data class TimerStarted(
    val timerId: String,
    val label: String,
    val durationSeconds: Int,
    val succeeded: Boolean,
    val detail: String,
  ) : KavyaActionCard

  data class ReminderSaved(
    val title: String,
    val dueTimeText: String,
    val succeeded: Boolean,
    val detail: String,
  ) : KavyaActionCard

  data class PendingMessage(
    val actionId: String,
    val platform: String,
    val recipient: String,
    val messageBody: String,
    val status: ConfirmationStatus = ConfirmationStatus.PENDING,
    val statusDetail: String = "Awaiting your confirmation before sending",
  ) : KavyaActionCard

  data class PendingCall(
    val actionId: String,
    val platform: String,
    val recipient: String,
    val phoneNumber: String,
    val callType: String,
    val status: ConfirmationStatus = ConfirmationStatus.PENDING,
    val statusDetail: String = "Confirm to initiate call",
  ) : KavyaActionCard

  data class MemoryUpdate(
    val operation: String,
    val label: String,
    val value: String,
    val succeeded: Boolean,
    val detail: String,
  ) : KavyaActionCard

  data class TimeReport(
    val formattedTime: String,
    val formattedDate: String,
  ) : KavyaActionCard

  data class HabitSaved(
    val habitId: Long,
    val title: String,
    val reminderOfferPending: Boolean,
    val detail: String,
  ) : KavyaActionCard

  data class HabitCompleted(
    val title: String,
    val streak: Int,
    val succeeded: Boolean,
    val detail: String,
  ) : KavyaActionCard

  data class HabitRemindersScheduled(
    val habitTitles: List<String>,
    val slotsLabel: String,
    val succeeded: Boolean,
    val detail: String,
  ) : KavyaActionCard
}

data class ActiveTimer(
  val id: String,
  val label: String,
  val totalSeconds: Int,
  val remainingSeconds: Int,
  val running: Boolean,
  val completed: Boolean = false,
)

data class ChatUiMessage(
  val id: String,
  val sender: String,
  val text: String,
  val mode: String,
  val timestampText: String = "",
  val actionSummary: String? = null,
  val actionCards: List<KavyaActionCard> = emptyList(),
)

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}
