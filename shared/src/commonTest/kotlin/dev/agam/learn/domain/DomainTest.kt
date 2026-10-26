package dev.agam.learn.domain

import dev.agam.learn.model.Blank
import dev.agam.learn.model.CodePuzzle
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val utc = TimeZone.UTC
private val toronto = TimeZone.of("America/Toronto")

private fun instant(text: String): Instant = Instant.parse(text)

class StreakCalculatorTest {
    private val calculator = DefaultStreakCalculator()

    @Test
    fun emptyHistoryHasNoStreak() {
        val summary = calculator.calculateSummary(
            emptyList(),
            instant("2026-01-10T12:00:00Z"),
            utc
        )

        assertEquals(StreakSummary(0, 0), summary)
    }

    @Test
    fun activityBeforeFourBelongsToPreviousLocalDay() {
        val summary = calculator.calculateSummary(
            listOf(
                instant("2026-01-06T03:59:59Z"),
                instant("2026-01-06T04:00:00Z")
            ),
            instant("2026-01-07T12:00:00Z"),
            utc
        )

        assertEquals(StreakSummary(currentStreak = 2, longestStreak = 2), summary)
    }

    @Test
    fun currentStreakMayEndYesterdayAndGapsLimitLongestStreak() {
        val summary = calculator.calculateSummary(
            listOf(
                instant("2026-01-01T12:00:00Z"),
                instant("2026-01-02T12:00:00Z"),
                instant("2026-01-04T12:00:00Z"),
                instant("2026-01-05T12:00:00Z")
            ),
            instant("2026-01-06T12:00:00Z"),
            utc
        )

        assertEquals(2, summary.currentStreak)
        assertEquals(2, summary.longestStreak)
    }

    @Test
    fun midnightAndCutoffUseLocalDate() {
        val summary = calculator.calculateSummary(
            listOf(
                instant("2026-01-06T04:59:59Z"), // 23:59:59 on Jan 5 in Toronto
                instant("2026-01-06T05:00:00Z"), // midnight on Jan 6 in Toronto
                instant("2026-01-06T08:59:59Z"), // 03:59:59, still Jan 5's effective day
                instant("2026-01-06T09:00:00Z") // 04:00, Jan 6's effective day
            ),
            instant("2026-01-07T15:00:00Z"),
            toronto
        )

        assertEquals(StreakSummary(currentStreak = 2, longestStreak = 2), summary)
    }

    @Test
    fun springDstChangeStillUsesFourLocalOClock() {
        val summary = calculator.calculateSummary(
            listOf(
                instant("2026-03-08T06:59:59Z"), // 01:59:59 EST, effective Mar 7
                instant("2026-03-08T08:00:00Z") // 04:00 EDT, effective Mar 8
            ),
            instant("2026-03-09T12:00:00Z"),
            toronto
        )

        assertEquals(StreakSummary(currentStreak = 2, longestStreak = 2), summary)
    }
}

class XpPolicyTest {
    private val policy = DefaultXpPolicy()
    private val multipleChoice = Exercise.MultipleChoice(
        id = "mc",
        prompt = "Choose",
        options = listOf("a", "b"),
        correctIndex = 0,
        explanation = ""
    )
    private val writeFunction = Exercise.WriteFunction(
        id = "code",
        prompt = "Implement",
        starterCode = "fun f() = 0",
        hiddenTests = "",
        referenceSolution = "fun f() = 1"
    )

    @Test
    fun awardsPlanValuesAndZeroForWrongAnswers() {
        assertEquals(10, policy.xpForExercise(multipleChoice, isFirstAttempt = true))
        assertEquals(25, policy.xpForExercise(writeFunction, isFirstAttempt = true))
        assertEquals(15, policy.xpForExercise(writeFunction, isFirstAttempt = false))
        assertEquals(0, policy.xpForExercise(multipleChoice, isFirstAttempt = true, correct = false))
        assertEquals(50, policy.xpForMultiPartPart(correct = true))
        assertEquals(0, policy.xpForMultiPartPart(correct = false))
    }

    @Test
    fun hintCostsAreCappedAtZero() {
        assertEquals(2, policy.hintCost(1))
        assertEquals(5, policy.hintCost(2))
        assertEquals(10, policy.hintCost(3))
        assertEquals(0, policy.xpAfterHint(3, 3))
        assertEquals(5, policy.xpAfterHint(10, 2))
    }
}

class DailyGoalTrackerTest {
    private val tracker = DefaultDailyGoalTracker()

