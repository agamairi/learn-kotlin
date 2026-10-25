# Handoff: learn-kotlin

State as of 2026-10-25, updated for v0.1.3. Written for whoever picks the project up next, for example Codex working in this checkout.

## What it is

An Android app for learning Kotlin, Duolingo-style, aimed at the Stripe Android Terminal interview. It has 12 chapters and 222 exercises: multiple choice, fill in the blank (cloze), predict the output, write a function, and 17 multi-part Stripe-style problems. Code exercises run on a real Kotlin compiler server. It also has XP, a daily goal, a streak with a 4 am rollover and freezes, Leitner review, stats, and an optional bring-your-own-key AI tutor.

- Repo: https://github.com/agamairi/learn-kotlin (public). Local checkout: `/Users/agamairi/development/learn-kotlin`, branch `main`.
- Releases: v0.1.0-alpha.1, alpha.2 and alpha.3 are debug-signed pre-releases. v0.1.0 is the first stable release for personal use, adds OpenRouter, and uses a non-debuggable release APK signed with the existing development certificate so alpha installs can update without losing progress.
- Device validation: Agam confirmed the alpha build works on his physical phone and subsequently confirmed v0.1.2 installed successfully. The reported installation failure was resolved in the phone's scan/install flow: two install dialogs appeared, the scanning flow failed, and the other installer completed installation. No APK change was needed for this report. The exact GitHub v0.1.2 APK also passed fresh install, alpha.3-to-release update, and launch on Android 15/API 35 arm64 AOSP ATD. A private-file marker survived the update, and UIAutomator loaded the release Home screen; learner-progress retention and visual emulator rendering were not verified.
- v0.1.1 adds Koda, an original teal fox mascot, seven in-app vector illustrations/icons, and adaptive color/monochrome launcher art. Source SVGs and the generator live in `assets/brand`; native vector resources are in `app-android/src/main/res`. `BrandArtwork.kt` draws them in Home, lesson completion and review. The launcher artwork fits the centered 66 dp circular safe area. Source/path parity, rendered light/dark artwork, and circular/themed launcher previews were checked. No phone was connected for a fresh v0.1.1 device test.
- v0.1.2 keeps the streak flame's three tips while removing the thin dark extension above its left side. SVG, Android vector and preview are regenerated from the shared source; the other artwork is unchanged.
- v0.1.3 fixes correct lesson feedback briefly turning red on Continue. The animated banner retains the checked result and explanation through its exit, uses new results immediately, keeps AI access flags current, and prevents repeated Continue taps. Three Compose/Robolectric regression tests run in the existing unit-test/CI task; the original live-result behavior fails the exit test. The signed release passed fresh installation and launch on API 35 arm64 AOSP ATD, plus a v0.1.2-to-v0.1.3 update that retained a real correct-answer result, its 10 XP, and all existing database rows. No physical phone was connected for v0.1.3 testing.
- Plan and rules: `docs/PLAN.md`. Content contract: `docs/CONTENT_SCHEMA.md`. Compiler server findings: `docs/spike-report.md`. AI API facts: `docs/AI_PROVIDERS.md`. Setup for users: `README.md`.

## Modules

| Module | What lives there |
|---|---|
| `:shared` (KMP: android + jvm) | `model/` (course, exercise and progress types), `content/` (CourseLoader), `domain/` (StreakCalculator with freezes, XpPolicy, DailyGoalTracker, ReviewScheduler, AnswerGrader, LessonCompletion), `data/` (SQLDelight repos, **ProgressRecorder**, SettingsRepository, SecretStore with an Android Keystore actual), `ai/` (AiTutor, AiTutorImpl, Anthropic/OpenAI/Gemini/OpenRouter clients, Prompts), `runner/` (CodeRunner and LanguageSupport contracts) |
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

The v0.1.3 full build and lint passed: 114 app (including three Compose animation regressions), 74 shared JVM, 71 shared Android host, 14 runtime JVM, 14 runtime Android host and 35 content tests (322 test executions, no failures). SQLDelight migration verification also passed. All 82 live content checks passed on stable Kotlin 2.4.21 for v0.1.0 (21 cloze warnings that only need surrounding context); content and runtime are unchanged in this feedback fix.

## Compiler server (needed for code exercises)

