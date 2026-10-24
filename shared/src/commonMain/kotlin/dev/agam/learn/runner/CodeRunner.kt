package dev.agam.learn.runner

/**
 * Execution engine contract for executing and testing code snippets.
 */
interface CodeRunner {
    /**
     * Executes the given source files and returns the execution outcome.
     */
    suspend fun run(files: List<SourceFile>): RunOutcome

    /**
     * Runs tests against the provided implementation files.
     */
    suspend fun test(files: List<SourceFile>, tests: List<SourceFile>): RunOutcome

    /**
     * Performs static analysis and syntax checks returning diagnostics without running code.
     */
    suspend fun diagnostics(files: List<SourceFile>): List<Diagnostic>
}