    @Test
    fun defaultGoalAndRatioAreClamped() {
        assertEquals(50, tracker.defaultTargetXp)
        assertTrue(tracker.isGoalMet(50, 50))
        assertEquals(1f, tracker.progressRatio(100, 50))
        assertEquals(0f, tracker.progressRatio(-5, 50))
        assertEquals(1f, tracker.progressRatio(0, 0))
    }

    @Test
    fun xpAtThreeFiftyNineBelongsToPreviousGoalDay() {
        val now = instant("2026-01-06T09:00:00Z") // 04:00 Toronto
        val results = listOf(
            result("2026-01-06T08:59:59Z", 20),
            result("2026-01-06T09:00:00Z", 30),
            result("2026-01-06T10:00:00Z", 100)
        )

        assertEquals(30, tracker.xpToday(results, instant("2026-01-06T09:00:01Z"), toronto))
        assertEquals(30, tracker.xpToday(results, now, toronto))
        assertEquals(30, tracker.progress(results, now, toronto).xpToday)
    }

    private fun result(time: String, xp: Int) = ExerciseResult(
        exerciseId = time,
        correct = xp > 0,
        xpAwarded = xp,
        epochMillis = instant(time).toEpochMilliseconds()
    )
}

class LeitnerSchedulerTest {
    private val scheduler = LeitnerScheduler()

    @Test
    fun correctAnswersMoveUpWithTheNewBoxInterval() {
        val card = ReviewCard("item", box = 1, dueEpochDay = 10)

        assertEquals(ReviewCard("item", 2, 12), scheduler.scheduleNextReview(card, true, 10))
        assertEquals(ReviewCard("item", 5, 26), scheduler.scheduleNextReview(ReviewCard("item", 5, 10), true, 10))
    }

    @Test
    fun wrongAnswersReturnToBoxOneDueTomorrow() {
        val card = ReviewCard("item", box = 4, dueEpochDay = 10)

        assertEquals(ReviewCard("item", 1, 21), scheduler.scheduleNextReview(card, false, 20))
        assertEquals(ReviewCard("new", 1, 11), scheduler.newCard("new", 10))
    }

    @Test
    fun dueItemsIncludeMissedCards() {
        val cards = listOf(
            ReviewCard("early", 1, 4),
            ReviewCard("today", 2, 5),
            ReviewCard("later", 3, 6)
        )

        assertEquals(listOf("early", "today"), scheduler.dueItems(cards, 5).map { it.itemId })
    }
}

class AnswerGraderTest {
    private val grader = DefaultAnswerGrader()

    @Test
    fun multipleChoiceRejectsInvalidIndexes() {
        val exercise = Exercise.MultipleChoice("mc", "", options = listOf("a", "b"), correctIndex = 1, explanation = "")

        assertTrue(grader.gradeMultipleChoice(exercise, 1))
        assertFalse(grader.gradeMultipleChoice(exercise, -1))
        assertFalse(grader.gradeMultipleChoice(exercise, 2))
    }

    @Test
    fun clozeTrimsAnswersHonorsCaseAndReportsEveryBlank() {
        val exercise = Exercise.Cloze(
            id = "cloze",
            prompt = "",
            template = "____1 ____2",
            blanks = listOf(
                Blank("____1", listOf("val"), caseSensitive = true),
                Blank("____2", listOf("println", "print"), caseSensitive = false)
            ),
            explanation = ""
        )

        val result = grader.gradeClozeWithFeedback(exercise, mapOf("____1" to " val ", "____2" to "PRINT"))
        assertTrue(result.isCorrect)
        assertEquals(2, result.feedback.size)
        assertTrue(result.feedback.all { it.isCorrect })

        val wrong = grader.gradeClozeWithFeedback(exercise, mapOf("____1" to "VAL"))
        assertFalse(wrong.isCorrect)
        assertFalse(wrong.feedback[0].isCorrect)
        assertFalse(wrong.feedback[1].isCorrect)
        assertTrue(wrong.feedback[1].message.contains("println"))
    }

    @Test
    fun predictOutputNormalizesLineEndingsLineTrailingWhitespaceAndFinalNewlines() {
        val exercise = Exercise.PredictOutput("output", "", "", "first\nsecond", "")

        assertTrue(grader.gradePredictOutput(exercise, "first \r\nsecond\t\n\n"))
        assertFalse(grader.gradePredictOutput(exercise, " first\nsecond"))
        assertFalse(grader.gradePredictOutput(exercise, "first\n second"))
    }

