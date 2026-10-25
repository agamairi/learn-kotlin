package dev.agam.learn.data

import app.cash.sqldelight.db.SqlDriver
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.domain.LeitnerScheduler
import dev.agam.learn.domain.DefaultXpPolicy
import dev.agam.learn.model.Blank
import dev.agam.learn.model.Chapter
import dev.agam.learn.model.Course
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.Lesson
import dev.agam.learn.model.ProblemPart
import dev.agam.learn.model.ReviewCard
import dev.agam.learn.runner.LanguageId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val toronto = TimeZone.of("America/Toronto")

private fun instant(text: String): Instant = Instant.parse(text)

private fun epochDay(year: Int, month: Int, day: Int): Long = LocalDate(year, month, day).toEpochDays().toLong()

/** A clock the test moves by hand. */
private class MutableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

private class StaticCourses(private val courses: List<Course>) : CourseRepository {
    override suspend fun getCourse(courseId: String): Course? = courses.firstOrNull { it.id == courseId }
    override suspend fun listCourses(): List<Course> = courses
}

private val multipleChoice = Exercise.MultipleChoice(
    id = "card-mc",
    prompt = "Pick",
    options = listOf("a", "b"),
    correctIndex = 0,
    explanation = ""
)
private val cloze = Exercise.Cloze(
    id = "card-cloze",
    prompt = "Fill",
    template = "____1",
    blanks = listOf(Blank("____1", listOf("val"))),
    explanation = ""
)
private val drill = Exercise.WriteFunction(
    id = "drill",
    prompt = "Write",
    starterCode = "fun f() = TODO()",
    hiddenTests = "",
    referenceSolution = "fun f() = 1"
)
private val problem = Exercise.MultiPartProblem(
    id = "problem",
    prompt = "Problem",
    story = "Story",
    parts = listOf(
        ProblemPart(id = "part-1", prompt = "one", hiddenTests = "", referenceSolution = "", expected = ""),
        ProblemPart(id = "part-2", prompt = "two", hiddenTests = "", referenceSolution = "", expected = "")
    )
)

private val course = Course(
    id = "test-course",
    language = LanguageId.KOTLIN,
    title = "Test",
    description = "Test course",
    chapters = listOf(
        Chapter(
            id = "ch-a",
            title = "Chapter A",
            summary = "",
            lessons = listOf(
                Lesson(
                    id = "lesson-a",
                    title = "Lesson A",
                    explanation = "",
                    exercises = listOf(multipleChoice, cloze, drill, problem)
                )
            )
        )
    )
)

class ProgressRecorderTest {
    private lateinit var driver: SqlDriver
    private lateinit var repository: SqlDelightProgressRepository
    private lateinit var settings: SqlDelightSettingsRepository
    private lateinit var clock: MutableClock
    private lateinit var recorder: ProgressRecorder

