package com.example.habits

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Entity(tableName = "habits")
data class HabitEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val userId: String,
  val title: String,
  val frequencyLabel: String = "Daily",
  val reminderSlots: String = "",
  val reminderEnabled: Boolean = false,
  val currentStreak: Int = 0,
  val lastCompletedDate: String = "",
  val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "habit_logs")
data class HabitLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val habitId: Long,
  val userId: String,
  val dateKey: String,
  val completedAtMillis: Long = System.currentTimeMillis(),
)

@Dao
interface HabitDao {
  @Query("SELECT * FROM habits WHERE userId = :userId ORDER BY createdAtMillis DESC")
  fun observeHabits(userId: String): Flow<List<HabitEntity>>

  @Query("SELECT * FROM habits WHERE userId = :userId ORDER BY createdAtMillis DESC")
  suspend fun getHabitsSnapshot(userId: String): List<HabitEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHabit(habit: HabitEntity): Long

  @Update
  suspend fun updateHabit(habit: HabitEntity)

  @Query("DELETE FROM habits WHERE id = :habitId")
  suspend fun deleteHabitById(habitId: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLog(log: HabitLogEntity)

  @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND dateKey = :dateKey")
  suspend fun deleteLogForDate(habitId: Long, dateKey: String)

  @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
  suspend fun deleteLogsForHabit(habitId: Long)
}

@Database(
  entities = [HabitEntity::class, HabitLogEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class KavyaLocalDatabase : RoomDatabase() {
  abstract fun habitDao(): HabitDao

  companion object {
    @Volatile
    private var INSTANCE: KavyaLocalDatabase? = null

    fun getInstance(context: Context): KavyaLocalDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          KavyaLocalDatabase::class.java,
          "kavya_local_habits.db",
        ).build().also { INSTANCE = it }
      }
    }
  }
}

class HabitRepository(private val habitDao: HabitDao) {

  fun observeHabits(userId: String): Flow<List<HabitEntity>> =
    habitDao.observeHabits(userId)

  suspend fun getHabitsSnapshot(userId: String): List<HabitEntity> =
    habitDao.getHabitsSnapshot(userId)

  suspend fun addHabit(
    userId: String,
    title: String,
    frequencyLabel: String = "Daily",
    reminderSlots: String = "",
    reminderEnabled: Boolean = false,
  ): HabitEntity {
    val cleanTitle = title.trim().take(120)
    require(cleanTitle.isNotEmpty()) { "Habit title cannot be empty" }

    val existing = habitDao.getHabitsSnapshot(userId).firstOrNull {
      it.title.equals(cleanTitle, ignoreCase = true)
    }
    if (existing != null) {
      val updated = if (reminderEnabled && reminderSlots.isNotBlank()) {
        existing.copy(reminderEnabled = true, reminderSlots = reminderSlots)
      } else {
        existing
      }
      if (updated != existing) habitDao.updateHabit(updated)
      return updated
    }

    val entity = HabitEntity(
      userId = userId,
      title = cleanTitle,
      frequencyLabel = frequencyLabel.ifBlank { "Daily" },
      reminderSlots = reminderSlots,
      reminderEnabled = reminderEnabled,
    )
    val newId = habitDao.insertHabit(entity)
    return entity.copy(id = newId)
  }

  suspend fun toggleHabitToday(habit: HabitEntity): HabitEntity {
    val today = todayDateKey()
    val yesterday = yesterdayDateKey()
    val wasDoneToday = habit.lastCompletedDate == today

    return if (wasDoneToday) {
      habitDao.deleteLogForDate(habit.id, today)
      val reverted = habit.copy(
        lastCompletedDate = "",
        currentStreak = (habit.currentStreak - 1).coerceAtLeast(0),
      )
      habitDao.updateHabit(reverted)
      reverted
    } else {
      val nextStreak = if (habit.lastCompletedDate == yesterday) {
        habit.currentStreak + 1
      } else {
        maxOf(1, habit.currentStreak + if (habit.lastCompletedDate.isEmpty()) 1 else 0)
      }
      habitDao.insertLog(
        HabitLogEntity(
          habitId = habit.id,
          userId = habit.userId,
          dateKey = today,
        )
      )
      val completed = habit.copy(
        lastCompletedDate = today,
        currentStreak = nextStreak,
      )
      habitDao.updateHabit(completed)
      completed
    }
  }

  suspend fun markHabitDoneByTitle(userId: String, query: String): HabitEntity? {
    val habits = habitDao.getHabitsSnapshot(userId)
    val match = habits.firstOrNull {
      it.title.equals(query.trim(), ignoreCase = true) ||
        it.title.contains(query.trim(), ignoreCase = true) ||
        query.trim().contains(it.title, ignoreCase = true)
    } ?: return null

    return if (match.lastCompletedDate == todayDateKey()) {
      match
    } else {
      toggleHabitToday(match)
    }
  }

  suspend fun updateHabitReminders(
    habit: HabitEntity,
    reminderEnabled: Boolean,
    reminderSlots: String,
  ): HabitEntity {
    val updated = habit.copy(
      reminderEnabled = reminderEnabled,
      reminderSlots = if (reminderEnabled) reminderSlots else habit.reminderSlots,
    )
    habitDao.updateHabit(updated)
    return updated
  }

  suspend fun deleteHabit(habitId: Long) {
    habitDao.deleteLogsForHabit(habitId)
    habitDao.deleteHabitById(habitId)
  }

  companion object {
    fun todayDateKey(): String =
      SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun yesterdayDateKey(): String {
      val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
      return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
  }
}
