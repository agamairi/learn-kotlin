# learn-kotlin: Product and Architecture Plan

Status: draft v1, 2026-10-24. Agreed in a two-round design debate between Codex GPT-5.6-Sol (skeptic) and Gemini 4 Argon (advocate), with research from Codex GPT-5.6-Luna and Gemini 3.8 Flash, moderated and fact-checked by Claude.

## 1. Goal

A Duolingo-style app for learning programming languages through short, chapter-based lessons, fill-in-the-blank and quiz exercises, and real code that compiles and runs. It keeps you coming back with a streak, XP, a daily goal and a review queue.

v1 scope: **Kotlin only**, seeded with the Stripe Android interview material that already exists (8 lessons, 17 multi-part problems, ~110 Q&A pairs, 312 verified checks). v1 is for one user (Agam) first; public release is a later decision with its own security review.

Design principles:

1. **Language-agnostic core.** Nothing in the domain, content model or UI assumes Kotlin. A new language is a content pack plus a `LanguageSupport` implementation.
2. **Content is data.** Courses live as versioned YAML in the repo, are validated at build time, and ship to the app as JSON.
3. **Every answer is verified.** Each code exercise carries a reference solution that must pass its own tests in CI, the same discipline that produced the 312 passing checks.
4. **Small first.** Build only what the next phase needs. No speculative modules, platforms or features.

## 2. Interview first

The Stripe team screen is on ~2026-10-27. Until then:

- **Your time on the app is zero, apart from one optional exception:** up to 3 hours of AI-free drills that double as interview practice (section 10). They replace a mock, they don't add to your load.
- **Agents may work unattended** on Phase 0 (section 9). You don't review anything until after the interview.

## 3. Decisions

| Topic | Decision | Why |
|---|---|---|
| Platform | Android app in Kotlin + Jetpack Compose. Only the shared logic is Kotlin Multiplatform (`commonMain`). | You asked for an app and Kotlin practice. Compose Multiplatform 1.12.2 is stable on Android/iOS/desktop (web/Wasm is Beta), so iOS or desktop can come later without a rewrite. No other targets in v1. |
| Running code | Self-hosted JetBrains **kotlin-compiler-server** (Apache-2.0, active) behind a `CodeRunner` interface. | Verified endpoints: `POST /api/compiler/run`, `POST /api/compiler/test` (JUnit, structured results), `POST /api/compiler/highlight` (diagnostics without running), plus translate-to-JS/Wasm. |
| Grading | Learner file + test file sent to `/api/compiler/test`; results come back as structured JUnit outcomes. | Sol showed that parsing PASS/FAIL from stdout is forgeable. The JUnit endpoint fixes that. |
| Compile-to-JS in the client | Not used. | Kotlin/JS can't use JVM APIs such as `java.math.BigDecimal` and `java.util.PriorityQueue`, which existing lessons use. |
| Runner security | Private, single-user, token-protected, resource-capped (section 6). | A JUnit result is grading, not a sandbox. Public release needs a separate threat model. |
| Content format | YAML sources, validated and converted to JSON at build time; the app reads JSON with kotlinx.serialization. | `kaml` (the usual YAML library) is archived and only fully supports JVM. JSON decoding works on every Kotlin target. |
| Exercises in v1 | MultipleChoice, Cloze, PredictOutput, WriteFunction, MultiPartProblem. | Covers the study page. Parsons and FindTheBug come later. |
| Gamification | Streak (local date, 4am cutoff), XP, daily goal. Streak freeze in Phase 3. | You asked for a streak system. Freezes, leagues and achievements wait. |
| Review | 5-box Leitner scheduler, no FSRS. | The FSRS Kotlin port has no license; the MIT alternative has 1 star and one maintainer. Leitner is ~40 lines with no dependencies. FSRS can replace it later behind the same interface. |
| Code editor | Compose `BasicTextField`, monospace, a small keyword highlighter, a symbol bar, and compiler diagnostics from `/highlight`. | sora-editor is Android-only and LGPL, which fights the KMP goal. Exercises are short. |
| Storage | SQLDelight, local-first. No accounts or sync in v1. | Offline progress; sync is a later phase. |

## 4. Architecture

### Modules (four)

```
learn-kotlin/
├── shared/            Kotlin Multiplatform (commonMain + androidMain)
│   ├── model/         Course, Chapter, Lesson, Exercise (sealed), progress types
│   ├── domain/        StreakCalculator, XpPolicy, DailyGoal, ReviewScheduler (Leitner), local graders
│   ├── content/       JSON decoding of course bundles (kotlinx.serialization)
│   ├── runner/        CodeRunner, LanguageSupport, RunOutcome contracts
│   └── data/          SQLDelight schema + repositories
├── runtime-kotlin/    KMP: Ktor client for kotlin-compiler-server; implements CodeRunner + LanguageSupport
├── content/           YAML course sources + a JVM build tool that validates and emits JSON into the app
└── app-android/       Compose UI, navigation, theming, SQLDelight Android driver, dependency wiring
```

