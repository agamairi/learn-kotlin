# Handoff: learn-kotlin

State as of 2026-10-24, main at the v0.1.0-alpha.3 tag plus this file. Written for whoever picks the project up next, for example Codex working in this checkout.

## What it is

An Android app for learning Kotlin, Duolingo-style, aimed at the Stripe Android Terminal interview. It has 12 chapters and 222 exercises: multiple choice, fill in the blank (cloze), predict the output, write a function, and 17 multi-part Stripe-style problems. Code exercises run on a real Kotlin compiler server. It also has XP, a daily goal, a streak with a 4 am rollover and freezes, Leitner review, stats, and an optional bring-your-own-key AI tutor.

- Repo: https://github.com/agamairi/learn-kotlin (public). Local checkout: `/Users/agamairi/development/learn-kotlin`, branch `main`.
- Releases: v0.1.0-alpha.1, alpha.2 and alpha.3 on GitHub Releases. All are debug-signed pre-releases. alpha.3 is feature-complete for v1.
- Plan and rules: `docs/PLAN.md`. Content contract: `docs/CONTENT_SCHEMA.md`. Compiler server findings: `docs/spike-report.md`. AI API facts: `docs/AI_PROVIDERS.md`. Setup for users: `README.md`.

## Modules

| Module | What lives there |
|---|---|
| `:shared` (KMP: android + jvm) | `model/` (course, exercise and progress types), `content/` (CourseLoader), `domain/` (StreakCalculator with freezes, XpPolicy, DailyGoalTracker, ReviewScheduler, AnswerGrader, LessonCompletion), `data/` (SQLDelight repos, **ProgressRecorder**, SettingsRepository, SecretStore with an Android Keystore actual), `ai/` (AiTutor, AiTutorImpl, Anthropic/OpenAI/Gemini clients, Prompts), `runner/` (CodeRunner and LanguageSupport contracts) |
| `:runtime-kotlin` (KMP) | `KotlinCompilerServerRunner` (Ktor client for kotlin-compiler-server) and `KotlinTestFileBuilder` |
| `:content` (JVM) | YAML-to-JSON content compiler, validator, and the live verifier (`ContentVerifier`, `HttpCompilerServerClient`) |
| `:app-android` | Compose UI, `di/DefaultAppContainer.kt` (wires the real implementations), `ai/DynamicAiTutor.kt` and `AiAccess.kt`, `runner/SettingsAwareKotlinCodeRunner.kt`, screens under `ui/` (home, path, lesson, code, problem, review, stats, settings) |

Course content: YAML in `content/kotlin/` (`course.yaml` plus `chapters/ch01..ch12`). The compiled bundle is `app-android/src/main/assets/courses/kotlin-stripe.json`. `preBuild` regenerates it, and it's also committed.

## Commands