    @BeforeTest
    fun setUp() {
        driver = createInMemoryTestDriver()
        val database = LearnDatabase(driver)
        repository = SqlDelightProgressRepository(database)
        settings = SqlDelightSettingsRepository(database)
        clock = MutableClock(instant("2026-01-06T12:00:00Z")) // 07:00 in Toronto on Jan 6
        recorder = recorderAt(clock)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun recorderAt(clock: Clock) = ProgressRecorder(
        progressRepository = repository,
        courseRepository = StaticCourses(listOf(course)),
        settingsRepository = settings,
        reviewScheduler = LeitnerScheduler(),
        xpPolicy = DefaultXpPolicy(),
        clock = clock,
        timeZone = toronto
    )

    @Test
    fun correctCardStoresLessonAndChapterIdsAndAddsDailyXp() = runTest {
        val outcome = recorder.record(ExerciseAttempt("a1", "card-mc", correct = true))

        assertEquals(RecordOutcome.Recorded(xpAwarded = 10, reviewCard = null), outcome)
        val stored = repository.getExerciseResults().single()
        assertEquals("card-mc", stored.exerciseId)
        assertEquals("lesson-a", stored.lessonId)
        assertEquals("ch-a", stored.chapterId)
        assertEquals(10, stored.xpAwarded)
        assertEquals(DailyActivity(epochDay(2026, 1, 6), 10, 1, goalMet = false), repository.getDailyActivity(epochDay(2026, 1, 6)))
    }

    @Test
    fun missedLessonCardSeedsABoxOneReviewCardInTheSameWrite() = runTest {
        recorder.record(ExerciseAttempt("a1", "card-cloze", correct = false))

        assertFalse(repository.getExerciseResults().single().correct)
        assertEquals(0, repository.getExerciseResults().single().xpAwarded)
        val card = assertNotNull(repository.getReviewCard("card-cloze"))
        assertEquals(ReviewCard("card-cloze", box = 1, dueEpochDay = epochDay(2026, 1, 7)), card)
        assertEquals(
            listOf("card-cloze"),
            repository.getDueReviewCards(epochDay(2026, 1, 7)).map { it.itemId }
        )
    }

    @Test
    fun lessonMistakeRetriesKeepOneDailyCompletionAndAwardXpOnTheFirstCorrectRetry() = runTest {
        recorder.record(ExerciseAttempt("initial-wrong", "card-mc", correct = false))
        recorder.record(
            ExerciseAttempt(
                attemptId = "retry-wrong",
                exerciseId = "card-mc",
                correct = false,
                isLessonRetry = true
            )
        )
        recorder.record(
            ExerciseAttempt(
                attemptId = "retry-correct",
                exerciseId = "card-mc",
                correct = true,
                isLessonRetry = true
            )
        )

        val results = repository.getExerciseResultsForExercise("card-mc")
        assertEquals(3, results.size)
        assertEquals(1, results.count { it.correct })
        assertEquals(listOf(0, 0, 10), results.map { it.xpAwarded }.sorted())
        assertEquals(
            DailyActivity(epochDay(2026, 1, 6), xpEarned = 10, exercisesCompleted = 1, goalMet = false),
            repository.getDailyActivity(epochDay(2026, 1, 6))
        )
    }

    @Test
    fun aMissedCardKeepsAnExistingReviewCard() = runTest {
        repository.upsertReviewCard(ReviewCard("card-cloze", box = 4, dueEpochDay = epochDay(2026, 1, 20)))

        recorder.record(ExerciseAttempt("a1", "card-cloze", correct = false))

        assertEquals(ReviewCard("card-cloze", box = 4, dueEpochDay = epochDay(2026, 1, 20)), repository.getReviewCard("card-cloze"))
    }

    @Test
    fun aRepeatedAttemptIdWritesNothingAgain() = runTest {
        recorder.record(ExerciseAttempt("same", "card-mc", correct = true))

        val second = recorder.record(ExerciseAttempt("same", "card-mc", correct = true))

        assertEquals(RecordOutcome.Duplicate, second)
        assertEquals(1, repository.getExerciseResults().size)
        assertEquals(10, repository.getDailyActivity(epochDay(2026, 1, 6))?.xpEarned)
    }

    @Test
    fun theStoreRejectsAnAttemptIdItAlreadyHoldsEvenWhenCalledDirectly() = runTest {
        val first = recorder.record(ExerciseAttempt("x", "card-mc", correct = true))
        assertIs<RecordOutcome.Recorded>(first)

        val commit = ProgressCommit(
            attemptId = "x",
            result = repository.getExerciseResults().single(),
            dailyActivity = DailyActivity(epochDay(2026, 1, 6), 999, 99, goalMet = true)
        )

        assertFalse(repository.commitProgress(commit))
        assertEquals(10, repository.getDailyActivity(epochDay(2026, 1, 6))?.xpEarned)
    }

    @Test
    fun effectiveDayIsYesterdayAt0359AndTodayAt0400() = runTest {
        clock.now = instant("2026-01-06T08:59:59Z") // 03:59 on Jan 6 in Toronto
        recorder.record(ExerciseAttempt("early", "card-mc", correct = true))
        assertEquals(epochDay(2026, 1, 5), recorder.today())
        assertEquals(10, repository.getDailyActivity(epochDay(2026, 1, 5))?.xpEarned)
        assertNull(repository.getDailyActivity(epochDay(2026, 1, 6)))

        clock.now = instant("2026-01-06T09:00:00Z") // 04:00 on Jan 6 in Toronto
        recorder.record(ExerciseAttempt("late", "card-mc", correct = true))
        assertEquals(epochDay(2026, 1, 6), recorder.today())
        assertEquals(10, repository.getDailyActivity(epochDay(2026, 1, 6))?.xpEarned)
    }

    @Test
    fun goalMetFlipsWhenTheConfiguredGoalIsReached() = runTest {
        settings.setDailyGoalXp(20)

        recorder.record(ExerciseAttempt("a1", "card-mc", correct = true))
        assertFalse(repository.getDailyActivity(epochDay(2026, 1, 6))!!.goalMet)

        recorder.record(ExerciseAttempt("a2", "card-mc", correct = true))
        val activity = repository.getDailyActivity(epochDay(2026, 1, 6))!!
        assertEquals(20, activity.xpEarned)
        assertTrue(activity.goalMet)
    }

    @Test
    fun codeDrillPaysTwentyFiveOnFirstTryFifteenAfterRetryAndOnlyOnce() = runTest {
        recorder.record(ExerciseAttempt("c1", "drill", correct = false))
        val retried = recorder.record(ExerciseAttempt("c2", "drill", correct = true))
        val again = recorder.record(ExerciseAttempt("c3", "drill", correct = true))

        assertEquals(RecordOutcome.Recorded(15, null), retried)
        assertEquals(RecordOutcome.Recorded(0, null), again)
        assertEquals(15, repository.getDailyActivity(epochDay(2026, 1, 6))?.xpEarned)
        assertEquals(1, repository.getDailyActivity(epochDay(2026, 1, 6))?.exercisesCompleted)
        assertEquals("lesson-a", repository.getExerciseResultsForExercise("drill").last().lessonId)
    }

    @Test
    fun codeDrillFirstTryPaysTwentyFive() = runTest {
        assertEquals(RecordOutcome.Recorded(25, null), recorder.record(ExerciseAttempt("c1", "drill", correct = true)))
    }

    @Test
    fun codeDrillWithHintLevelOneReducesXpByTwo() = runTest {
        val outcome = recorder.record(ExerciseAttempt("c1", "drill", correct = true, aiHintsUsed = listOf(1)))
        assertEquals(RecordOutcome.Recorded(23, null), outcome)
        assertEquals(23, repository.getDailyActivity(epochDay(2026, 1, 6))?.xpEarned)
    }

    @Test
    fun codeDrillWithAllThreeHintLevelsReducesXp() = runTest {
        // 25 - 2 - 5 - 10 = 8 XP
        val outcome = recorder.record(ExerciseAttempt("c1", "drill", correct = true, aiHintsUsed = listOf(1, 2, 3)))
        assertEquals(RecordOutcome.Recorded(8, null), outcome)
        assertEquals(8, repository.getDailyActivity(epochDay(2026, 1, 6))?.xpEarned)
    }

    @Test
    fun codeDrillRetryWithHintsNeverReducesXpBelowZero() = runTest {
        recorder.record(ExerciseAttempt("c1", "drill", correct = false))
        // Retry base is 15. Hints cost 2 + 5 + 10 = 17. 15 - 17 -> 0.
        val retried = recorder.record(ExerciseAttempt("c2", "drill", correct = true, aiHintsUsed = listOf(1, 2, 3)))
        assertEquals(RecordOutcome.Recorded(0, null), retried)
        assertEquals(0, repository.getDailyActivity(epochDay(2026, 1, 6))?.xpEarned)
    }

    @Test
    fun multiPartPartsPayFiftyOncePerPartAndCompletionPaysNothing() = runTest {
        assertEquals(RecordOutcome.Recorded(50, null), recorder.record(ProblemPartAttempt("p1", "problem", "part-1", correct = true)))
        assertEquals(RecordOutcome.Recorded(0, null), recorder.record(ProblemPartAttempt("p2", "problem", "part-1", correct = true)))
        assertEquals(RecordOutcome.Recorded(0, null), recorder.record(ProblemPartAttempt("p3", "problem", "part-2", correct = false)))
        assertEquals(RecordOutcome.Recorded(50, null), recorder.record(ProblemPartAttempt("p4", "problem", "part-2", correct = true)))

        assertEquals(RecordOutcome.Recorded(0, null), recorder.record(ExerciseAttempt("done", "problem", correct = true)))

        val activity = repository.getDailyActivity(epochDay(2026, 1, 6))!!
        assertEquals(100, activity.xpEarned)
        assertEquals(1, activity.exercisesCompleted, "Parts are not exercises; only the problem completion counts")
        assertEquals(
            setOf("part-1", "part-2", "problem"),
            repository.getExerciseResults().map { it.exerciseId }.toSet()
        )
        assertTrue(repository.getExerciseResults().filter { it.exerciseId.startsWith("part") }.all { it.lessonId == "lesson-a" })
    }

    @Test
    fun reviewAnswerReschedulesTheCardAndPaysCardXpInDailyActivity() = runTest {
        repository.upsertReviewCard(ReviewCard("card-mc", box = 2, dueEpochDay = epochDay(2026, 1, 6)))

        val correct = recorder.record(ExerciseAttempt("r1", "card-mc", correct = true, review = true))
        assertEquals(RecordOutcome.Recorded(10, ReviewCard("card-mc", box = 3, dueEpochDay = epochDay(2026, 1, 6) + 4)), correct)
        assertEquals(ReviewCard("card-mc", box = 3, dueEpochDay = epochDay(2026, 1, 6) + 4), repository.getReviewCard("card-mc"))
        assertEquals(10, repository.getDailyActivity(epochDay(2026, 1, 6))?.xpEarned)
    }

    @Test
    fun wrongReviewAnswerReturnsTheCardToBoxOneAndPaysNothing() = runTest {
        repository.upsertReviewCard(ReviewCard("card-mc", box = 4, dueEpochDay = epochDay(2026, 1, 6)))

        val wrong = recorder.record(ExerciseAttempt("r1", "card-mc", correct = false, review = true))

        assertEquals(RecordOutcome.Recorded(0, ReviewCard("card-mc", box = 1, dueEpochDay = epochDay(2026, 1, 7))), wrong)
        assertEquals(0, repository.getDailyActivity(epochDay(2026, 1, 6))?.xpEarned)
    }

    @Test
    fun unknownExercisesAndInapplicableEventsAreRejectedWithoutWriting() = runTest {
        assertEquals(RecordOutcome.Rejected, recorder.record(ExerciseAttempt("u1", "no-such-id", correct = true)))
        assertEquals(RecordOutcome.Rejected, recorder.record(ProblemPartAttempt("u2", "problem", "no-such-part", correct = true)))
        assertEquals(RecordOutcome.Rejected, recorder.record(ExerciseAttempt("u3", "drill", correct = true, review = true)))
        assertTrue(repository.getExerciseResults().isEmpty())
        assertNull(repository.getDailyActivity(epochDay(2026, 1, 6)))
    }

    @Test
    fun completingALessonIsSavedAndReadBack() = runTest {
        assertFalse(repository.isLessonCompleted("lesson-a"))

        recorder.completeLesson("lesson-a")

        assertTrue(repository.isLessonCompleted("lesson-a"))
    }

    @Test
    fun aDayWithOnlyFailedAttemptsIsNotAGoalDay() = runTest {
        recorder.record(ExerciseAttempt("f1", "drill", correct = false))
        recorder.record(ExerciseAttempt("f2", "drill", correct = false))

        assertEquals(0, recorder.refreshStreak().currentStreak)
        assertFalse(repository.getDailyActivity(epochDay(2026, 1, 6))!!.goalMet)
    }

    @Test
    fun sevenGoalDaysEarnAFreezeThatIsStoredAndCoversAMissedDay() = runTest {
        settings.setDailyGoalXp(10)
        for (dayOfMonth in 1..7) {
            clock.now = instant("2026-01-0${dayOfMonth}T12:00:00Z")
            recorder.record(ExerciseAttempt("g$dayOfMonth", "card-mc", correct = true))
        }
        assertEquals(1, repository.getHeldStreakFreezes())

        // Day 8 is missed. On day 9 the freeze covers it and is stored as spent.
        clock.now = instant("2026-01-09T12:00:00Z")
        recorder.record(ExerciseAttempt("g9", "card-mc", correct = true))

        assertEquals(setOf(epochDay(2026, 1, 8)), repository.getFrozenEpochDays())
        assertEquals(0, repository.getHeldStreakFreezes())
        assertEquals(9, recorder.refreshStreak().currentStreak)
    }

    @Test
    fun aFreezeIsSpentAtTheFourAmCutoffNotBeforeIt() = runTest {
        settings.setDailyGoalXp(10)
        for (dayOfMonth in 1..7) {
            clock.now = instant("2026-01-0${dayOfMonth}T12:00:00Z")
            recorder.record(ExerciseAttempt("g$dayOfMonth", "card-mc", correct = true))
        }

        // 03:59 on Jan 9 is still Jan 8's learning day, so Jan 8 is not missed yet.
        clock.now = instant("2026-01-09T08:59:59Z")
        val beforeCutoff = recorder.refreshStreak()
        assertEquals(emptySet(), beforeCutoff.frozenDates)
        assertEquals(1, beforeCutoff.heldFreezes)
        assertEquals(emptySet(), repository.getFrozenEpochDays())

        // 04:00 on Jan 9 starts the new day: Jan 8 was missed and the freeze covers it.
        clock.now = instant("2026-01-09T09:00:00Z")
        val afterCutoff = recorder.refreshStreak()
        assertEquals(setOf(LocalDate(2026, 1, 8)), afterCutoff.frozenDates)
        assertEquals(0, afterCutoff.heldFreezes)
        assertEquals(setOf(epochDay(2026, 1, 8)), repository.getFrozenEpochDays())
    }

    @Test
    fun refreshingTheStreakTwiceNeverSpendsAFreezeTwice() = runTest {
        settings.setDailyGoalXp(10)
        for (dayOfMonth in 1..7) {
            clock.now = instant("2026-01-0${dayOfMonth}T12:00:00Z")
            recorder.record(ExerciseAttempt("g$dayOfMonth", "card-mc", correct = true))
        }
        clock.now = instant("2026-01-10T12:00:00Z")

        val first = recorder.refreshStreak()
        val second = recorder.refreshStreak()

        assertEquals(first, second)
        assertEquals(1, repository.getFrozenEpochDays().size)
        assertEquals(0, repository.getHeldStreakFreezes())
    }

    @Test
    fun twentyOneGoalDaysHoldTwoFreezesAndTheCapHolds() = runTest {
        settings.setDailyGoalXp(10)
        for (dayOfMonth in 1..21) {
            clock.now = instant("2026-01-${dayOfMonth.toString().padStart(2, '0')}T12:00:00Z")
            recorder.record(ExerciseAttempt("g$dayOfMonth", "card-mc", correct = true))
        }

        assertEquals(2, repository.getHeldStreakFreezes())
        assertEquals(2, recorder.refreshStreak().heldFreezes)
    }
}