Packages inside `shared` keep the boundaries without the cost of extra Gradle modules. A package becomes its own module only when a second implementation forces a real boundary.

### Core types (sketch; compiles on Kotlin 2.2)

```kotlin
data class Course(val id: String, val language: LanguageId, val title: String, val chapters: List<Chapter>)
data class Chapter(val id: String, val title: String, val lessons: List<Lesson>)
data class Lesson(val id: String, val title: String, val explanation: String, val exercises: List<Exercise>)

@JvmInline value class LanguageId(val value: String)          // "kotlin", later "swift", "python"

sealed interface Exercise {
    val id: String
    val prompt: String
    data class MultipleChoice(override val id: String, override val prompt: String,
        val code: String?, val options: List<String>, val correctIndex: Int, val explanation: String) : Exercise
    data class Cloze(override val id: String, override val prompt: String,
        val template: String, val blanks: List<Blank>, val explanation: String) : Exercise
    data class PredictOutput(override val id: String, override val prompt: String,
        val code: String, val expectedOutput: String, val explanation: String) : Exercise
    data class WriteFunction(override val id: String, override val prompt: String,
        val starterCode: String, val hiddenTests: String, val hints: List<String>) : Exercise
    data class MultiPartProblem(override val id: String, override val prompt: String,
        val parts: List<WriteFunction>, val faq: List<Pair<String, String>>) : Exercise
}
data class Blank(val placeholder: String, val accepted: List<String>)

data class SourceFile(val name: String, val text: String)

sealed interface RunOutcome {
    data class Passed(val testsRun: Int, val stdout: String) : RunOutcome
    data class TestsFailed(val failures: List<TestFailure>, val stdout: String) : RunOutcome
    data class CompileError(val diagnostics: List<Diagnostic>) : RunOutcome
    data class Timeout(val seconds: Int) : RunOutcome
    data class Unavailable(val reason: String) : RunOutcome
}
data class TestFailure(val testName: String, val message: String)
data class Diagnostic(val line: Int, val column: Int, val severity: String, val message: String)

interface CodeRunner {
    suspend fun run(files: List<SourceFile>): RunOutcome
    suspend fun test(files: List<SourceFile>, tests: List<SourceFile>): RunOutcome
    suspend fun diagnostics(files: List<SourceFile>): List<Diagnostic>
}

interface LanguageSupport {
    val id: LanguageId
    val displayName: String
    val runner: CodeRunner
    val keywords: Set<String>                                 // for the small syntax highlighter
}
```

The real models add `@Serializable` and serial names for the sealed `Exercise` hierarchy.

### Adding a language later

1. Add `content/<language>/...` YAML.
2. Implement `LanguageSupport` (display name, keywords, test-file template).
3. Implement `CodeRunner` for that language (for example a Judge0-backed runner, which supports 90+ languages but must have its language versions checked and pinned).
4. Nothing in `shared/domain`, `shared/model` or the UI changes.

## 5. Content

### Mapping the existing material

| Source | Becomes |
|---|---|
| Lessons 1–8 (syntax map, strings, collections, testing, modeling, time/queues/graphs, idioms, mobile-style Kotlin) | Chapters 1–8. Each: a short explanation, 4–8 multiple-choice/cloze/predict-output cards, and 1–2 write-a-function exercises from the drills |
| 17 multi-part problems (mobile and Terminal first) | Chapter 9, "Stripe practice": MultiPartProblem exercises, parts unlock one at a time, FAQ as clarifying answers |
| ~110 rapid-fire Q&A pairs | Review deck: multiple-choice and cloze cards that feed the Leitner queue |
| The 10 "mistakes from this week" | Chapter 10, "Habits": find-the-bug style cards (as multiple choice until FindTheBug exists) |

### Authoring format (YAML)

```yaml
id: kotlin-ch03-collections
title: The collections toolkit
lessons:
  - id: groupby-basics
    title: Grouping things into piles
    explanation: |
      groupBy sorts items into piles by a label you choose ...
    exercises:
      - type: cloze
        id: cloze-groupby
        prompt: Group the sales by shop.
        template: "val byShop = sales.____ { it.shop }"
        blanks:
          - placeholder: "____"
            accepted: ["groupBy"]
        explanation: groupBy returns a Map from each key to the list of items with that key.
      - type: write_function
        id: wf-totals
        prompt: Return each merchant's total cents, sorted by merchant.
        starter_code: |
          fun totals(sales: List<Sale>): List<Pair<String, Long>> = TODO()
        hidden_tests: |
          class TotalsTest { @Test fun example() { assertEquals(listOf("acme" to 3550L), totals(sample)) } }
        reference_solution: |
          fun totals(sales: List<Sale>) = sales.groupBy { it.shop }.map { (k, v) -> k to v.sumOf { it.cents } }.sortedBy { it.first }
```