Run from the repo root. JDK 21 is needed, and `local.properties` must have `sdk.dir` (it's gitignored and already present here).

```sh
./gradlew build                      # exactly what GitHub CI runs; includes shared tests on the Android host target
./gradlew :app-android:assembleDebug # APK at app-android/build/outputs/apk/debug/app-android-debug.apk
./gradlew :app-android:testDebugUnitTest :shared:jvmTest :runtime-kotlin:jvmTest :content:test
./gradlew :content:compileContent    # YAML -> assets JSON
./gradlew :content:verifyContent     # runs every reference solution and program against the live server; writes docs/content-verification.md
./gradlew :shared:verifySqlDelightMigration
```

The last full run passed: 107 app, 69 shared JVM, 66 shared Android host, 14 runtime and 35 content tests, and 82 of 82 exercises verified live (21 cloze warnings that only need surrounding context).

## Compiler server (needed for code exercises)

- A Docker container named `kotlin-compiler-server` runs on `127.0.0.1:8080`, loopback only on purpose. It is Kotlin 2.5.0-Beta2, built from JetBrains/kotlin-compiler-server at commit `325cdb872475891da66fc4c9b05763de8f011e16`.
- The clone is at `/Users/agamairi/development/tools/kotlin-compiler-server`. Start or rebuild it with `KCS_CLONE=/Users/agamairi/development/tools/kotlin-compiler-server tools/compiler-server/run.sh`. `KCS_CLONE` is required.
- Building it needs JDK 17, which an agent installed with Homebrew (`openjdk@17`). Remove it with `brew uninstall openjdk@17` if you never rebuild the server.
- A stopped container, `dreamairi-cloud-server-api-1`, also maps port 8080. Don't start both.
- The emulator reaches the server at `http://10.0.2.2:8080` (the app's default). A USB phone needs `adb reverse tcp:8080 tcp:8080` and `http://localhost:8080` in Settings. Cleartext HTTP is allowed only to those hosts (`res/xml/network_security_config.xml`).
- Security: there is no auth, learner code runs as root with internet access, and learner code can forge a JUnit result. Keep it single-user and loopback-only. The runner fails closed: missing testResults is a failure, and it requires the exact expected (class, method) pairs with status OK, plain file names, a 200 KB cap and a 30 s timeout.

## Release flow (as done for alpha.1 to alpha.3)

1. Bump `versionCode` and `versionName` in `app-android/build.gradle.kts`.
2. Run `./gradlew build`, then `./gradlew :app-android:assembleDebug`. Check the version with `$ANDROID_SDK/build-tools/<latest>/aapt2 dump badging <apk>`.
3. Commit, then `git push origin main`, `git tag -a v0.1.0-alpha.N -m "..."` and `git push origin v0.1.0-alpha.N`.
4. Copy the APK to `learn-kotlin-<version>-debug.apk` and run `gh release create <tag> <apk> --prerelease --notes-file <notes>`.

## Rules that must keep holding

- **XP:** every XP-awarding action goes through `shared/.../data/ProgressRecorder.kt`. It runs one transaction: the result (lessonId and chapterId always set), daily XP for the effective day, goalMet, review card seeding or rescheduling, and spent streak freezes. It is idempotent per attempt id. Don't write progress from view models directly.
- **Rates:** 10 per card, 25 for a first-try code pass, 15 after a retry, 50 per multi-part part (once), and hints cost 2, 5 and 10. A day counts when the goal is met. Freezes: 1 per 7 goal days, max 2, spent automatically.
- **AI key:** stored only through `SecretStore` (Android Keystore AES-GCM; the ciphertext sits in the settings table). It is never logged, never in URLs, never in `AiSettings`. An undecryptable key, for example after a backup restore, is deleted and treated as no key.
- **Interview mode:** every AI entry point is hidden in the UI and refused in the view model and AiTutorImpl. During a running mock timer it can't be switched off.
- **AI cards:** "More like this" cards are labeled "AI-generated, unverified". They give no XP, never enter review, and are never saved to content.

## Gotchas learned the hard way

- SQLDelight makes one queries class per `.sq` file, so keep one file per table. A column named `value` becomes the Kotlin property `value_`.
- **Schema changes need a migration.** alpha.1 shipped schema v1, and `1.sqm` migrates to v2 (attempt_id plus the streak_freeze table). Put the next change in `2.sqm` and extend `shared/src/jvmTest/.../ExerciseResultMigrationTest.kt`, which builds alpha.1's exact schema.
- commonTest database tests must use `createInMemoryTestDriver()` (commonTest `TestDatabase.kt`, with JDBC actuals for jvmTest and androidHostTest). The Android driver factory needs a Context and fails on the host. Run `./gradlew build` before pushing, not only `jvmTest`.
- Ktor 3.1.1 MockEngine handlers are `suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData`. The request content type sits on `request.body.contentType`, not in the headers.
- `runTest` uses virtual time, so a `withTimeout` around a real MockEngine call fires early. The runner tests wrap their bodies in `Dispatchers.Default`.
- Always rethrow `CancellationException` before broad catches.
- **Content rules:**
  - Hidden tests use JUnit 4 (`org.junit.*`), with top-level test classes and no package line.
  - Multi-part `reference_solution`s are cumulative: each one is the full file through that part.
  - The server's child JVM has a 32 MB heap, so references must not build huge collections.
  - Predict-output comparison ignores trailing whitespace and newlines.
  - Cloze accepted answers must not repeat punctuation that's already in the template.
- antigravity (Gemini Flash) jobs often end "interrupted" or without a final message. Check the files and build yourself. The Codex sandbox can't run Gradle here.

## Not done / next steps

1. **Never run on a device or emulator.** No Android system image is installed (`system-images/android-36/google_apis_playstore` is empty, about 1.5 GB to download). Agam is testing the alpha APKs; expect UI issues to come back from him.
2. **Move the compiler server to a stable Kotlin release** (it's 2.5.0-Beta2 now), rebuild, and re-run `:content:verifyContent`.
3. **Optional:** a hosted runner (the plan mentions Cloud Run with a token). It needs real isolation first; see the security bullet above.
4. **Optional:** swap Leitner for FSRS behind `ReviewScheduler`, or add a second language pack (`docs/PLAN.md`, "Adding a language later").
5. **Housekeeping:** the merged `feat/*` local branches still exist; delete them with `git branch -d` if you like. `allowBackup` is on by design (progress backs up; the key doesn't survive a restore).
