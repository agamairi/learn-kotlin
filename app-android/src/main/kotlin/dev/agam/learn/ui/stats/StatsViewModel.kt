package dev.agam.learn.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.di.AppContainer
import dev.agam.learn.model.DailyActivity
import dev.agam.learn.model.ExerciseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel aggregating XP history, streak calculation, and chapter accuracy metrics.
 */
class StatsViewModel(
    private val progressRepository: ProgressRepository,
    private val settingsRepository: SettingsRepository,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

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

            val totalXp = activities.sumOf { it.xpEarned }
            val totalExercises = activities.sumOf { it.exercisesCompleted }

            // Streak calculation: count consecutive active days leading to today/yesterday
            val currentEpochDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
            val activeEpochDays = activities.filter { it.xpEarned > 0 || it.goalMet }.map { it.epochDay }.toSet()

            var streak = 0
            var checkDay = currentEpochDay
            if (!activeEpochDays.contains(checkDay)) {
                checkDay -= 1 // Allow for activity yesterday
            }
            while (activeEpochDays.contains(checkDay)) {
                streak++
                checkDay--
            }

            // Longest streak
            var longest = 0
            var temp = 0
            val sortedActive = activeEpochDays.sorted()
            var prevDay: Long? = null
            for (day in sortedActive) {
                if (prevDay == null || day == prevDay + 1) {
                    temp++
                } else {
                    temp = 1
                }
                if (temp > longest) longest = temp
                prevDay = day
            }

            // Recent 7 days for the bar chart
            val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val recentDays = (6 downTo 0).map { offset ->
                val targetDay = currentEpochDay - offset
                val activity = activities.find { it.epochDay == targetDay }
                val dayOfWeekIndex = ((targetDay + 4) % 7).toInt().let { if (it < 0) it + 7 else it }
                val label = dayLabels[dayOfWeekIndex]
                DailyXp(
                    dayLabel = label,
                    xp = activity?.xpEarned ?: 0,
                    goalMet = (activity?.xpEarned ?: 0) >= dailyGoalSetting
                )
            }

            // Calendar grid for recent 28 days
            val calendarDays = (27 downTo 0).map { offset ->
                val day = currentEpochDay - offset
                val dayNum = ((day % 31) + 1).toInt()
                CalendarDay(
                    dayOfMonth = dayNum,
                    isActive = activeEpochDays.contains(day),
                    isToday = day == currentEpochDay
                )
            }

            // Chapter accuracy
            val accuracies = computeChapterAccuracies(results)

            _uiState.update {
                it.copy(
                    totalXp = totalXp,
                    totalExercisesCompleted = totalExercises,
                    currentStreak = streak,
                    longestStreak = maxOf(longest, streak),
                    dailyGoalXp = dailyGoalSetting,
                    recentXpHistory = recentDays,
                    calendarDays = calendarDays,
                    chapterAccuracies = accuracies,
                    isLoading = false
                )
            }
        }
    }

    private fun computeChapterAccuracies(results: List<ExerciseResult>): List<ChapterAccuracy> {
        val chapterDefs = listOf(
            Triple("ch01", "Syntax Basics", setOf("ch01", "syntax")),
            Triple("ch02", "Strings & Text", setOf("ch02", "strings")),
            Triple("ch03", "Collections", setOf("ch03", "collections", "groupby")),
            Triple("ch04", "Testing", setOf("ch04", "testing")),
            Triple("ch05", "Data Modeling", setOf("ch05", "modeling")),
            Triple("ch06", "Time & Graphs", setOf("ch06", "time", "graphs")),
            Triple("ch07", "Idioms", setOf("ch07", "idioms")),
            Triple("ch08", "Mobile Kotlin", setOf("ch08", "mobile")),
            Triple("ch09", "Stripe Classics", setOf("ch09", "stripe", "terminal", "problem"))
        )

        // Only chapters with attempts are listed; the screen shows an empty state otherwise.
        return chapterDefs.mapNotNull { (id, title, prefixes) ->
            val matching = results.filter { r ->
                prefixes.any { p -> r.exerciseId.contains(p, ignoreCase = true) }
            }

            if (matching.isNotEmpty()) {
                val attempted = matching.size
                val correct = matching.count { it.correct }
                val pct = (correct * 100) / attempted
                ChapterAccuracy(
                    chapterId = id,
                    chapterTitle = title,
                    attempted = attempted,
                    correct = correct,
                    accuracyPercent = pct
                )
            } else {
                null
            }
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StatsViewModel(
                        progressRepository = appContainer.progressRepository,
                        settingsRepository = appContainer.settingsRepository
                    ) as T
                }
            }
    }
}
