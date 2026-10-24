package dev.agam.learn.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result of an individual attempt on an exercise.
 */
@Serializable
data class ExerciseResult(
    @SerialName("exercise_id")
    val exerciseId: String,
    val correct: Boolean,
    @SerialName("xp_awarded")
    val xpAwarded: Int,
    @SerialName("epoch_millis")
    val epochMillis: Long,
    @SerialName("lesson_id")
    val lessonId: String? = null,
    @SerialName("chapter_id")
    val chapterId: String? = null
)

/**
 * Aggregated daily activity record for tracking streak and daily goals.
 */
@Serializable
data class DailyActivity(
    @SerialName("epoch_day")
    val epochDay: Long,
    @SerialName("xp_earned")
    val xpEarned: Int,
    @SerialName("exercises_completed")
    val exercisesCompleted: Int,
    @SerialName("goal_met")
    val goalMet: Boolean
)

/**
 * A flashcard in the Leitner spaced repetition queue.
 */
@Serializable
data class ReviewCard(
    @SerialName("item_id")
    val itemId: String,
    val box: Int,
    @SerialName("due_epoch_day")
    val dueEpochDay: Long
)
