package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.habits.HabitRepository
import com.example.habits.KavyaLocalDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kavya AI", appName)
  }

  @Test
  fun `habitRepository creates local record toggles completion and updates reminders`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, KavyaLocalDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = HabitRepository(db.habitDao())
      val saved = repo.addHabit(userId = "user-1", title = "Drink water")
      assertTrue(saved.id > 0L)
      assertEquals("Drink water", saved.title)

      val completed = repo.toggleHabitToday(saved)
      assertEquals(1, completed.currentStreak)
      assertEquals(HabitRepository.todayDateKey(), completed.lastCompletedDate)

      val withReminders = repo.updateHabitReminders(completed, true, "09:00, 14:00, 20:00")
      assertTrue(withReminders.reminderEnabled)
      assertEquals("09:00, 14:00, 20:00", withReminders.reminderSlots)
    } finally {
      db.close()
    }
  }
}
