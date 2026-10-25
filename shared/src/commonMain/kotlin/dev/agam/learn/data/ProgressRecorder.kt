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
 * Lesson attempts carry [lessonRunId] and stay in memory until the whole lesson, including its
 * mistake round, settles. Standalone review and other non-lesson activity commits immediately.
 */
data class ExerciseAttempt(
    val attemptId: String,
    val exerciseId: String,
    val correct: Boolean,
    val review: Boolean = false,
    val aiHintsUsed: List<Int> = emptyList(),
    /** A retry within a lesson's mistake round; record it without counting another completion. */
    val isLessonRetry: Boolean = false,
    /** Non-null only while this attempt belongs to an active lesson run. */
    val lessonRunId: String? = null
)

/** One checked part of a multi-part problem. Each part pays its XP once, on its first pass. */
data class ProblemPartAttempt(
    val attemptId: String,
    val problemId: String,
    val partId: String,
    val correct: Boolean,
    /** Non-null only while this attempt belongs to an active lesson run. */
    val lessonRunId: String? = null
)

/** What [ProgressRecorder.record] did with an attempt. */
sealed interface RecordOutcome {
    /** Accepted. [xpAwarded] is provisional for a lesson run until its settlement succeeds. */
    data class Recorded(val xpAwarded: Int, val reviewCard: ReviewCard?) : RecordOutcome

    /** The attempt id was already accepted or committed, so nothing was written again. */
    data object Duplicate : RecordOutcome

    /** The exercise or part is not in the course, or the event does not apply to it. */
    data object Rejected : RecordOutcome
}

sealed interface LessonRunSettlementOutcome {
    data class Settled(val xpAwarded: Int) : LessonRunSettlementOutcome
    data object Duplicate : LessonRunSettlementOutcome
    data object Rejected : LessonRunSettlementOutcome
}

