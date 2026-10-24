package dev.agam.learn.ui.stats

import dev.agam.learn.data.AppSettings
import dev.agam.learn.domain.effectiveLocalDate
import dev.agam.learn.fake.FakeCourseRepository
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.FakeSettingsRepository
import dev.agam.learn.fake.FixedClock
import dev.agam.learn.fake.progressRecorderFor
import dev.agam.learn.model.Chapter
import dev.agam.learn.model.Course
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.Lesson
import dev.agam.learn.runner.LanguageId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StatsViewModelTest {

    private lateinit var fakeRepo: FakeProgressRepository
    private lateinit var fakeSettings: FakeSettingsRepository
    private lateinit var clock: FixedClock
    private val toronto = TimeZone.of("America/Toronto")
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    /** Jan 6 at noon in Toronto, so the learning day is Jan 6. */
    private val today: Long
        get() = effectiveLocalDate(clock.instant, toronto).toEpochDays().toLong()

    private fun seedActivity(activity: DailyActivity) = runBlocking { fakeRepo.recordDailyActivity(activity) }

    private fun seedResult(result: ExerciseResult) = runBlocking { fakeRepo.recordExerciseResult(result) }

    @Before
    fun setup() {
        fakeRepo = FakeProgressRepository()
        fakeSettings = FakeSettingsRepository(AppSettings(dailyGoalXp = 75))
        clock = FixedClock(Instant.parse("2026-01-06T17:00:00Z"))
    }

    private fun viewModel(course: Course = twelveChapterCourse()) = StatsViewModel(
        progressRepository = fakeRepo,
        settingsRepository = fakeSettings,
        courseRepository = FakeCourseRepository(listOf(course)),
        progressRecorder = progressRecorderFor(
            fakeRepo,
            listOf(course),
            settings = fakeSettings,
            clock = clock,
            timeZone = toronto
        ),
        coroutineScope = testScope
    )

    /** A course with twelve chapters, one lesson and one exercise each, like the real content. */
    private fun twelveChapterCourse(): Course = Course(
        id = "kotlin-stripe",
        language = LanguageId.KOTLIN,
        title = "Kotlin",
        description = "",
        chapters = (1..12).map { n ->
            val id = "ch%02d-test".format(n)
            Chapter(
                id = id,
                title = "Chapter $n",
                summary = "",
                lessons = listOf(
                    Lesson(
                        id = "$id-lesson",
                        title = "Lesson $n",
                        explanation = "",
                        exercises = listOf(
                            Exercise.MultipleChoice(
                                id = "$id-ex",
                                prompt = "Q",
                                options = listOf("a", "b"),
                                correctIndex = 0,
                                explanation = ""
                            )
                        )
                    )
                )
            )
        }
    )

    @Test
    fun testStatsLoading() {
        listOf(
            DailyActivity(epochDay = today - 2, xpEarned = 50, exercisesCompleted = 5, goalMet = true),
            DailyActivity(epochDay = today - 1, xpEarned = 60, exercisesCompleted = 6, goalMet = true),
            DailyActivity(epochDay = today, xpEarned = 40, exercisesCompleted = 4, goalMet = false)
        ).forEach { seedActivity(it) }

        listOf(
            ExerciseResult(exerciseId = "ch01-test-ex", correct = true, xpAwarded = 10, epochMillis = 1000, chapterId = "ch01-test"),
            ExerciseResult(exerciseId = "ch01-test-ex", correct = false, xpAwarded = 0, epochMillis = 2000, chapterId = "ch01-test"),
            ExerciseResult(exerciseId = "ch02-test-ex", correct = true, xpAwarded = 10, epochMillis = 3000, chapterId = "ch02-test")
        ).forEach { seedResult(it) }

        val state = viewModel().uiState.value
        assertFalse(state.isLoading)
        assertEquals(150, state.totalXp)
        assertEquals(15, state.totalExercisesCompleted)
        assertEquals(7, state.recentXpHistory.size)
        assertEquals(28, state.calendarDays.size)
        // Today has no goal day yet, so the run ends yesterday and covers the two goal days before it.
        assertEquals(2, state.currentStreak)
        assertEquals(2, state.longestStreak)
        assertTrue(state.calendarDays.last().isToday)
        assertEquals(LocalDate.parse("2026-01-06").dayOfMonth, state.calendarDays.last().dayOfMonth)
    }

    @Test
    fun everyChapterOfTheCourseIsListedEvenWithoutAttempts() {
        val state = viewModel().uiState.value

        assertEquals(12, state.chapterAccuracies.size)
        assertTrue(state.chapterAccuracies.all { it.attempted == 0 })
        assertEquals("ch10-test", state.chapterAccuracies[9].chapterId)
        assertEquals("ch12-test", state.chapterAccuracies[11].chapterId)
    }

    @Test
    fun laterChaptersAreCountedFromTheirStoredChapterId() {
        seedResult(
            ExerciseResult(exerciseId = "ch11-test-ex", correct = true, xpAwarded = 10, epochMillis = 1000, chapterId = "ch11-test")
        )
        seedResult(
            ExerciseResult(exerciseId = "ch11-test-ex", correct = false, xpAwarded = 0, epochMillis = 2000, chapterId = "ch11-test")
        )

        val row = viewModel().uiState.value.chapterAccuracies.single { it.chapterId == "ch11-test" }

        assertEquals(2, row.attempted)
        assertEquals(1, row.correct)
        assertEquals(50, row.accuracyPercent)
    }

    @Test
    fun resultsSavedWithoutAChapterAreMatchedThroughTheCourse() {
        seedResult(
            ExerciseResult(exerciseId = "ch12-test-ex", correct = true, xpAwarded = 10, epochMillis = 1000)
        )

        val row = viewModel().uiState.value.chapterAccuracies.single { it.chapterId == "ch12-test" }

        assertEquals(1, row.attempted)
        assertEquals(100, row.accuracyPercent)
    }

    @Test
    fun frozenDayIsShownInTheCalendarAndTheStreakCountsIt() {
        fakeSettings = FakeSettingsRepository(AppSettings(dailyGoalXp = 10))
        // Seven goal days from nine to three days ago earn one freeze. Two days ago was missed and
        // the freeze covers it. Yesterday was a goal day.
        for (daysAgo in 9 downTo 3) {
            seedActivity(DailyActivity(today - daysAgo, 10, 1, goalMet = true))
        }
        seedActivity(DailyActivity(today - 1, 10, 1, goalMet = true))

        val state = viewModel().uiState.value

        val frozen = state.calendarDays.filter { it.isFrozen }
        assertEquals(1, frozen.size)
        // The calendar runs from 27 days ago to today, so the frozen day is at index 25.
        assertEquals(today - 2, today - 27 + state.calendarDays.indexOf(frozen.single()))
        assertEquals(0, state.heldStreakFreezes)
        assertEquals(9, state.currentStreak)
    }

    @Test
    fun withNoAttemptsThereIsNoStreakAndNoActiveDay() {
        val state = viewModel().uiState.value

        assertEquals(0, state.currentStreak)
        assertTrue(state.calendarDays.none { it.isActive || it.isFrozen })
    }

    @Test
    fun dailyGoalComesFromSavedSettings() {
        assertEquals(75, viewModel().uiState.value.dailyGoalXp)
    }
}
