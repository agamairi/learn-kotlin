package dev.agam.learn.data

import dev.agam.learn.content.CourseRepository
import dev.agam.learn.domain.DefaultStreakCalculator
import dev.agam.learn.domain.ReviewScheduler
import dev.agam.learn.domain.StreakFreezeState
import dev.agam.learn.domain.XpPolicy
import dev.agam.learn.domain.effectiveLocalDate
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.ReviewCard
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A new identifier for one attempt. Screens create one per attempt and reuse it only for a retry of that same attempt. */
@OptIn(ExperimentalUuidApi::class)
fun newAttemptId(): String = Uuid.random().toString()

/**
 * A graded answer to one exercise, reported by a screen.
 *
 * - A lesson card (multiple choice, cloze, predict output) pays 10 XP when correct. A wrong answer
 *   seeds a box-1 review card if the card has none.
 * - A review answer ([review] = true) reschedules the card through the scheduler and pays the card XP when correct.
 * - A code drill pays once per exercise: 25 XP when the first attempt passes, 15 after retries.
 * - A multi-part problem is completed as a whole. Completion pays no XP, because each part pays its own.
 */
data class ExerciseAttempt(
    val attemptId: String,
    val exerciseId: String,
    val correct: Boolean,
    val review: Boolean = false,
    val aiHintsUsed: List<Int> = emptyList()
)

/** One checked part of a multi-part problem. Each part pays its XP once, on its first pass. */
data class ProblemPartAttempt(
    val attemptId: String,
    val problemId: String,
    val partId: String,
    val correct: Boolean
)

/** What [ProgressRecorder.record] did with an attempt. */
sealed interface RecordOutcome {
    /** Written. [reviewCard] is the card's schedule after a review answer, otherwise null. */
    data class Recorded(val xpAwarded: Int, val reviewCard: ReviewCard?) : RecordOutcome

    /** The attempt id was already committed, so nothing was written. */
    data object Duplicate : RecordOutcome

    /** The exercise or part is not in the course, or the event does not apply to it. Nothing was written. */
    data object Rejected : RecordOutcome
}

/**
 * The only path that changes learner progress. Every XP-awarding screen reports graded attempts here.
 *
 * For each attempt, one transaction writes the exercise result (always with its lesson and chapter
 * ids, looked up from the course), adds the XP to the daily activity of the effective day, updates
 * goal-met and the review queue, and stores the streak freezes spent on the way. A repeated attempt
 * id writes nothing. Time and time zone come from the injected [clock] and [timeZone].
 */
