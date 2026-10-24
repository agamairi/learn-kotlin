package dev.agam.learn.ui.home

/**
 * Information on the active/next lesson recommended for continuation.
 */
data class ContinueLessonInfo(
    val courseId: String,
    val lessonId: String,
    val lessonTitle: String,
    val chapterTitle: String,
    val exerciseCount: Int
)

/**
 * UI state for the Home dashboard screen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val streakDays: Int = 0,
    /** Streak freezes available to cover a missed day. */
    val heldStreakFreezes: Int = 0,
    val isStreakActiveToday: Boolean = false,
    val todayXp: Int = 0,
    val dailyGoalXp: Int = 50,
    val goalProgressRatio: Float = 0f,
    val isGoalMet: Boolean = false,
    val reviewDueCount: Int = 0,
    val continueLesson: ContinueLessonInfo? = null
)