/**
 * The only path that changes learner progress. Standalone actions commit immediately. Lesson
 * attempts are staged under an explicit run id, then attempts, daily totals, review seeds, streak
 * freezes, and lesson completion are committed together once the mistake round is finished.
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
    private val lessonRuns = mutableMapOf<String, LessonRunSession>()

    /** The current instant, from the injected clock. */
    fun now(): Instant = clock.now()

    /** Today's effective day (see [effectiveLocalDate]) as an epoch day, the key of daily activity and review cards. */
    fun today(): Long = effectiveLocalDate(clock.now(), timeZone).toEpochDays().toLong()

    /** Opens a run before any of its lesson or studio attempts can be accepted. */
    suspend fun beginLessonRun(lessonId: String, runId: String): Boolean = mutex.withLock {
        if (lessonId.isBlank() || runId.isBlank()) return@withLock false
        val existing = lessonRuns[runId]
        if (existing != null) return@withLock existing.lessonId == lessonId && !existing.settled
        if (courseRepository.listCourses().none { course ->
                course.chapters.any { chapter -> chapter.lessons.any { it.id == lessonId } }
            }
        ) {
            return@withLock false
        }
        lessonRuns.entries.removeAll { it.value.settled }
        lessonRuns[runId] = LessonRunSession(lessonId)
        true
    }

    /** Discards an unfinished run when the learner exits the lesson. */
    suspend fun abandonLessonRun(runId: String): Unit = mutex.withLock {
        lessonRuns.remove(runId)
        Unit
    }

    /** Records a graded exercise attempt, either immediately or as part of a lesson run. */
    suspend fun record(attempt: ExerciseAttempt): RecordOutcome = mutex.withLock {
        val placed = placedExercises()[attempt.exerciseId] ?: return@withLock RecordOutcome.Rejected
        val session = attempt.lessonRunId?.let { lessonRuns[it] }
        if (attempt.lessonRunId != null && (session == null || session.lessonId != placed.lessonId || session.settled)) {
            return@withLock RecordOutcome.Rejected
        }
        if (session?.attemptIds?.contains(attempt.attemptId) == true) return@withLock RecordOutcome.Duplicate
        if (session != null && attempt.review) return@withLock RecordOutcome.Rejected
        recordExercise(attempt, placed, session)
    }

    /** Records a checked part of a multi-part problem. */
    suspend fun record(attempt: ProblemPartAttempt): RecordOutcome = mutex.withLock {
        val problem = placedExercises()[attempt.problemId] ?: return@withLock RecordOutcome.Rejected
        val session = attempt.lessonRunId?.let { lessonRuns[it] }
        if (attempt.lessonRunId != null && (session == null || session.lessonId != problem.lessonId || session.settled)) {
            return@withLock RecordOutcome.Rejected
        }
        if (session?.attemptIds?.contains(attempt.attemptId) == true) return@withLock RecordOutcome.Duplicate
        recordProblemPart(attempt, problem, session)
    }

    /** Provisional XP shown by the lesson player while the run remains active. */
    suspend fun lessonRunXp(runId: String): Int = mutex.withLock {
        lessonRuns[runId]?.attempts?.sumOf { it.result.xpAwarded } ?: 0
    }

    /** Confirms that a studio reported a passing result into this exact active lesson run. */
    suspend fun lessonRunHasPassed(runId: String, exerciseId: String): Boolean = mutex.withLock {
        val session = lessonRuns[runId] ?: return@withLock false
        !session.settled && session.attempts.any { it.result.exerciseId == exerciseId && it.result.correct }
    }

    /**
     * Atomically settles a completed lesson run. The effective day is chosen at completion, so a
     * lesson crossing the 4 am rollover credits the day on which the learner finished it.
     */
    suspend fun settleLessonRun(lessonId: String, runId: String): LessonRunSettlementOutcome = mutex.withLock {
        val session = lessonRuns[runId] ?: return@withLock LessonRunSettlementOutcome.Rejected
        if (session.lessonId != lessonId) return@withLock LessonRunSettlementOutcome.Rejected
        if (session.settled) return@withLock LessonRunSettlementOutcome.Duplicate
        val lesson = courseRepository.listCourses()
            .asSequence()
            .flatMap { it.chapters.asSequence() }
            .flatMap { it.lessons.asSequence() }
            .firstOrNull { it.id == lessonId }
            ?: return@withLock LessonRunSettlementOutcome.Rejected
        val requiredExerciseIds = lesson.exercises.mapTo(mutableSetOf()) { it.id }
        val passedExerciseIds = session.attempts
            .filter { it.result.correct && it.result.lessonId == lessonId }
            .mapTo(mutableSetOf()) { it.result.exerciseId }
        if (session.attempts.any { it.result.lessonId != lessonId } ||
            !passedExerciseIds.containsAll(requiredExerciseIds)
        ) {
            return@withLock LessonRunSettlementOutcome.Rejected
        }

        val now = clock.now()
        val day = effectiveLocalDate(now, timeZone)
        val dayEpoch = day.toEpochDays().toLong()
        val totalXp = session.attempts.sumOf { it.result.xpAwarded }
        val priorActivity = progressRepository.getDailyActivity(dayEpoch)
        val dailyActivity = if (session.attempts.isEmpty()) {
            null
        } else {
            val xpToday = (priorActivity?.xpEarned ?: 0) + totalXp
            DailyActivity(
                epochDay = dayEpoch,
                xpEarned = xpToday,
                exercisesCompleted = (priorActivity?.exercisesCompleted ?: 0) + session.attempts.count { it.completesExercise },
                goalMet = xpToday >= settingsRepository.getSettings().dailyGoalXp
            )
        }
        val history = if (dailyActivity == null) {
            progressRepository.getDailyActivities()
        } else {
            progressRepository.getDailyActivities().filterNot { it.epochDay == dayEpoch } + dailyActivity
        }
        val streak = evaluateStreak(history, day)
        val settlement = LessonProgressSettlement(
            runId = runId,
            lessonId = lessonId,
            completedAtEpochMillis = now.toEpochMilliseconds(),
            attempts = session.attempts.toList(),
            dailyActivity = dailyActivity,
            // A new mistake card only becomes durable at settlement. Keep its scheduler interval
            // relative to completion rather than making it overdue while the learner finishes.
            reviewCards = session.attempts.mapNotNull { attempt ->
                attempt.reviewCard?.let { card ->
                    val attemptedDay = effectiveLocalDate(
                        Instant.fromEpochMilliseconds(attempt.result.epochMillis),
                        timeZone
                    ).toEpochDays().toLong()
                    val interval = (card.dueEpochDay - attemptedDay).coerceAtLeast(1L)
                    card.copy(dueEpochDay = dayEpoch + interval)
                }
            }.distinctBy { it.itemId },
            frozenEpochDays = streak.frozenDates.mapTo(mutableSetOf()) { it.toEpochDays().toLong() },
            heldStreakFreezes = streak.heldFreezes
        )

        val committed = progressRepository.settleLessonProgress(settlement)
        session.settled = true
        if (committed) LessonRunSettlementOutcome.Settled(totalXp) else LessonRunSettlementOutcome.Duplicate
    }

    /** Legacy completion hook for callers that do not own a staged run. */
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

    private suspend fun recordExercise(
        attempt: ExerciseAttempt,
        placed: PlacedExercise,
        session: LessonRunSession?
    ): RecordOutcome {
        val exercise = placed.exercise
        val now = clock.now()
        val day = effectiveLocalDate(now, timeZone)
        val prior = exerciseHistory(exercise.id, session)
        val alreadyPassed = prior.any { it.correct }
        val isCard = exercise is Exercise.MultipleChoice ||
            exercise is Exercise.Cloze ||
            exercise is Exercise.PredictOutput

        return when {
            isCard && attempt.review -> recordReview(attempt, placed, now, day)
            isCard -> recordLessonCard(attempt, placed, now, day, session)
            exercise is Exercise.WriteFunction && !attempt.review -> {
                val firstPass = attempt.correct && !alreadyPassed
                val baseXp = if (firstPass) xpPolicy.xpForExercise(exercise, isFirstAttempt = prior.isEmpty()) else 0
                val xp = if (firstPass) {
                    attempt.aiHintsUsed.fold(baseXp) { currentXp, level -> xpPolicy.xpAfterHint(currentXp, level) }
                } else {
                    0
                }
                acceptAttempt(
                    session = session,
                    attemptId = attempt.attemptId,
                    result = placed.result(exercise.id, attempt.correct, xp, now),
                    completesExercise = firstPass,
                    reviewCard = null,
                    day = day
                )
            }
            exercise is Exercise.MultiPartProblem && attempt.correct && !attempt.review -> acceptAttempt(
                session = session,
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
        day: LocalDate,
        session: LessonRunSession?
    ): RecordOutcome {
        val exercise = placed.exercise
        val dayEpoch = day.toEpochDays().toLong()
        val xp = if (attempt.correct) xpPolicy.xpForExercise(exercise, isFirstAttempt = true) else 0
        val existingCard = progressRepository.getReviewCard(exercise.id)
        val stagedCard = session?.attempts?.lastOrNull { it.reviewCard?.itemId == exercise.id }?.reviewCard
        val seed = if (!attempt.correct && existingCard == null && stagedCard == null) {
            reviewScheduler.newCard(exercise.id, dayEpoch)
        } else {
            null
        }
        return acceptAttempt(
            session = session,
            attemptId = attempt.attemptId,
            result = placed.result(exercise.id, attempt.correct, xp, now),
            completesExercise = !attempt.isLessonRetry,
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
        return acceptAttempt(
            session = null,
            attemptId = attempt.attemptId,
            result = placed.result(exercise.id, attempt.correct, xp, now),
            completesExercise = true,
            reviewCard = rescheduled,
            day = day
        )
    }

    private suspend fun recordProblemPart(
        attempt: ProblemPartAttempt,
        problem: PlacedExercise,
        session: LessonRunSession?
    ): RecordOutcome {
        val exercise = problem.exercise as? Exercise.MultiPartProblem ?: return RecordOutcome.Rejected
        if (exercise.parts.none { it.id == attempt.partId }) return RecordOutcome.Rejected
        val now = clock.now()
        val prior = exerciseHistory(attempt.partId, session)
        val xp = if (attempt.correct && prior.none { it.correct }) {
            xpPolicy.xpForMultiPartPart(correct = true)
        } else {
            0
        }
        return acceptAttempt(
            session = session,
            attemptId = attempt.attemptId,
            result = problem.result(attempt.partId, attempt.correct, xp, now),
            completesExercise = false,
            reviewCard = null,
            day = effectiveLocalDate(now, timeZone)
        )
    }

    private suspend fun exerciseHistory(exerciseId: String, session: LessonRunSession?): List<ExerciseResult> =
        progressRepository.getExerciseResultsForExercise(exerciseId) +
            session?.attempts?.map { it.result }?.filter { it.exerciseId == exerciseId }.orEmpty()

    /** Accepts an event into the run, or immediately commits a standalone event. */
    private suspend fun acceptAttempt(
        session: LessonRunSession?,
        attemptId: String,
        result: ExerciseResult,
        completesExercise: Boolean,
        reviewCard: ReviewCard?,
        day: LocalDate
    ): RecordOutcome {
        if (session != null) {
            if (!session.attemptIds.add(attemptId)) return RecordOutcome.Duplicate
            session.attempts += StagedLessonAttempt(
                attemptId = attemptId,
                result = result,
                completesExercise = completesExercise,
                reviewCard = reviewCard
            )
            return RecordOutcome.Recorded(result.xpAwarded, reviewCard)
        }

        return commit(
            attemptId = attemptId,
            result = result,
            completesExercise = completesExercise,
            reviewCard = reviewCard,
            day = day
        )
    }

    /** The one write path for non-lesson progress. */
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
        return if (committed) RecordOutcome.Recorded(result.xpAwarded, reviewCard) else RecordOutcome.Duplicate
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
                    lesson.exercises.map { exercise -> PlacedExercise(exercise, lessonId = lesson.id, chapterId = chapter.id) }
                }
            }
            .associateBy { it.exercise.id }

    private class LessonRunSession(val lessonId: String) {
        val attempts = mutableListOf<StagedLessonAttempt>()
        val attemptIds = mutableSetOf<String>()
        var settled: Boolean = false
    }

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
