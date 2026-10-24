package dev.agam.learn.fake

import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.domain.DefaultXpPolicy
import dev.agam.learn.domain.LeitnerScheduler
import dev.agam.learn.model.Chapter
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.Lesson
import dev.agam.learn.runner.LanguageId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/** A clock the test moves by hand. */
class FixedClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** The real progress path, over in-memory fakes, with the real XP and review rules. */
fun progressRecorderFor(
    progress: FakeProgressRepository,
    courses: List<Course>,
    settings: SettingsRepository = FakeSettingsRepository(),
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): ProgressRecorder = ProgressRecorder(
    progressRepository = progress,
    courseRepository = FakeCourseRepository(courses),
    settingsRepository = settings,
    reviewScheduler = LeitnerScheduler(),
    xpPolicy = DefaultXpPolicy(),
    clock = clock,
    timeZone = timeZone
)

/** A course with one chapter and one lesson that holds [exercises]. */
fun courseOf(
    vararg exercises: Exercise,
    lessonId: String = "lesson-test",
    chapterId: String = "ch-test"
): Course = Course(
    id = "kotlin-stripe",
    language = LanguageId.KOTLIN,
    title = "Test course",
    description = "Course for progress tests",
    chapters = listOf(
        Chapter(
            id = chapterId,
            title = "Test chapter",
            summary = "",
            lessons = listOf(
                Lesson(
                    id = lessonId,
                    title = "Test lesson",
                    explanation = "",
                    exercises = exercises.toList()
                )
            )
        )
    )
)
