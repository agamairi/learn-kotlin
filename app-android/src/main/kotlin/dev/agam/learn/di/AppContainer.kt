package dev.agam.learn.di

import dev.agam.learn.ai.AiTutor
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ProgressRecorder
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.data.SecretStore
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.domain.DailyGoalTracker
import dev.agam.learn.domain.ReviewScheduler
import dev.agam.learn.domain.StreakCalculator
import dev.agam.learn.domain.XpPolicy
import dev.agam.learn.runner.LanguageSupport

/**
 * Dependency injection container interface for the learn-kotlin application.
 */
interface AppContainer {
    val courseRepository: CourseRepository
    val progressRepository: ProgressRepository
    /** The one path that records learner progress. Every XP-awarding screen uses it. */
    val progressRecorder: ProgressRecorder
    val settingsRepository: SettingsRepository
    val secretStore: SecretStore
    val streakCalculator: StreakCalculator
    val xpPolicy: XpPolicy
    val dailyGoalTracker: DailyGoalTracker
    val reviewScheduler: ReviewScheduler
    val answerGrader: AnswerGrader
    val languageSupport: LanguageSupport
    val aiTutor: AiTutor
}
