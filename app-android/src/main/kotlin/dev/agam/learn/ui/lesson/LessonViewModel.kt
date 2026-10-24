package dev.agam.learn.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.di.AppContainer
import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.domain.XpPolicy
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.ExerciseResult
import dev.agam.learn.model.Lesson
import dev.agam.learn.runner.LanguageSupport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel managing the state machine for lesson explanation, exercise progression,
 * grading, XP accumulation, and results presentation.
 */
class LessonViewModel(
    private val courseRepository: CourseRepository,
    private val progressRepository: ProgressRepository,
    private val answerGrader: AnswerGrader,
    private val xpPolicy: XpPolicy,
    val languageSupport: LanguageSupport
) : ViewModel() {

    constructor(appContainer: AppContainer) : this(
        courseRepository = appContainer.courseRepository,
        progressRepository = appContainer.progressRepository,
        answerGrader = appContainer.answerGrader,
        xpPolicy = appContainer.xpPolicy,
        languageSupport = appContainer.languageSupport
    )

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private var activeLesson: Lesson? = null
    private var exercises: List<Exercise> = emptyList()

    /**
     * Loads the target lesson from the curriculum by identifier.
     */
    fun loadLesson(lessonId: String, courseId: String = "kotlin-foundations") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val courses = courseRepository.listCourses()
            val targetCourse = courses.firstOrNull { it.id == courseId } ?: courses.firstOrNull()

            if (targetCourse == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Course '$courseId' not found."
                    )
                }
                return@launch
            }

            var foundLesson: Lesson? = null
            var chapterTitle = ""

            for (chapter in targetCourse.chapters) {
                val lesson = chapter.lessons.firstOrNull { it.id == lessonId }
                if (lesson != null) {
                    foundLesson = lesson
                    chapterTitle = chapter.title
                    break
                }
            }

            if (foundLesson == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Lesson '$lessonId' not found."
                    )
                }
                return@launch
            }

            activeLesson = foundLesson
            exercises = foundLesson.exercises

            _uiState.update {
                it.copy(
                    isLoading = false,
                    courseId = targetCourse.id,
                    lessonId = foundLesson.id,
                    lessonTitle = foundLesson.title,
                    chapterTitle = chapterTitle,
                    step = LessonStep.Explanation(foundLesson.explanation),
                    totalExercisesCount = exercises.size
                )
            }
        }
    }

    /**
     * User clicks "Start Exercises" after reading the lesson explanation.
     */
    fun onStartPractice() {
        if (exercises.isEmpty()) {
            _uiState.update {
                it.copy(
                    step = LessonStep.Completed(
                        totalXp = 0,
                        correctCount = 0,
                        totalCount = 0
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                step = LessonStep.ExercisePlaying(
                    exerciseIndex = 0,
                    totalExercises = exercises.size,
                    exercise = exercises[0]
                ),
                selectedOptionIndex = null,
                clozeAnswers = emptyMap(),
                predictOutputText = "",
                isAnswerChecked = false,
                isAnswerCorrect = null,
                currentExplanation = ""
            )
        }
    }

    fun onOptionSelected(index: Int) {
        if (_uiState.value.isAnswerChecked) return
        _uiState.update { it.copy(selectedOptionIndex = index) }
    }

    fun onClozeAnswerChanged(placeholder: String, value: String) {
        if (_uiState.value.isAnswerChecked) return
        _uiState.update {
            val updatedMap = it.clozeAnswers.toMutableMap()
            updatedMap[placeholder] = value
            it.copy(clozeAnswers = updatedMap)
        }
    }

    fun onPredictOutputChanged(text: String) {
        if (_uiState.value.isAnswerChecked) return
        _uiState.update { it.copy(predictOutputText = text) }
    }

    /**
     * Evaluates the learner's answer against the active exercise.
     */
    fun onCheckAnswer() {
        val currentStep = _uiState.value.step as? LessonStep.ExercisePlaying ?: return
        if (_uiState.value.isAnswerChecked) return

        val exercise = currentStep.exercise
        val (isCorrect, explanation) = when (exercise) {
            is Exercise.MultipleChoice -> {
                val selected = _uiState.value.selectedOptionIndex ?: -1
                val correct = answerGrader.gradeMultipleChoice(exercise, selected)
                correct to exercise.explanation
            }
            is Exercise.Cloze -> {
                val correct = answerGrader.gradeCloze(exercise, _uiState.value.clozeAnswers)
                correct to exercise.explanation
            }
            is Exercise.PredictOutput -> {
                val correct = answerGrader.gradePredictOutput(exercise, _uiState.value.predictOutputText)
                correct to exercise.explanation
            }
            is Exercise.WriteFunction -> {
                // Non-code cards are graded locally; code exercises pass to code runner in code view
                true to (exercise.explanation ?: "Completed code function.")
            }
            is Exercise.MultiPartProblem -> {
                true to "Completed problem step."
            }
        }

        val xp = if (isCorrect) xpPolicy.xpForExercise(exercise, isFirstAttempt = true) else 0

        viewModelScope.launch {
            progressRepository.recordExerciseResult(
                ExerciseResult(
                    exerciseId = exercise.id,
                    correct = isCorrect,
                    xpAwarded = xp,
                    epochMillis = Clock.System.now().toEpochMilliseconds()
                )
            )
        }

        _uiState.update {
            it.copy(
                isAnswerChecked = true,
                isAnswerCorrect = isCorrect,
                currentExplanation = explanation,
                xpEarnedInLesson = it.xpEarnedInLesson + xp,
                correctExercisesCount = if (isCorrect) it.correctExercisesCount + 1 else it.correctExercisesCount
            )
        }
    }

    /**
     * Advances to the next exercise or transitions to the summary completion view.
     */
    fun onContinueNext() {
        val currentStep = _uiState.value.step as? LessonStep.ExercisePlaying ?: return
        val nextIndex = currentStep.exerciseIndex + 1

        if (nextIndex < exercises.size) {
            _uiState.update {
                it.copy(
                    step = LessonStep.ExercisePlaying(
                        exerciseIndex = nextIndex,
                        totalExercises = exercises.size,
                        exercise = exercises[nextIndex]
                    ),
                    selectedOptionIndex = null,
                    clozeAnswers = emptyMap(),
                    predictOutputText = "",
                    isAnswerChecked = false,
                    isAnswerCorrect = null,
                    currentExplanation = ""
                )
            }
        } else {
            // Lesson completed: commit daily activity
            val finalXp = _uiState.value.xpEarnedInLesson
            val correctCount = _uiState.value.correctExercisesCount
            val totalCount = exercises.size

            viewModelScope.launch {
                val now = Clock.System.now()
                val todayEpochDay = now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()
                val existingActivity = progressRepository.getDailyActivity(todayEpochDay)
                val newXp = (existingActivity?.xpEarned ?: 0) + finalXp
                val newCompleted = (existingActivity?.exercisesCompleted ?: 0) + totalCount
                val target = 50 // default daily target

                progressRepository.recordDailyActivity(
                    DailyActivity(
                        epochDay = todayEpochDay,
                        xpEarned = newXp,
                        exercisesCompleted = newCompleted,
                        goalMet = newXp >= target
                    )
                )
            }

            _uiState.update {
                it.copy(
                    step = LessonStep.Completed(
                        totalXp = finalXp,
                        correctCount = correctCount,
                        totalCount = totalCount
                    ),
                    isAnswerChecked = false
                )
            }
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LessonViewModel(appContainer) as T
                }
            }
    }
}