    @Test
    fun codePuzzleGradesBlanksWithFeedback() {
        val puzzle = CodePuzzle(
            template = "fun double(x: Int): Int = ____1\nfun add(a: Int, b: Int): Int = ____2",
            blanks = listOf(
                Blank(
                    placeholder = "____1",
                    accepted = listOf("x * 2"),
                    options = listOf("x * 2", "x + 2", "x / 2", "x * x")
                ),
                Blank(
                    placeholder = "____2",
                    accepted = listOf("a + b"),
                    options = listOf("a + b", "a - b", "a * b", "b - a")
                )
            ),
            explanation = "Simple arithmetic one-liners."
        )

        val pass = grader.gradeCodePuzzleWithFeedback(
            puzzle,
            mapOf("____1" to "x * 2", "____2" to "a + b")
        )
        assertTrue(pass.isCorrect)
        assertEquals(2, pass.feedback.size)
        assertTrue(pass.feedback.all { it.isCorrect })

        val partial = grader.gradeCodePuzzleWithFeedback(
            puzzle,
            mapOf("____1" to "x * 2", "____2" to "a - b")
        )
        assertFalse(partial.isCorrect)
        assertTrue(partial.feedback[0].isCorrect)
        assertFalse(partial.feedback[1].isCorrect)

        val missing = grader.gradeCodePuzzle(
            puzzle,
            mapOf("____1" to "x * 2")
        )
        assertFalse(missing)
    }

    @Test
    fun codePuzzleRejectsEmptyPuzzles() {
        val emptyPuzzle = CodePuzzle(
            template = "fun empty() = 1",
            blanks = emptyList()
        )
        assertFalse(grader.gradeCodePuzzle(emptyPuzzle, emptyMap()))
        val result = grader.gradeCodePuzzleWithFeedback(emptyPuzzle, emptyMap())
        assertFalse(result.isCorrect)
        assertTrue(result.feedback.isEmpty())
    }

    @Test
    fun codePuzzleRejectsAnswersNotOfferedInOptions() {
        val puzzle = CodePuzzle(
            template = "val result = ____1",
            blanks = listOf(
                Blank(
                    placeholder = "____1",
                    accepted = listOf("a + b", "b + a"),
                    options = listOf("a + b", "a - b", "a * b", "a / b")
                )
            )
        )
        // "b + a" is accepted in the model, but NOT offered in the options
        val unoffered = grader.gradeCodePuzzleWithFeedback(
            puzzle,
            mapOf("____1" to "b + a")
        )
        assertFalse(unoffered.isCorrect)
        assertFalse(unoffered.feedback.single().isCorrect)

        // "a + b" is in the options and accepted
        val offered = grader.gradeCodePuzzleWithFeedback(
            puzzle,
            mapOf("____1" to "a + b")
        )
        assertTrue(offered.isCorrect)
        assertTrue(offered.feedback.single().isCorrect)

        // Option trimming works
        val trimmed = grader.gradeCodePuzzleWithFeedback(
            puzzle,
            mapOf("____1" to "  a + b  ")
        )
        assertTrue(trimmed.isCorrect)
    }

    @Test
    fun codePuzzleRejectsAnswersWhenNoChoicesAreConfigured() {
        val malformedPuzzle = CodePuzzle(
            template = "val result = ____1",
            blanks = listOf(Blank(placeholder = "____1", accepted = listOf("a + b")))
        )

        val result = grader.gradeCodePuzzleWithFeedback(
            malformedPuzzle,
            mapOf("____1" to "a + b")
        )

        assertFalse(result.isCorrect)
        assertFalse(result.feedback.single().isCorrect)
    }

    @Test
    fun codePuzzleCaseSensitivityRules() {
        val sensitivePuzzle = CodePuzzle(
            template = "val x: ____1 = 1L",
            blanks = listOf(
                Blank(
                    placeholder = "____1",
                    accepted = listOf("Long"),
                    caseSensitive = true,
                    options = listOf("Long", "long", "Int", "Double")
                )
            )
        )
        assertTrue(grader.gradeCodePuzzle(sensitivePuzzle, mapOf("____1" to "Long")))
        assertFalse(grader.gradeCodePuzzle(sensitivePuzzle, mapOf("____1" to "long")))

        val insensitivePuzzle = CodePuzzle(
            template = "val x: ____1 = 1L",
            blanks = listOf(
                Blank(
                    placeholder = "____1",
                    accepted = listOf("Long"),
                    caseSensitive = false,
                    options = listOf("Long", "long", "Int", "Double")
                )
            )
        )
        assertTrue(grader.gradeCodePuzzle(insensitivePuzzle, mapOf("____1" to "long")))
    }
}
