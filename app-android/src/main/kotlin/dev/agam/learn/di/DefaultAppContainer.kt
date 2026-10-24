package dev.agam.learn.di

import android.content.Context
import dev.agam.learn.content.CourseRepository
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.domain.AnswerGrader
import dev.agam.learn.domain.DailyGoalTracker
import dev.agam.learn.domain.ReviewScheduler
import dev.agam.learn.domain.StreakCalculator
import dev.agam.learn.domain.XpPolicy
import dev.agam.learn.fake.FakeAnswerGrader
import dev.agam.learn.fake.FakeCourseRepository
import dev.agam.learn.fake.FakeDailyGoalTracker
import dev.agam.learn.fake.FakeProgressRepository
import dev.agam.learn.fake.FakeReviewScheduler
import dev.agam.learn.fake.FakeStreakCalculator
import dev.agam.learn.fake.FakeXpPolicy
import dev.agam.learn.runner.LanguageSupport
import dev.agam.learn.runtime.kotlin.KotlinLanguageSupport

/**
 * Default container providing in-memory fake repositories and domain services.
 */
class DefaultAppContainer(
    context: Context? = null
) : AppContainer {

    override val courseRepository: CourseRepository by lazy {
        FakeCourseRepository.fromAssetsOrSample(context)
    }

    override val progressRepository: ProgressRepository by lazy {
        FakeProgressRepository()
    }

    override val streakCalculator: StreakCalculator by lazy {
        FakeStreakCalculator()
    }

    override val xpPolicy: XpPolicy by lazy {
        FakeXpPolicy()
    }

    override val dailyGoalTracker: DailyGoalTracker by lazy {
        FakeDailyGoalTracker()
    }

    override val reviewScheduler: ReviewScheduler by lazy {
        FakeReviewScheduler()
    }

    override val answerGrader: AnswerGrader by lazy {
        FakeAnswerGrader()
    }

    override val languageSupport: LanguageSupport by lazy {
        KotlinLanguageSupport()
    }
}
