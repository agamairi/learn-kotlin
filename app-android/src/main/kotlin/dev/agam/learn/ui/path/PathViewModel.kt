package dev.agam.learn.ui.path

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for assembling the chapter and lesson map, determining
 * locked/unlocked state from stored exercise results.
 */
class PathViewModel(
    private val courseRepository: CourseRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    constructor(appContainer: AppContainer) : this(
        courseRepository = appContainer.courseRepository,
        progressRepository = appContainer.progressRepository
    )

    private val _uiState = MutableStateFlow(PathUiState())
    val uiState: StateFlow<PathUiState> = _uiState.asStateFlow()

    fun loadPath(courseId: String = "kotlin-stripe") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val courses = courseRepository.listCourses()
            val course = courses.firstOrNull { it.id == courseId } ?: courses.firstOrNull()

            if (course == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Course '$courseId' not found."
                    )
                }
                return@launch
            }

            val results = progressRepository.getExerciseResults()
            val passedExerciseIds = results.filter { it.correct }.map { it.exerciseId }.toSet()

            var previousLessonCompleted = true
            var totalLessonsCount = 0
            var completedLessonsCount = 0

            val chapterUiModels = course.chapters.mapIndexed { chapterIndex, chapter ->
                var chapterCompletedCount = 0

                val lessonUiModels = chapter.lessons.map { lesson ->
                    totalLessonsCount++

                    val isLessonComplete = lesson.exercises.isNotEmpty() &&
                        lesson.exercises.all { passedExerciseIds.contains(it.id) }

                    val status = when {
                        isLessonComplete -> {
                            chapterCompletedCount++
                            completedLessonsCount++
                            previousLessonCompleted = true
                            LessonStatus.COMPLETED
                        }
                        previousLessonCompleted -> {
                            previousLessonCompleted = false
                            LessonStatus.UNLOCKED
                        }
                        else -> {
                            LessonStatus.LOCKED
                        }
                    }

                    LessonUiModel(
                        id = lesson.id,
                        title = lesson.title,
                        status = status,
                        exerciseCount = lesson.exercises.size
                    )
                }

                ChapterUiModel(
                    id = chapter.id,
                    title = chapter.title,
                    summary = chapter.summary,
                    isUnlocked = chapterIndex == 0 || lessonUiModels.any { it.status != LessonStatus.LOCKED },
                    completedLessonsCount = chapterCompletedCount,
                    totalLessonsCount = chapter.lessons.size,
                    lessons = lessonUiModels
                )
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    courseId = course.id,
                    courseTitle = course.title,
                    courseDescription = course.description,
                    chapters = chapterUiModels,
                    totalLessons = totalLessonsCount,
                    completedLessons = completedLessonsCount
                )
            }
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PathViewModel(appContainer) as T
                }
            }
    }
}
