package dev.agam.learn.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.di.AppContainer
import dev.agam.learn.domain.DailyGoalTracker
import dev.agam.learn.domain.isLessonComplete
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home dashboard: streak and held freezes, today's XP against the saved goal,
 * the review count due, and the next lesson to continue. Every day here is the effective learning
 * day (04:00 rollover), and the streak comes from the same evaluation Stats uses.
 */
class HomeViewModel(
    private val courseRepository: CourseRepository,
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
    private val progressRecorder: ProgressRecorder,
    private val dailyGoalTracker: DailyGoalTracker
) : ViewModel() {

    constructor(appContainer: AppContainer) : this(
        courseRepository = appContainer.courseRepository,
        progressRepository = appContainer.progressRepository,
        settingsRepository = appContainer.settingsRepository,
        progressRecorder = appContainer.progressRecorder,
        dailyGoalTracker = appContainer.dailyGoalTracker
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val today = progressRecorder.today()
            val goalXp = settingsRepository.getSettings().dailyGoalXp

            // 1. Today's XP against the saved goal
            val todayXp = progressRepository.getDailyActivity(today)?.xpEarned ?: 0
            val goalRatio = dailyGoalTracker.progressRatio(todayXp, goalXp)
            val isGoalMet = dailyGoalTracker.isGoalMet(todayXp, goalXp)

            // 2. Streak and held freezes (this also spends a freeze on a day that just ended)
            val streak = progressRecorder.refreshStreak()

            // 3. Due review count
            val reviewCount = progressRepository.getDueReviewCards(today).size

            // 4. Continue Lesson recommendation: the first lesson that is not complete
            val courses = courseRepository.listCourses()
            val mainCourse = courses.firstOrNull()
            var continueLessonInfo: ContinueLessonInfo? = null

            if (mainCourse != null) {
                val results = progressRepository.getExerciseResults()
                val passedExerciseIds = results.filter { it.correct }.map { it.exerciseId }.toSet()
                val savedCompletions = progressRepository.getCompletedLessonIds()
                findLoop@ for (chapter in mainCourse.chapters) {
                    for (lesson in chapter.lessons) {
                        if (!isLessonComplete(lesson, savedCompletions, passedExerciseIds)) {
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
                    streakDays = streak.currentStreak,
                    heldStreakFreezes = streak.heldFreezes,
                    isStreakActiveToday = isGoalMet,
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
