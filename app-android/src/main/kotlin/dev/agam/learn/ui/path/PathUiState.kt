package dev.agam.learn.ui.path

/**
 * Progression status of an individual lesson in the learning path.
 */
enum class LessonStatus {
    COMPLETED,
    UNLOCKED,
    LOCKED
}

/**
 * Presentation model for a lesson row in the curriculum path.
 */
data class LessonUiModel(
    val id: String,
    val title: String,
    val status: LessonStatus,
    val exerciseCount: Int
)

/**
 * Presentation model for a chapter grouping in the curriculum path.
 */
data class ChapterUiModel(
    val id: String,
    val title: String,
    val summary: String,
    val isUnlocked: Boolean,
    val completedLessonsCount: Int,
    val totalLessonsCount: Int,
    val lessons: List<LessonUiModel>
)

/**
 * UI state for the Path curriculum overview screen.
 */
data class PathUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val courseId: String = "",
    val courseTitle: String = "",
    val courseDescription: String = "",
    val chapters: List<ChapterUiModel> = emptyList(),
    val totalLessons: Int = 0,
    val completedLessons: Int = 0
) {
    val overallProgressFraction: Float
        get() = if (totalLessons > 0) completedLessons.toFloat() / totalLessons else 0f
}
