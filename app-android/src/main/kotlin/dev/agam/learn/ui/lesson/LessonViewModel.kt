package dev.agam.learn.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ExerciseAttempt
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.data.newAttemptId
import dev.agam.learn.di.AppContainer
import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.model.Exercise
import dev.agam.learn.model.Lesson
import dev.agam.learn.runner.LanguageSupport
import dev.agam.learn.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing the state machine for lesson explanation, exercise progression,
 * grading, XP accumulation, and results presentation.
 *
 * Results are written through [ProgressRecorder]. Code drills and multi-part problems are graded in
 * their own studio screens, which record their attempts; this screen only advances when it finds a
 * passing stored result for them.
 */
class LessonViewModel(
    private val courseRepository: CourseRepository,
    private val progressRepository: ProgressRepository,
    private val progressRecorder: ProgressRecorder,
    private val answerGrader: AnswerGrader,
    val languageSupport: LanguageSupport
) : ViewModel() {

    constructor(appContainer: AppContainer) : this(
        courseRepository = appContainer.courseRepository,
        progressRepository = appContainer.progressRepository,
        progressRecorder = appContainer.progressRecorder,
        answerGrader = appContainer.answerGrader,
        languageSupport = appContainer.languageSupport
    )

    private val _uiState = MutableStateFlow(LessonUiState())
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private var activeLesson: Lesson? = null
    private var exercises: List<Exercise> = emptyList()

    /** Identifies this visit to the lesson. Attempt ids are built from it, so each attempt is recorded once. */
    private val runId: String = newAttemptId()

    /** When this visit started. The lesson's XP is the XP recorded for it since then. */
    private var runStartedAtMillis: Long = 0L

    /**
     * Route of the studio screen that completes [exercise], or null for exercises the lesson grades itself.
     */
    fun routeForExercise(exercise: Exercise): String? = when (exercise) {
        is Exercise.WriteFunction -> Screen.CodeExercise.createRoute(exercise.id)
        is Exercise.MultiPartProblem -> Screen.Problem.createRoute(exercise.id)
        else -> null
    }

    /**
     * Loads the target lesson from the curriculum by identifier.
     */
    fun loadLesson(lessonId: String, courseId: String = "kotlin-stripe") {
        runStartedAtMillis = progressRecorder.now().toEpochMilliseconds()
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
        // Code and problem exercises are graded in their studio screens; the lesson records their result.
        if (routeForExercise(exercise) != null) return

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
            is Exercise.WriteFunction, is Exercise.MultiPartProblem -> return
        }

        // Mark the answer checked before recording, so a second tap cannot record it again.
        _uiState.update {
            it.copy(
                isAnswerChecked = true,
                isAnswerCorrect = isCorrect,
                currentExplanation = explanation,
                correctExercisesCount = if (isCorrect) it.correctExercisesCount + 1 else it.correctExercisesCount
            )
        }

        viewModelScope.launch {
            progressRecorder.record(
                ExerciseAttempt(
                    attemptId = "$runId:${exercise.id}",
                    exerciseId = exercise.id,
                    correct = isCorrect
                )
            )
            refreshLessonXp()
        }
    }

    /**
     * Called when a studio screen (code drill or multi-part problem) returns after a passing check.
     * The studio has already recorded the attempt, so this only advances the lesson, and only when
     * the stored results say the exercise passed.
     */
    fun onStudioExerciseCompleted(exerciseId: String) {
        val currentStep = _uiState.value.step as? LessonStep.ExercisePlaying ?: return
        if (currentStep.exercise.id != exerciseId || _uiState.value.isAnswerChecked) return

        viewModelScope.launch {
            val passed = progressRepository.getExerciseResultsForExercise(exerciseId).any { it.correct }
            if (!passed) return@launch
            val xp = lessonXpThisRun()
            _uiState.update { state ->
                val playing = state.step as? LessonStep.ExercisePlaying
                if (playing == null || playing.exercise.id != exerciseId || state.isAnswerChecked) {
                    return@update state
                }
                state.copy(
                    isAnswerChecked = true,
                    isAnswerCorrect = true,
                    currentExplanation = when (val exercise = playing.exercise) {
                        is Exercise.WriteFunction -> exercise.explanation ?: "Completed code function."
                        is Exercise.MultiPartProblem -> "Completed problem."
                        else -> "Exercise completed."
                    },
                    xpEarnedInLesson = xp,
                    correctExercisesCount = state.correctExercisesCount + 1
                )
            }
        }
    }

    /** XP recorded for this lesson since this visit started, read from the stored results. */
    private suspend fun lessonXpThisRun(): Int {
        val lessonId = activeLesson?.id ?: return 0
        return progressRepository.getExerciseResultsForLesson(lessonId)
            .filter { it.epochMillis >= runStartedAtMillis }
            .sumOf { it.xpAwarded }
    }

    private suspend fun refreshLessonXp() {
        val xp = lessonXpThisRun()
        _uiState.update { state ->
            val step = state.step
            state.copy(
                xpEarnedInLesson = xp,
                step = if (step is LessonStep.Completed) step.copy(totalXp = xp) else step
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
            // The last exercise is done: save the lesson as complete, then show the summary.
            val lessonId = activeLesson?.id
            val correctCount = _uiState.value.correctExercisesCount
            val totalCount = exercises.size

            _uiState.update {
                it.copy(
                    step = LessonStep.Completed(
                        totalXp = it.xpEarnedInLesson,
                        correctCount = correctCount,
                        totalCount = totalCount
                    ),
                    isAnswerChecked = false
                )
            }

            if (lessonId != null) {
                viewModelScope.launch {
                    progressRecorder.completeLesson(lessonId)
                    refreshLessonXp()
                }
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