### Build-time validation (the content compiler)

A small JVM tool in `content/`:

1. Parses YAML.
2. Decodes it into the same `@Serializable` models the app uses. A schema error fails the build.
3. Checks the rules: unique ids, `correctIndex` in range, every cloze blank appears in its template.
4. Writes one JSON bundle per course into `app-android` assets.
5. In CI, sends every `reference_solution` with its `hidden_tests` to the compiler server and fails if any test fails. That guarantees every expected answer is verified.

In v1 the "hidden" tests ship inside the app bundle. That's fine for a single user. A public version would keep tests server-side.

## 6. Running code safely

Development: run kotlin-compiler-server in Docker on the laptop. The Android emulator reaches the host at `10.0.2.2`.

Later, for use on a real phone: Cloud Run with request-based billing. The free tier is 180,000 vCPU-seconds and 360,000 GiB-seconds a month, which is far more than one person needs. Cold starts are unmeasured, and the spike will measure them.

Minimum hardening before the runner is reachable from anywhere but localhost:

- **No anonymous access:** a bearer token, or Cloud Run IAM.
- **Limits:** request body size limit, request timeout (about 10 s), per-test timeout.
- **Capacity:** max instances 1, CPU and memory caps (about 2 GiB), a concurrency limit, a budget alert.
- **Network:** no outbound network, no metadata server access, no dependency downloads at run time.
- **Container:** non-root, pinned image, read-only filesystem where possible, fresh state per run.
- **Public release:** a separate threat model and stronger isolation, such as gVisor or a microVM.

## 7. Learning engine

- **XP:** 10 per correct card, 25 per code exercise passed first try, 15 after retries, 50 per multi-part problem part.
- **Daily goal:** a chosen XP target (default 50). Progress shows on Home.
- **Streak:** a day counts if the daily goal was met. Days are local dates in the device's time zone with a 4am cutoff, using kotlinx-datetime. Streak freezes arrive in Phase 3.
- **Review (Leitner, 5 boxes):** intervals of 1, 2, 4, 8 and 16 days. A correct answer moves a card up a box; a wrong answer sends it back to box 1. Missed lesson cards enter box 1 automatically. The `ReviewScheduler` interface allows a later FSRS swap.

All of this lives in `shared/domain`: pure functions with unit tests and no Android dependencies.

## 7b. AI tutor (bring your own API key)

Added 2026-10-24 at Agam's request. Optional, and off until a key is added.

- **Settings › AI tutor:** choose a provider (Anthropic, OpenAI or Google Gemini), paste your own API key, pick a model from a short list, and run "Test connection". The key is stored on the device only, encrypted with an Android Keystore key, and never logged or included in exports. A "Remove key" button deletes it.
- **What it does:**
  - **Explain my mistake:** after a wrong answer or failed tests, sends the exercise, your answer and the expected result, and gets a short plain-language explanation.
  - **Hint ladder:** three levels (nudge, approach, near-solution) for code exercises; each level costs some XP.
  - **More like this:** generates 3 extra multiple-choice or cloze cards on the current topic, shown with an "AI-generated, unverified" label and never mixed into verified content or the XP economy.
  - **Review my code:** comments on readability and Kotlin idioms of a passing solution.
- **Interview mode:** timed mocks and multi-part problems run in "no AI" mode. Tutor buttons are hidden until the mock ends, matching the Stripe screen's rules.
- **Architecture:** an `AiTutor` interface in `shared` (provider-agnostic: `explainMistake`, `hint`, `generateCards`, `reviewCode`), with one `AiProvider` implementation per vendor in `shared` using Ktor (Anthropic Messages API, OpenAI Chat Completions, Gemini generateContent). Prompts live as templates in one file. Network errors, bad keys and rate limits become friendly messages, never crashes. The current, correct API shapes and model names must be checked against each vendor's official docs at build time; do not invent them.
- **Privacy note shown in Settings:** your answers and code are sent to the provider you chose, using your key and your account.

## 8. Screens (v1)

