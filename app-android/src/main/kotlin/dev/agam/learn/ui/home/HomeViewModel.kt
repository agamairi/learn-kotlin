package dev.agam.learn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.di.AppContainer
import dev.agam.learn.domain.DailyGoalTracker
import dev.agam.learn.domain.StreakCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel for the Home dashboard, coordinating streak calculation, daily goal progress,
 * due review count, and continue learning suggestions.
 */
class HomeViewModel(
    private val courseRepository: CourseRepository,
    private val progressRepository: ProgressRepository,
    private val streakCalculator: StreakCalculator,
    private val dailyGoalTracker: DailyGoalTracker
) : ViewModel() {

    constructor(appContainer: AppContainer) : this(
        courseRepository = appContainer.courseRepository,
        progressRepository = appContainer.progressRepository,
        streakCalculator = appContainer.streakCalculator,
        dailyGoalTracker = appContainer.dailyGoalTracker
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val now = Clock.System.now()
            val timeZone = TimeZone.currentSystemDefault()
            val todayEpochDay = now.toLocalDateTime(timeZone).date.toEpochDays().toLong()

            // 1. Goal and Today's XP
            val todayActivity = progressRepository.getDailyActivity(todayEpochDay)
            val todayXp = todayActivity?.xpEarned ?: 0
            val goalXp = 50
            val goalRatio = dailyGoalTracker.progressRatio(todayXp, goalXp)
            val isGoalMet = dailyGoalTracker.isGoalMet(todayXp, goalXp)

            // 2. Streak
            val results = progressRepository.getExerciseResults()
            val timestamps = results.map { Instant.fromEpochMilliseconds(it.epochMillis) }
            val streakDays = streakCalculator.calculateStreak(timestamps, now, timeZone)

            // 3. Due review count
            val dueCards = progressRepository.getDueReviewCards(todayEpochDay)
            val reviewCount = dueCards.size

            // 4. Continue Lesson recommendation
            val courses = courseRepository.listCourses()
            val mainCourse = courses.firstOrNull()
            var continueLessonInfo: ContinueLessonInfo? = null

            if (mainCourse != null) {
                val passedExerciseIds = results.filter { it.correct }.map { it.exerciseId }.toSet()
                findLoop@ for (chapter in mainCourse.chapters) {
                    for (lesson in chapter.lessons) {
                        val isComplete = lesson.exercises.isNotEmpty() &&
                            lesson.exercises.all { passedExerciseIds.contains(it.id) }
                        if (!isComplete) {
                            continueLessonInfo = ContinueLessonInfo(
                                courseId = mainCourse.id,
                                lessonId = lesson.id,
                                lessonTitle = lesson.title,
                                chapterTitle = chapter.title,
                                exerciseCount = lesson.exercises.size
                            )
                            break@findLoop
                        }
                    }
                }

                // If all lessons completed, default back to first lesson
                if (continueLessonInfo == null && mainCourse.chapters.isNotEmpty() && mainCourse.chapters[0].lessons.isNotEmpty()) {
                    val firstLesson = mainCourse.chapters[0].lessons[0]
                    continueLessonInfo = ContinueLessonInfo(
                        courseId = mainCourse.id,
                        lessonId = firstLesson.id,
                        lessonTitle = firstLesson.title,
                        chapterTitle = mainCourse.chapters[0].title,
                        exerciseCount = firstLesson.exercises.size
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    streakDays = streakDays,
                    isStreakActiveToday = todayXp > 0,
                    todayXp = todayXp,
                    dailyGoalXp = goalXp,
                    goalProgressRatio = goalRatio,
                    isGoalMet = isGoalMet,
                    reviewDueCount = reviewCount,
                    continueLesson = continueLessonInfo
                )
            }
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(appContainer) as T
                }
            }
    }
}
