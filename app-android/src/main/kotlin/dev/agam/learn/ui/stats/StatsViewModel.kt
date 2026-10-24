package dev.agam.learn.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.di.AppContainer
import dev.agam.learn.model.Course
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ExerciseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** The course whose chapters Stats lists. */
private const val STATS_COURSE_ID = "kotlin-stripe"

/**
 * ViewModel aggregating XP history, the streak and its frozen days, and per-chapter accuracy.
 * Days are effective learning days, and the streak is the same evaluation Home shows.
 */
class StatsViewModel(
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
    private val courseRepository: CourseRepository,
    private val progressRecorder: ProgressRecorder,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    constructor(appContainer: AppContainer) : this(
        progressRepository = appContainer.progressRepository,
        settingsRepository = appContainer.settingsRepository,
        courseRepository = appContainer.courseRepository,
        progressRecorder = appContainer.progressRecorder
    )

    private val scope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(StatsUiState(isLoading = true))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        _uiState.update { it.copy(isLoading = true) }

        scope.launch {
            val activities = progressRepository.getDailyActivities().sortedBy { it.epochDay }
            val results = progressRepository.getExerciseResults()
            val dailyGoalSetting = settingsRepository.getSettings().dailyGoalXp
            val courses = courseRepository.listCourses()

            val totalXp = activities.sumOf { it.xpEarned }
            val totalExercises = activities.sumOf { it.exercisesCompleted }

            val streak = progressRecorder.refreshStreak()
            val today = progressRecorder.today()
            val goalDays = activities.filter { it.goalMet }.mapTo(mutableSetOf()) { it.epochDay }
            val frozenDays = streak.frozenDates.mapTo(mutableSetOf()) { it.toEpochDays().toLong() }

            // Recent 7 days for the bar chart
            val recentDays = (6 downTo 0).map { offset ->
                val targetDay = today - offset
                val xp = activities.firstOrNull { it.epochDay == targetDay }?.xpEarned ?: 0
                DailyXp(
                    dayLabel = dayLabel(targetDay),
                    xp = xp,
                    goalMet = xp >= dailyGoalSetting
                )
            }

            // Calendar grid for the last 28 days, labelled with the real day of month
            val calendarDays = (27 downTo 0).map { offset ->
                val day = today - offset
                CalendarDay(
                    dayOfMonth = LocalDate.fromEpochDays(day.toInt()).dayOfMonth,
                    isActive = day in goalDays,
                    isToday = day == today,
                    isFrozen = day in frozenDays
                )
            }

            _uiState.update {
                it.copy(
                    totalXp = totalXp,
                    totalExercisesCompleted = totalExercises,
                    currentStreak = streak.currentStreak,
                    longestStreak = streak.longestStreak,
                    heldStreakFreezes = streak.heldFreezes,
                    dailyGoalXp = dailyGoalSetting,
                    recentXpHistory = recentDays,
                    calendarDays = calendarDays,
                    chapterAccuracies = computeChapterAccuracies(courses, results),
                    isLoading = false
                )
            }
        }
    }

    /**
     * One row for every chapter of the course, including chapters with no attempts yet. An attempt is
     * counted in the chapter its stored result names; results saved before chapters were stored are
     * matched to their chapter through the course.
     */
    private fun computeChapterAccuracies(
        courses: List<Course>,
        results: List<ExerciseResult>
    ): List<ChapterAccuracy> {
        val course = courses.firstOrNull { it.id == STATS_COURSE_ID } ?: courses.firstOrNull() ?: return emptyList()
        val chapterOfExercise = course.chapters.flatMap { chapter ->
            chapter.lessons.flatMap { lesson ->
                lesson.exercises.flatMap { exercise ->
                    val partIds = (exercise as? Exercise.MultiPartProblem)?.parts?.map { it.id }.orEmpty()
                    (partIds + exercise.id).map { id -> id to chapter.id }
                }
            }
        }.toMap()

        return course.chapters.map { chapter ->
            val attempts = results.filter { result ->
                (result.chapterId ?: chapterOfExercise[result.exerciseId]) == chapter.id
            }
            val attempted = attempts.size
            val correct = attempts.count { it.correct }
            ChapterAccuracy(
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                attempted = attempted,
                correct = correct,
                accuracyPercent = if (attempted == 0) 0 else (correct * 100) / attempted
            )
        }
    }

    private fun dayLabel(epochDay: Long): String = when (LocalDate.fromEpochDays(epochDay.toInt()).dayOfWeek) {
        DayOfWeek.SUNDAY -> "Sun"
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StatsViewModel(appContainer) as T
                }
            }
    }
}