1. **Home:** streak, today's XP against the goal, "continue lesson", the review count due.
2. **Path:** chapters and lessons as a progress map.
3. **Lesson player:** one exercise card at a time, with instant feedback and an explanation.
4. **Code exercise:** prompt, editor, Run and Check buttons, test results, compiler diagnostics underlined.
5. **Multi-part problem:** parts revealed one at a time, clarifying answers, an optional 45-minute timer.
6. **Review:** Leitner cards that are due.
7. **Stats:** XP history, streak calendar, per-chapter accuracy.
8. **Settings:** daily goal, AI tutor provider and key, compiler server URL and token.

## 9. Phases and gates

Effort estimates from the debate disagree and none were measured: Sol said 30–50 agent hours plus 12–18 of your hours after the interview; Argon said 20–28 agent hours plus 8–12 of yours. Plan for the higher range.

| Phase | Contents | Gate to move on | Who |
|---|---|---|---|
| **0. Spike + scaffold** (may run before 10/27, zero time from you) | Runner spike (checklist below), Gradle scaffold for the 4 modules, YAML conversion of the study material, the content compiler | Spike passes every item; every reference solution passes in CI | Agents: Luna (spike + content conversion), Terra (scaffold + content compiler); Claude verifies |
| **1. Learn path, offline** | Models, JSON loading, SQLDelight progress, Home/Path/Lesson screens, MultipleChoice/Cloze/PredictOutput, XP, daily goal, streak | You can finish Chapter 1 on a phone, and progress survives a restart | Terra (shared + data), Gemini 3.8 Flash (Compose screens), you review |
| **2. Real code** | `runtime-kotlin` Ktor client, Code exercise screen, `/test` grading, `/highlight` diagnostics, MultiPartProblem with reveal | 5 write-function exercises and 3 Stripe problems pass end to end | Terra + Luna; Sol reviews the runner and security |
| **3. Review + polish** | Leitner queue, Review and Stats screens, streak freeze, accessibility, Cloud Run deployment with a token | Seven straight days of real personal use | Flash (UI), Terra (domain), Argon (content QA) |
| **4. Second language** (only when wanted) | One small pack in another language, with its own runner | No changes needed in `shared/domain` or the UI | Agents |

### Phase 0 runner spike checklist

Run each against kotlin-compiler-server in Docker, capped at 2 GiB, and record the result:

1. A correct solution plus tests → structured pass, latency (cold and warm).
2. A compile error → diagnostics with line and column.
3. An infinite loop → bounded timeout, server stays healthy.
4. A memory bomb → bounded failure, server stays healthy.
5. `System.exit(0)` inside the learner's code → can't fake a pass.
6. A learner printing fake "PASS" lines → doesn't affect the JUnit result.
7. A learner trying to read the test file or the filesystem → record what's reachable.
8. The Kotlin version reported by the server → pin it, and note how to upgrade.
9. `/highlight` on broken code → useful diagnostics for the editor.

If items 3–7 can't be bounded, code exercises stay local-only (localhost runner) until isolation is fixed.

## 10. Optional AI-free drills (interview practice, at most 3 hours before 10/27)

These are plain Kotlin logic with hand-written tests, the same shape as the Stripe screen, and they become real app code:

1. `StreakCalculator`: given activity timestamps, a time zone and "now", return the current streak with the 4am cutoff. Test it across midnight, across the cutoff, with gaps, and with an empty history.
2. `LeitnerScheduler`: given a card's box and whether the answer was correct, return the new box and next due day.
3. `ClozeGrader`: compare answers to accepted values, trimmed, with case rules per blank.

Write each in your StripePrep project with the `expect()` helper, timed like a mock. They count toward your mock log.

## 11. Risks and open questions

- **JVM cold starts on Cloud Run** may be slow. The spike measures them; the fallback is the local Docker runner, or one warm instance (small monthly cost).
- **Compiler version drift:** pin the server's Kotlin version and update on purpose.
- **Content accuracy at scale:** solved by CI validation of reference solutions (section 5).
- **Scope creep:** each phase has a gate, and nothing ships early.
- **Public release** needs a security review, server-side tests, accounts and sync. None of that is in v1.

## Sources

- JetBrains kotlin-compiler-server: https://github.com/JetBrains/kotlin-compiler-server (routes read from `CompilerRestController.kt`)
- Kotlin Playground: https://github.com/JetBrains/kotlin-playground
- Compose Multiplatform platform stability: https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- Cloud Run pricing: https://cloud.google.com/run/pricing
- Judge0: https://github.com/judge0/judge0 · JDoodle Compiler API: https://www.jdoodle.com/compiler-api
- kotlinx-datetime: https://github.com/Kotlin/kotlinx-datetime · SQLDelight: https://github.com/sqldelight/sqldelight · Ktor: https://github.com/ktorio/ktor
- kaml (archived): https://github.com/charleskorn/kaml · ts-fsrs / FSRS: https://github.com/open-spaced-repetition
