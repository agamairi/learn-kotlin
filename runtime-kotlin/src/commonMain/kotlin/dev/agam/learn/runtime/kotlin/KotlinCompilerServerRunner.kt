package dev.agam.learn.runtime.kotlin

import dev.agam.learn.runner.CodeRunner
import dev.agam.learn.runner.Diagnostic
import dev.agam.learn.runner.RunOutcome
import dev.agam.learn.runner.SourceFile

/**
 * Runner implementation that delegates code execution, testing, and diagnostics to an instance
 * of JetBrains `kotlin-compiler-server`.
 *
 * For full endpoint protocol contracts, sandboxing parameters, resource limits, and latency spikes,
 * see `docs/spike-report.md`.
 *
 * @param baseUrl Base URL of the kotlin-compiler-server endpoint (e.g. "http://10.0.2.2:8080").
 * @param token Optional bearer token for authentication when connecting to deployed runners.
 */
class KotlinCompilerServerRunner(
    val baseUrl: String,
    val token: String? = null
) : CodeRunner {

    override suspend fun run(files: List<SourceFile>): RunOutcome {
        TODO("Execute code against POST /api/compiler/run as specified in docs/spike-report.md")
    }

    override suspend fun test(files: List<SourceFile>, tests: List<SourceFile>): RunOutcome {
        TODO("Execute JUnit test suite against POST /api/compiler/test as specified in docs/spike-report.md")
    }

    override suspend fun diagnostics(files: List<SourceFile>): List<Diagnostic> {
        TODO("Fetch static analysis diagnostics against POST /api/compiler/highlight as specified in docs/spike-report.md")
    }
}