class ProgressRecorder(
    private val progressRepository: ProgressRepository,
    private val courseRepository: CourseRepository,
    private val settingsRepository: SettingsRepository,
    private val reviewScheduler: ReviewScheduler,
    private val xpPolicy: XpPolicy,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) {
    private val mutex = Mutex()
    private val streaks = DefaultStreakCalculator()

    /** The current instant, from the injected clock. */
    fun now(): Instant = clock.now()

    /** Today's effective day (see [effectiveLocalDate]) as an epoch day, the key of daily activity and review cards. */
    fun today(): Long = effectiveLocalDate(clock.now(), timeZone).toEpochDays().toLong()

    /** Records a graded exercise attempt. */
    suspend fun record(attempt: ExerciseAttempt): RecordOutcome = mutex.withLock {
        val placed = placedExercises()[attempt.exerciseId] ?: return@withLock RecordOutcome.Rejected
        recordExercise(attempt, placed)
    }

    /** Records a checked part of a multi-part problem. */
    suspend fun record(attempt: ProblemPartAttempt): RecordOutcome = mutex.withLock {
        val problem = placedExercises()[attempt.problemId] ?: return@withLock RecordOutcome.Rejected
        recordProblemPart(attempt, problem)
    }

    /** Saves the completion of a lesson, which unlocks the next lesson on the path. */
    suspend fun completeLesson(lessonId: String) {
        progressRepository.markLessonCompleted(lessonId, clock.now().toEpochMilliseconds())
    }

    /**
     * Evaluates the streak as of today and stores the freezes it spends, so the result stays
     * the same on later reads. Called when Home or Stats opens, so a day that just ended is
     * covered by a freeze even when the learner did nothing that day.
     */
    suspend fun refreshStreak(): StreakFreezeState = mutex.withLock {
        val today = effectiveLocalDate(clock.now(), timeZone)
        val state = evaluateStreak(progressRepository.getDailyActivities(), today)
        val stored = progressRepository.getFrozenEpochDays()
        state.frozenDates.forEach { date ->
            val epochDay = date.toEpochDays().toLong()
            if (epochDay !in stored) progressRepository.recordStreakFreeze(epochDay)
        }
        if (progressRepository.getHeldStreakFreezes() != state.heldFreezes) {
            progressRepository.setHeldStreakFreezes(state.heldFreezes)
        }
        state
    }

    private suspend fun recordExercise(attempt: ExerciseAttempt, placed: PlacedExercise): RecordOutcome {
        val exercise = placed.exercise
        val now = clock.now()
        val day = effectiveLocalDate(now, timeZone)
        val prior = progressRepository.getExerciseResultsForExercise(exercise.id)
        val alreadyPassed = prior.any { it.correct }
        val isCard = exercise is Exercise.MultipleChoice ||
            exercise is Exercise.Cloze ||
            exercise is Exercise.PredictOutput

        return when {
            isCard && attempt.review -> recordReview(attempt, placed, now, day)
            isCard -> recordLessonCard(attempt, placed, now, day)
            exercise is Exercise.WriteFunction && !attempt.review -> {
                val firstPass = attempt.correct && !alreadyPassed
                val baseXp = if (firstPass) xpPolicy.xpForExercise(exercise, isFirstAttempt = prior.isEmpty()) else 0
                val xp = if (firstPass) {
                    attempt.aiHintsUsed.fold(baseXp) { currentXp, level ->
                        xpPolicy.xpAfterHint(currentXp, level)
                    }
                } else {
                    0
                }
                commit(
                    attemptId = attempt.attemptId,
                    result = placed.result(exercise.id, attempt.correct, xp, now),
                    completesExercise = firstPass,
                    reviewCard = null,
                    day = day
                )
            }
            exercise is Exercise.MultiPartProblem && attempt.correct && !attempt.review -> commit(
                attemptId = attempt.attemptId,
                result = placed.result(exercise.id, correct = true, xp = 0, epochMillis = now.toEpochMilliseconds()),
                completesExercise = !alreadyPassed,
                reviewCard = null,
                day = day
            )
            else -> RecordOutcome.Rejected
        }
    }

    private suspend fun recordLessonCard(
        attempt: ExerciseAttempt,
        placed: PlacedExercise,
        now: Instant,
        day: LocalDate
    ): RecordOutcome {
        val exercise = placed.exercise
        val dayEpoch = day.toEpochDays().toLong()
        val xp = if (attempt.correct) xpPolicy.xpForExercise(exercise, isFirstAttempt = true) else 0
        val seed = if (!attempt.correct && progressRepository.getReviewCard(exercise.id) == null) {
            reviewScheduler.newCard(exercise.id, dayEpoch)
        } else {
            null
        }
        return commit(
            attemptId = attempt.attemptId,
            result = placed.result(exercise.id, attempt.correct, xp, now),
            completesExercise = true,
            reviewCard = seed,
            day = day
        )
    }

    private suspend fun recordReview(
        attempt: ExerciseAttempt,
        placed: PlacedExercise,
        now: Instant,
        day: LocalDate
    ): RecordOutcome {
        val exercise = placed.exercise
        val dayEpoch = day.toEpochDays().toLong()
        val card = progressRepository.getReviewCard(exercise.id) ?: reviewScheduler.newCard(exercise.id, dayEpoch)
        val rescheduled = reviewScheduler.scheduleNextReview(card, attempt.correct, dayEpoch)
        val xp = if (attempt.correct) xpPolicy.xpForExercise(exercise, isFirstAttempt = true) else 0
        return commit(
            attemptId = attempt.attemptId,
            result = placed.result(exercise.id, attempt.correct, xp, now),
            completesExercise = true,
            reviewCard = rescheduled,
            day = day
        )
    }

    private suspend fun recordProblemPart(attempt: ProblemPartAttempt, problem: PlacedExercise): RecordOutcome {
        val exercise = problem.exercise as? Exercise.MultiPartProblem ?: return RecordOutcome.Rejected
        if (exercise.parts.none { it.id == attempt.partId }) return RecordOutcome.Rejected
        val now = clock.now()
        val prior = progressRepository.getExerciseResultsForExercise(attempt.partId)
        val xp = if (attempt.correct && prior.none { it.correct }) {
            xpPolicy.xpForMultiPartPart(correct = true)
        } else {
            0
        }
        return commit(
            attemptId = attempt.attemptId,
            result = problem.result(attempt.partId, attempt.correct, xp, now),
            completesExercise = false,
            reviewCard = null,
            day = effectiveLocalDate(now, timeZone)
        )
    }

    /**
     * The one write path: result, daily activity, review card and streak freezes in one transaction.
     * [completesExercise] counts toward the day's completed exercises.
     */
    private suspend fun commit(
        attemptId: String,
        result: ExerciseResult,
        completesExercise: Boolean,
        reviewCard: ReviewCard?,
        day: LocalDate
    ): RecordOutcome {
        val dayEpoch = day.toEpochDays().toLong()
        val goal = settingsRepository.getSettings().dailyGoalXp
        val existing = progressRepository.getDailyActivity(dayEpoch)
        val xpToday = (existing?.xpEarned ?: 0) + result.xpAwarded
        val activity = DailyActivity(
            epochDay = dayEpoch,
            xpEarned = xpToday,
            exercisesCompleted = (existing?.exercisesCompleted ?: 0) + (if (completesExercise) 1 else 0),
            goalMet = xpToday >= goal
        )
        val history = progressRepository.getDailyActivities().filterNot { it.epochDay == dayEpoch } + activity
        val streak = evaluateStreak(history, day)

        val committed = progressRepository.commitProgress(
            ProgressCommit(
                attemptId = attemptId,
                result = result,
                dailyActivity = activity,
                reviewCard = reviewCard,
                frozenEpochDays = streak.frozenDates.mapTo(mutableSetOf()) { it.toEpochDays().toLong() },
                heldStreakFreezes = streak.heldFreezes
            )
        )
        return if (committed) {
            RecordOutcome.Recorded(xpAwarded = result.xpAwarded, reviewCard = reviewCard)
        } else {
            RecordOutcome.Duplicate
        }
    }

    /** Streak state from the days on which the goal was met. Other days never count. */
    private fun evaluateStreak(history: List<DailyActivity>, today: LocalDate): StreakFreezeState {
        val goalDays = history
            .filter { it.goalMet }
            .mapTo(mutableSetOf()) { LocalDate.fromEpochDays(it.epochDay.toInt()) }
        return streaks.evaluate(goalDays, today)
    }

    private suspend fun placedExercises(): Map<String, PlacedExercise> =
        courseRepository.listCourses()
            .flatMap { course -> course.chapters }
            .flatMap { chapter ->
                chapter.lessons.flatMap { lesson ->
                    lesson.exercises.map { exercise ->
                        PlacedExercise(exercise, lessonId = lesson.id, chapterId = chapter.id)
                    }
                }
            }
            .associateBy { it.exercise.id }

    /** An exercise together with the lesson and chapter it belongs to. */
    private class PlacedExercise(
        val exercise: Exercise,
        val lessonId: String,
        val chapterId: String
    ) {
        fun result(exerciseId: String, correct: Boolean, xp: Int, epochMillis: Instant): ExerciseResult =
            result(exerciseId, correct, xp, epochMillis.toEpochMilliseconds())

        fun result(exerciseId: String, correct: Boolean, xp: Int, epochMillis: Long): ExerciseResult =
            ExerciseResult(
                exerciseId = exerciseId,
                correct = correct,
                xpAwarded = xp,
                epochMillis = epochMillis,
                lessonId = lessonId,
                chapterId = chapterId
            )
    }
}
