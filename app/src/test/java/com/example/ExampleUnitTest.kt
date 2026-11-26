package com.example

import com.example.ai.OfflineAssistantEngine
import com.example.ai.ParsedKavyaCommand
import com.example.habits.HabitEntity
import com.example.model.KavyaLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun offlineEngine_parsesMultiTaskAppAndTimer() {
    val engine = OfflineAssistantEngine()
    val result = engine.processOffline(
      input = "Kavya, open YouTube and set a 10 minute timer",
      preferredLanguage = KavyaLanguage.Hinglish,
      displayName = "Aarav",
      memories = emptyList(),
    )
    assertTrue(result.commands.any { it is ParsedKavyaCommand.OpenApp && it.appName == "YouTube" })
    assertTrue(result.commands.any { it is ParsedKavyaCommand.SetTimer && it.durationSeconds == 600 })
  }

  @Test
  fun offlineEngine_evaluatesArithmetic() {
    val engine = OfflineAssistantEngine()
    val result = engine.processOffline(
      input = "24 * 5",
      preferredLanguage = KavyaLanguage.English,
      displayName = "Aarav",
      memories = emptyList(),
    )
    assertTrue(result.reply.contains("120"))
    assertEquals(0, result.commands.size)
  }

  @Test
  fun offlineEngine_habitFlowAsksThenAddsAndOffersReminders() {
    val engine = OfflineAssistantEngine()

    val step1 = engine.processOffline(
      input = "track my daily habits",
      preferredLanguage = KavyaLanguage.English,
      displayName = "Aarav",
      memories = emptyList(),
    )
    assertTrue(step1.commands.isEmpty())
    assertTrue(step1.reply.contains("which tasks", ignoreCase = true))

    val step2 = engine.processOffline(
      input = "Drink water, Read 20 mins and Workout",
      preferredLanguage = KavyaLanguage.Hinglish,
      displayName = "Aarav",
      memories = emptyList(),
      lastKavyaMessage = step1.reply,
    )
    val added = step2.commands.filterIsInstance<ParsedKavyaCommand.AddHabit>()
    assertEquals(3, added.size)
    assertEquals("Drink water", added[0].habitTitle)
    assertEquals("Read 20 mins", added[1].habitTitle)
    assertEquals("Workout", added[2].habitTitle)
    assertTrue(step2.reply.contains("reminders", ignoreCase = true))

    val step3 = engine.processOffline(
      input = "Yes, remind me throughout the day",
      preferredLanguage = KavyaLanguage.English,
      displayName = "Aarav",
      memories = emptyList(),
      habits = listOf(HabitEntity(id = 1L, userId = "u1", title = "Drink water")),
      lastKavyaMessage = step2.reply,
    )
    assertTrue(step3.commands.any { it is ParsedKavyaCommand.ScheduleHabitReminders })
  }
}
