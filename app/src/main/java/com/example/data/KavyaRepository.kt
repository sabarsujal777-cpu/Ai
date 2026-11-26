package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.ChatMessageEntity
import com.example.model.KavyaLanguage
import com.example.model.MemoryCategory
import com.example.model.MemoryItem
import com.example.model.ReminderItem
import com.example.model.UserPreference
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class KavyaRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireMatchingUserId(expectedUserId: String): String {
    val uid = auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
    return uid
  }

  fun observePreferences(userId: String): Flow<UserPreference?> = flow {
    val uid = requireMatchingUserId(userId)
    val docPath = "users/$uid/preferences/profile"
    emitAll(
      db.collection("users").document(uid).collection("preferences").document("profile")
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(UserPreference::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.GET, docPath)
          }
          throw error
        }
    )
  }

  suspend fun ensureDefaultPreferences(userId: String, defaultDisplayName: String): Result<Unit> {
    val docPath = "users/$userId/preferences/profile"
    return try {
      val uid = requireMatchingUserId(userId)
      val docRef = db.collection("users").document(uid).collection("preferences").document("profile")
      val existing = docRef.get().await()
      if (!existing.exists()) {
        val safeName = defaultDisplayName.trim().ifBlank { "Friend" }.take(60)
        val payload = mapOf(
          "userId" to uid,
          "displayName" to safeName,
          "preferredLanguage" to KavyaLanguage.Hinglish.label,
          "speechSpeed" to 1.0,
          "autoSpeak" to true,
          "memoryEnabled" to true,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updatePreferences(
    userId: String,
    displayName: String,
    preferredLanguage: KavyaLanguage,
    speechSpeed: Double,
    autoSpeak: Boolean,
    memoryEnabled: Boolean,
  ): Result<Unit> {
    val docPath = "users/$userId/preferences/profile"
    return try {
      val uid = requireMatchingUserId(userId)
      val safeName = displayName.trim().ifBlank { "Friend" }.take(60)
      val clampedSpeed = speechSpeed.coerceIn(0.5, 2.0)
      val docRef = db.collection("users").document(uid).collection("preferences").document("profile")
      val existing = docRef.get().await()
      if (!existing.exists()) {
        val createPayload = mapOf(
          "userId" to uid,
          "displayName" to safeName,
          "preferredLanguage" to preferredLanguage.label,
          "speechSpeed" to clampedSpeed,
          "autoSpeak" to autoSpeak,
          "memoryEnabled" to memoryEnabled,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(createPayload).await()
      } else {
        docRef.update(
          mapOf(
            "displayName" to safeName,
            "preferredLanguage" to preferredLanguage.label,
            "speechSpeed" to clampedSpeed,
            "autoSpeak" to autoSpeak,
            "memoryEnabled" to memoryEnabled,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  fun observeMemories(userId: String): Flow<List<MemoryItem>> = flow {
    val uid = requireMatchingUserId(userId)
    val path = "users/$uid/memories"
    emitAll(
      db.collection("users").document(uid).collection("memories")
        .orderBy("updatedAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(MemoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun containsSensitiveInfo(text: String): Boolean {
    val lower = text.lowercase()
    val sensitiveKeywords = listOf(
      "password", "passcode", "otp", "pin code", "atm pin", "upi pin",
      "cvv", "secret key", "api key", "private key", "aadhaar", "pan card", "ssn"
    )
    if (sensitiveKeywords.any { lower.contains(it) }) return true
    val cardPattern = Regex("\\b(?:\\d[ -]*?){13,16}\\b")
    return cardPattern.containsMatchIn(text)
  }

  suspend fun addMemory(
    userId: String,
    category: MemoryCategory,
    label: String,
    value: String,
  ): Result<Unit> {
    val cleanLabel = label.trim().take(80)
    val cleanValue = value.trim().take(500)
    val docPath = "users/$userId/memories"
    if (cleanLabel.isEmpty() || cleanValue.isEmpty()) {
      return Result.failure(IllegalArgumentException("Memory label and value cannot be empty."))
    }
    if (containsSensitiveInfo("$cleanLabel $cleanValue")) {
      return Result.failure(
        SecurityException("Kavya never stores passwords, OTPs, PINs, or sensitive financial info.")
      )
    }
    return try {
      val uid = requireMatchingUserId(userId)
      val docRef = db.collection("users").document(uid).collection("memories").document()
      val payload = mapOf(
        "userId" to uid,
        "category" to category.wireValue,
        "label" to cleanLabel,
        "value" to cleanValue,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateMemory(
    userId: String,
    memoryId: String,
    category: MemoryCategory,
    label: String,
    value: String,
  ): Result<Unit> {
    val cleanLabel = label.trim().take(80)
    val cleanValue = value.trim().take(500)
    val docPath = "users/$userId/memories/$memoryId"
    if (memoryId.isBlank() || cleanLabel.isEmpty() || cleanValue.isEmpty()) {
      return Result.failure(IllegalArgumentException("Memory ID, label, and value cannot be empty."))
    }
    if (containsSensitiveInfo("$cleanLabel $cleanValue")) {
      return Result.failure(
        SecurityException("Kavya never stores passwords, OTPs, PINs, or sensitive financial info.")
      )
    }
    return try {
      val uid = requireMatchingUserId(userId)
      db.collection("users").document(uid).collection("memories").document(memoryId)
        .update(
          mapOf(
            "category" to category.wireValue,
            "label" to cleanLabel,
            "value" to cleanValue,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteMemory(userId: String, memoryId: String): Result<Unit> {
    val docPath = "users/$userId/memories/$memoryId"
    return try {
      val uid = requireMatchingUserId(userId)
      require(memoryId.isNotBlank()) { "Memory ID cannot be blank" }
      db.collection("users").document(uid).collection("memories").document(memoryId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  fun observeReminders(userId: String): Flow<List<ReminderItem>> = flow {
    val uid = requireMatchingUserId(userId)
    val path = "users/$uid/reminders"
    emitAll(
      db.collection("users").document(uid).collection("reminders")
        .orderBy("dueAtMillis", Query.Direction.ASCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(ReminderItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun addReminder(
    userId: String,
    title: String,
    dueTimeText: String,
    dueAtMillis: Long,
  ): Result<String> {
    val cleanTitle = title.trim().take(160)
    val cleanDueText = dueTimeText.trim().ifBlank { "Upcoming" }.take(80)
    val safeDueMillis = if (dueAtMillis > 0L) dueAtMillis else System.currentTimeMillis() + 3_600_000L
    val docPath = "users/$userId/reminders"
    if (cleanTitle.isEmpty()) {
      return Result.failure(IllegalArgumentException("Reminder title cannot be empty."))
    }
    return try {
      val uid = requireMatchingUserId(userId)
      val docRef = db.collection("users").document(uid).collection("reminders").document()
      val payload = mapOf(
        "userId" to uid,
        "title" to cleanTitle,
        "dueTimeText" to cleanDueText,
        "dueAtMillis" to safeDueMillis,
        "completed" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun toggleReminderCompleted(
    userId: String,
    reminderId: String,
    completed: Boolean,
  ): Result<Unit> {
    val docPath = "users/$userId/reminders/$reminderId"
    return try {
      val uid = requireMatchingUserId(userId)
      require(reminderId.isNotBlank()) { "Reminder ID cannot be blank" }
      db.collection("users").document(uid).collection("reminders").document(reminderId)
        .update(
          mapOf(
            "completed" to completed,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteReminder(userId: String, reminderId: String): Result<Unit> {
    val docPath = "users/$userId/reminders/$reminderId"
    return try {
      val uid = requireMatchingUserId(userId)
      require(reminderId.isNotBlank()) { "Reminder ID cannot be blank" }
      db.collection("users").document(uid).collection("reminders").document(reminderId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  fun observeMessages(userId: String): Flow<List<ChatMessageEntity>> = flow {
    val uid = requireMatchingUserId(userId)
    val path = "users/$uid/messages"
    emitAll(
      db.collection("users").document(uid).collection("messages")
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .limitToLast(80)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(ChatMessageEntity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun addMessage(
    userId: String,
    sender: String,
    text: String,
    mode: String,
    actionSummary: String? = null,
  ): Result<String> {
    val cleanSender = if (sender == "user") "user" else "kavya"
    val cleanText = text.trim().take(4000)
    val cleanMode = if (mode == "offline") "offline" else "online"
    val cleanSummary = actionSummary?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
    val docPath = "users/$userId/messages"
    if (cleanText.isEmpty()) {
      return Result.failure(IllegalArgumentException("Message text cannot be empty."))
    }
    return try {
      val uid = requireMatchingUserId(userId)
      val docRef = db.collection("users").document(uid).collection("messages").document()
      val payload = mapOf(
        "userId" to uid,
        "sender" to cleanSender,
        "text" to cleanText,
        "mode" to cleanMode,
        "actionSummary" to cleanSummary,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ).filterValues { it != null }
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun clearMessages(userId: String): Result<Unit> {
    val docPath = "users/$userId/messages"
    return try {
      val uid = requireMatchingUserId(userId)
      val snapshot = db.collection("users").document(uid).collection("messages").get().await()
      if (!snapshot.isEmpty) {
        val batch = db.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }
}
