package dev.agam.learn.runner

import dev.agam.learn.data.SettingsRepository
import dev.agam.learn.runtime.kotlin.KotlinCompilerServerRunner
import io.ktor.client.HttpClient

/**
 * [CodeRunner] that delegates to [KotlinCompilerServerRunner] configured with the current
 * server URL and optional bearer token from [SettingsRepository], defaulting to http://10.0.2.2:8080.
 * One [HttpClient] is reused across runs; a new runner is built per call so settings changes apply
 * immediately.
 */
class SettingsAwareKotlinCodeRunner(
    private val settingsRepository: SettingsRepository,
    private val httpClient: HttpClient = HttpClient(),
    private val defaultBaseUrl: String = "http://10.0.2.2:8080"
) : CodeRunner {

    private suspend fun currentRunner(): KotlinCompilerServerRunner {
        val settings = settingsRepository.getSettings()
        val url = settings.compilerServerUrl.ifBlank { defaultBaseUrl }
        val token = settings.compilerServerToken?.ifBlank { null }
        return KotlinCompilerServerRunner(baseUrl = url, token = token, httpClient = httpClient)
    }

    override suspend fun run(files: List<SourceFile>): RunOutcome =
        currentRunner().run(files)

    override suspend fun test(files: List<SourceFile>, tests: List<SourceFile>): RunOutcome =
        currentRunner().test(files, tests)

    override suspend fun diagnostics(files: List<SourceFile>): List<Diagnostic> =
        currentRunner().diagnostics(files)
}
