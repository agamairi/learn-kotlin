package dev.agam.learn.di

import android.content.Context
import dev.agam.learn.ai.AiTutor
import dev.agam.learn.ai.DynamicAiTutor
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.AssetCourseRepository
import dev.agam.learn.data.LearnDatabase
import dev.agam.learn.data.LearnDatabaseDriverFactory
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.data.SecretStore
import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.data.SqlDelightProgressRepository
import dev.agam.learn.data.SqlDelightSettingsRepository
import dev.agam.learn.data.createSecretStore
import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.domain.DailyGoalTracker
import dev.agam.learn.domain.DefaultAnswerGrader
import dev.agam.learn.domain.DefaultDailyGoalTracker
import dev.agam.learn.domain.DefaultStreakCalculator
import dev.agam.learn.domain.DefaultXpPolicy
import dev.agam.learn.domain.LeitnerScheduler
import dev.agam.learn.domain.ReviewScheduler
import dev.agam.learn.domain.StreakCalculator
import dev.agam.learn.domain.XpPolicy
import dev.agam.learn.runner.LanguageSupport
import dev.agam.learn.runner.SettingsAwareKotlinCodeRunner
import dev.agam.learn.runtime.kotlin.KotlinLanguageSupport

/**
 * Production dependency injection container providing real persistent storage,
 * assets-backed course discovery, SQLDelight database drivers, Android Keystore SecretStore,
 * compiler runner, and BYOK AI tutor.
 */
class DefaultAppContainer(
    context: Context? = null,
    databaseName: String = "learn.db"
) : AppContainer {

    private val appContext: Context = requireNotNull(context) {
        "Context must be provided to DefaultAppContainer"
    }.applicationContext

    private val database: LearnDatabase by lazy {
        val driver = LearnDatabaseDriverFactory(appContext).createDriver(databaseName)
        LearnDatabase(driver)
    }

    override val courseRepository: CourseRepository by lazy {
        AssetCourseRepository(appContext)
    }

    override val progressRepository: ProgressRepository by lazy {
        SqlDelightProgressRepository(database)
    }

    override val settingsRepository: SettingsRepository by lazy {
        SqlDelightSettingsRepository(database)
    }

    override val secretStore: SecretStore by lazy {
        createSecretStore(database)
    }

    override val streakCalculator: StreakCalculator by lazy {
        DefaultStreakCalculator()
    }

    override val xpPolicy: XpPolicy by lazy {
        DefaultXpPolicy()
    }

    override val dailyGoalTracker: DailyGoalTracker by lazy {
        DefaultDailyGoalTracker()
    }

    override val reviewScheduler: ReviewScheduler by lazy {
        LeitnerScheduler()
    }

    override val answerGrader: AnswerGrader by lazy {
        DefaultAnswerGrader()
    }

    override val languageSupport: LanguageSupport by lazy {
        KotlinLanguageSupport(
            runner = SettingsAwareKotlinCodeRunner(settingsRepository)
        )
    }

    override val aiTutor: AiTutor by lazy {
        DynamicAiTutor(settingsRepository, secretStore)
    }
}