- A Docker container named `kotlin-compiler-server` runs on `127.0.0.1:8080`, loopback only on purpose. It uses stable Kotlin 2.4.21, built from JetBrains/kotlin-compiler-server at commit `325cdb872475891da66fc4c9b05763de8f011e16` plus the committed `tools/compiler-server/kotlin-2.4.21.patch`. Follow the tool README to apply the patch before the host build.
- The stopped `kotlin-compiler-server-beta` container and its `kotlin-compiler-server:2.5.0-Beta2-host` image are retained locally for rollback. Do not run both on port 8080.
- The clone is at `/Users/agamairi/development/tools/kotlin-compiler-server`. Start or rebuild it with `KCS_CLONE=/Users/agamairi/development/tools/kotlin-compiler-server tools/compiler-server/run.sh`. `KCS_CLONE` is required.
- Building it needs JDK 17, which an agent installed with Homebrew (`openjdk@17`). Remove it with `brew uninstall openjdk@17` if you never rebuild the server.
- A stopped container, `dreamairi-cloud-server-api-1`, also maps port 8080. Don't start both.
- The emulator reaches the server at `http://10.0.2.2:8080` (the app's default). A USB phone needs `adb reverse tcp:8080 tcp:8080` and `http://localhost:8080` in Settings. Cleartext HTTP is allowed only to those hosts (`res/xml/network_security_config.xml`).
- Security: there is no auth, learner code runs as root with internet access, and learner code can forge a JUnit result. Keep it single-user and loopback-only. The runner fails closed: missing testResults is a failure, and it requires the exact expected (class, method) pairs with status OK, plain file names, a 200 KB cap and a 30 s timeout.

## Release flow

1. Bump `versionCode` and `versionName` in `app-android/build.gradle.kts`.
2. Run `./gradlew build :shared:verifySqlDelightMigration :app-android:assembleRelease` and `./gradlew :content:verifyContent` against the local compiler server.
3. Sign `app-android/build/outputs/apk/release/app-android-release-unsigned.apk` using Android SDK `apksigner` and the existing local `~/.android/debug.keystore` (`androiddebugkey`, standard Android development-keystore password). Keep the keystore out of Git. Retaining that certificate allows alpha users to update in place; the release variant itself is non-debuggable. A different production key would require a fresh installation.
4. Verify the signature with `apksigner verify --print-certs`, inspect version and `debuggable=false` with `aapt2`, and generate a SHA-256 checksum.
5. Before publication, test the exact signed candidate APK on a disposable emulator or dedicated test device, separately from build, lint, and signature verification. Check fresh installation and launch (`adb install "$SIGNED_APK"`, then `adb shell am start -W -n dev.agam.learn/.MainActivity`). Also install the previous published APK, create sample progress, update with `adb install -r "$SIGNED_APK"`, and confirm launch and progress retention. Reset or uninstall only on that disposable test device; never erase the user's data for a routine release check. Record Android version and results.
6. Commit and push `main`; check GitHub CI. Tag the verified commit (for the first stable release, `v0.1.0`) and push the tag.
7. Publish `learn-kotlin-<version>.apk` and its checksum with `gh release create <tag> <apk> <checksum> --latest --notes-file <notes>`. Stable releases omit `--prerelease`.

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
- antigravity (Gemini Flash) jobs can end "interrupted" or without a final message. Check the files and build yourself. Gradle can run from this checkout when the agent environment has the normal local filesystem/process permissions.

## Not done / next steps

1. **Installation issue resolved.** Agam confirmed v0.1.2 installed on his physical phone after the duplicate scan/install dialog issue. The earlier alpha physical-device smoke test is complete. API 35 ATD fresh-install, launch, and Home hierarchy checks passed for v0.1.3; updating from v0.1.2 retained a correct-answer result, 10 XP, and all existing database rows. A full stable physical-device smoke test, visual emulator review, and the seven-day soak test have not been recorded.
2. **Stable compiler migration complete.** The server runs Kotlin 2.4.21. Re-run `:content:verifyContent` whenever content or the compiler version changes.
3. **Optional:** a hosted runner (the plan mentions Cloud Run with a token). It needs real isolation first; see the security bullet above.
4. **Optional:** swap Leitner for FSRS behind `ReviewScheduler`, or add a second language pack (`docs/PLAN.md`, "Adding a language later").
5. **Housekeeping:** the merged `feat/*` local branches still exist; delete them with `git branch -d` if you like. `allowBackup` is on by design (progress backs up; the key doesn't survive a restore).
