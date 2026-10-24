package dev.agam.learn.runner

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Details of a test failure from runner execution.
 */
@Serializable
data class TestFailure(
    @SerialName("test_name")
    val testName: String,
    val message: String
)

/**
 * Sealed outcome of running code or tests against a runner.
 */
@Serializable
sealed interface RunOutcome {

    @Serializable
    @SerialName("passed")
    data class Passed(
        @SerialName("tests_run")
        val testsRun: Int,
        val stdout: String
    ) : RunOutcome

    @Serializable
    @SerialName("tests_failed")
    data class TestsFailed(
        val failures: List<TestFailure>,
        val stdout: String
    ) : RunOutcome

    @Serializable
    @SerialName("compile_error")
    data class CompileError(
        val diagnostics: List<Diagnostic>
    ) : RunOutcome

    @Serializable
    @SerialName("timeout")
    data class Timeout(
        val seconds: Int
    ) : RunOutcome

    @Serializable
    @SerialName("unavailable")
    data class Unavailable(
        val reason: String
    ) : RunOutcome
}
