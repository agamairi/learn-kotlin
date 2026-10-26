package dev.agam.learn.fake

import dev.agam.learn.data.ProgressCommit
import dev.agam.learn.data.ProgressRepository
import dev.agam.learn.model.ExerciseResult
import kotlinx.coroutines.CompletableDeferred

/** Progress repository gate for checking UI behavior while a recorder write is suspended. */
class ControlledProgressRepository(
    private val delegate: ProgressRepository
) : ProgressRepository by delegate {
    var gatedExerciseId: String? = null
    val historyReadEntered = CompletableDeferred<String>()
    val releaseHistoryRead = CompletableDeferred<Unit>()
    var gatedHistoryReadCount: Int = 0
        private set
    var throwAfterNextCommit = false

    override suspend fun getExerciseResultsForExercise(exerciseId: String): List<ExerciseResult> {
        if (exerciseId == gatedExerciseId) {
            gatedHistoryReadCount++
            historyReadEntered.complete(exerciseId)
            releaseHistoryRead.await()
        }
        return delegate.getExerciseResultsForExercise(exerciseId)
    }

    override suspend fun commitProgress(commit: ProgressCommit): Boolean {
        val committed = delegate.commitProgress(commit)
        if (throwAfterNextCommit) {
            throwAfterNextCommit = false
            throw IllegalStateException("Simulated lost response after commit")
        }
        return committed
    }
}
