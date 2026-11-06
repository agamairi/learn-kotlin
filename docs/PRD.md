# Learn Code: Product Requirements Document

**Status:** Proposed
**Date:** 2026-11-04
**Source:** Inspection of the existing `learn-kotlin` repository

## 1. Product Summary

Learn Code is a short-session, Duolingo-style learning product for programming languages and technical interviews. It combines structured language courses, interview-style problem solving, active recall, streaks, and an optional AI tutor.

The product starts with the existing Kotlin-for-Android/interview-prep experience and grows into a mobile learning platform where new courses can be shipped as versioned JSON course packs. A learner can create an account, sync progress across mobile devices, install a pack from a trusted GitHub source or community marketplace, and continue with a cached course when temporarily offline.

The first target is mobile only:

- Native mobile learning through React Native.
- Cloud-backed accounts, progress sync, entitlements, and notifications.
- Local-first caching and an offline queue so network loss does not interrupt a session.
- First-party, curated, and community-authored course packs.

Desktop and browser clients are deferred, but the domain, API, content, storage, and UI boundaries must not make them expensive later.

The product promise is: **learn practical programming skills in five-minute sessions, then prove that you can use them in real interview and coding situations.**

## 2. Problem and Opportunity

Existing programming-learning products often split the experience between passive tutorials, isolated coding challenges, and interview platforms. Learners have to choose between:

- Beginner-friendly repetition that lacks realistic problems.
- Real coding problems that assume too much context.
- Interview preparation that is difficult to maintain as a daily habit.
- AI assistance that gives answers without building durable understanding.

Learn Code can own the space between language learning and interview preparation:

1. Teach a concept in a small, approachable lesson.
2. Reinforce it with varied question types.
3. Revisit weak concepts with spaced repetition.
4. Apply the concept in a guided or real coding problem.
5. Make progress visible through a habit loop.

The repository already demonstrates this loop for Kotlin, including guided code puzzles, interview-style problems, a review queue, daily goals, streaks, and progress analytics.

## 3. Goals and Non-Goals

### Goals

- Make programming practice feel approachable, repeatable, and habit-forming.
- Support multiple programming languages without coupling the learning engine to one language.
- Let content authors add or update courses without changing the client application.
- Keep lessons and supported exercises usable offline after a pack is installed.
- Preserve the current product strengths: structured content, local grading, review scheduling, progress settlement, and the Octo Coach brand system.
- Build a credible path from a personal Android app to a mobile subscription business.
- Keep third-party course packs safe, inspectable, attributable, and removable.
- Make the first public beta polished enough to share as a real product launch on LinkedIn.

### Non-goals for the first public release

- A general-purpose IDE or replacement for a desktop development environment.
- A screen dominated by a code editor, terminal, compiler output, or arbitrary code execution.
- Arbitrary untrusted code execution on a learner's device or in an unprotected public server.
- A social network, public leaderboard, or creator marketplace at launch.
- Desktop and browser clients in the first public beta.
- Supporting every programming language at once.
- AI-generated courses being treated as verified curriculum without human review.

## 4. Target Users

### Primary learner: interview-focused developer

Wants a clear daily plan for a language, algorithms, Android, backend, or system-design interview. Values realistic constraints, explanations, mock problems, timers, and measurable improvement.

### Secondary learner: language switcher

Knows one language and wants to become productive in another. Values comparisons, syntax drills, short examples, and confidence-building practice before larger projects.

### Secondary learner: consistent beginner

Needs a small daily action and visible encouragement. Values simple language, immediate feedback, an approachable mascot, and a path that makes the next task obvious.

### Content author or educator

Wants to publish a focused course or question bank without building an application. Needs a documented schema, validation tooling, preview support, provenance, and versioned releases.

### Product operator

Needs to curate quality, manage subscriptions, measure retention, moderate packs, and protect learners from malicious or low-quality content.

## 5. Current Repository Baseline

> **Implementation update (2026-11-04):** The Kotlin/Gradle Android modules described in this historical baseline have now been removed from the repository. The active source of truth is the Expo React Native client in `apps/mobile` and the TypeScript packages in `packages/`. The baseline below records the product and behavioral inventory that informed the migration.

The existing app is a valuable product prototype, not a blank slate.

### Existing user experience

- Home dashboard with streak, daily XP, goal progress, review count, and a continue-lesson action.
- Curved learning path with chapters, lessons, locked/unlocked states, and completion progress.
- Lesson player with one question at a time, immediate feedback, explanations, and a mistake-review round.
- Multiple choice, cloze, predict-output, guided code puzzles, and multi-part coding problems.
- Code-puzzle options that reconstruct authored reference solutions without requiring a compiler connection.
- Review screen backed by a five-box Leitner schedule.
- Stats screen with XP history, streak calendar, exercise totals, and chapter accuracy.
- Settings for daily goals, learning feedback, reduced motion, and an optional bring-your-own-key AI tutor.
- Optional AI actions for explaining mistakes, hints, generated practice cards, and code review.

### Existing technical foundation

- `shared/`: Kotlin Multiplatform models, graders, XP, daily-goal, streak, review, repositories, settings, and AI interfaces.
- `runtime-kotlin/`: language support and a Kotlin compiler-server client retained for author verification and legacy developer flows.
- `content/`: YAML course sources, semantic validation, compilation to JSON, and optional live verification against a Kotlin compiler server.
- `app-android/`: Jetpack Compose screens, navigation, dependency wiring, and the bundled course asset.
- SQLDelight tables for exercise attempts, daily activity, lesson completion, review cards, settings, streak freezes, and idempotent lesson-run settlement.
- Automated tests covering domain rules, content validation, progress persistence, UI view models, and compiler/runtime behavior.

### Existing content and brand inventory

- 12 chapters.
- 41 lessons.
- 222 authored exercises.
- 17 multi-part interview-style problems, according to the repository handoff documentation.
- Five exercise families in the content contract: `multiple_choice`, `cloze`, `predict_output`, `write_function`, and `multi_part_problem`.
- Original Octo Coach vector artwork with mascot poses, review art, streak, XP, lesson, and launcher assets.
- Shared SVG masters plus a generator that emits Android vector resources.
- Warm ivory, sage, clay, ochre, and charcoal visual system with light and dark themes.

### Important current limitations

- The shipped client is Android-only.
- There is one Kotlin course and no remote course-pack installer or marketplace.
- Progress is local to one device; there are no accounts, cloud sync, or remote entitlements.
- Learner code is intentionally constrained to authored guided choices in the current product.
- The public security model for arbitrary code execution is not ready.
- Hidden tests, reference solutions, and answer keys can be present in the app bundle; this is acceptable for personal offline use but not for competitive or high-stakes public grading.

The React Native migration should treat the Android app as the behavioral reference while making account identity, sync, pack installation, and remote configuration first-class capabilities.

## 6. Product Principles

### Practice before content volume

Every screen should lead to an action: answer, predict, complete, review, or solve. Explanations support practice rather than replacing it.

### Small sessions, serious outcomes

The default session should be completable in 5–10 minutes, while interview and multi-part modes can intentionally be longer.

### Verified where it matters

Authored answers, expected output, puzzle reconstruction, and reference solutions must pass validation before publication. AI-generated material is clearly labeled until reviewed.

### Offline-first, sync-ready

A learner should be able to continue a downloaded course without a network connection. Cloud accounts and sync should be added behind repository interfaces rather than baked into screens.

### Motivation without manipulation

Streaks, lives, reminders, and rewards should encourage a sustainable habit. The product must not hide learning behind ads, punish a learner for taking a break, or use urgent notifications that are unrelated to learning value.

### Content is a contract

The app renders a stable, versioned content contract. Course packs should not require a client release for ordinary content additions.

## 7. Core Learning Loop

```text
Open app
  -> See today's goal and the next best action
  -> Complete a short lesson or due review
  -> Receive immediate explanation and XP
  -> Continue through a mistake round when needed
  -> Build streak and unlock the next lesson
  -> Return later for spaced repetition
  -> Apply the concept in an interview-style problem
```

### Session types

1. **Learn:** new concepts in a guided sequence.
2. **Review:** due cards selected by the review scheduler.
3. **Practice:** extra questions on a completed concept.
4. **Problem:** a longer, interview-style challenge with staged parts.
5. **Mock interview:** timed, AI-free, low-distraction practice.

## 8. Functional Requirements

### 8.1 Onboarding and course discovery

The learner can:

- Choose a goal such as language fundamentals, interview preparation, Android, algorithms, or daily practice.
- See recommended first-party courses and installed course packs.
- Preview a course's language, level, lesson count, estimated scope, publisher, version, and update date.
- Install a course from the catalog or from a supported GitHub URL.
- Continue learning offline after installation.
- Remove an installed pack without deleting unrelated learner settings or progress unless explicitly confirmed.

### 8.2 Course packs

The client can:

- Download a pack manifest from a trusted HTTPS URL.
- Validate the pack's JSON structure and semantic rules before activation.
- Display a clear error when a pack is malformed, unsupported, unsigned, expired, or incompatible.
- Store the raw pack, normalized metadata, checksum, source URL, version, and installation timestamp.
- Keep the last known-good version active if an update fails.
- Check for updates using `ETag`, `Last-Modified`, or a pack-defined version endpoint.
- Allow a learner to pin a version for offline stability.
- Track progress by stable `packId`, `courseId`, `lessonId`, and `exerciseId`.

### 8.3 Lesson player

The client can:

- Render all supported question types from the content contract.
- Show progress through the current lesson without exposing unnecessary complexity.
- Validate an answer locally where the pack provides a local grading rule.
- Show explanation, accepted answer, expected output, or test feedback after checking.
- Queue missed questions into the current lesson's mistake round.
- Prevent duplicate settlement when a learner retries, rotates a device, or repeats a completion callback.
- Pause and resume a lesson safely.
- Respect accessibility settings, reduced motion, text scaling, keyboard navigation, and screen readers.

### 8.4 Guided reasoning and code examples

The product is a reasoning coach, not a mobile IDE. The learner should understand why a method, data structure, control flow, or debugging step is appropriate before being asked to write much code.

The client should:

- Introduce one concept at a time with a short explanation and a concrete code example.
- Show readable code snippets with syntax highlighting and line-by-line emphasis.
- Ask which method, operator, collection, or control-flow construct fits a situation.
- Ask the learner to predict what a small program returns or prints.
- Ask the learner to order the logical steps before choosing code.
- Use guided blanks to build a correct function from meaningful spans.
- Show a small bug and ask what is wrong, what the code should do, or which fix is safest.
- Explain both the correct answer and why the tempting alternatives are wrong.
- Use multi-part problems to teach decomposition, naming, edge cases, and test thinking.

The app should avoid:

- Opening a blank editor before the learner has seen the relevant pattern.
- Requiring arbitrary typing for every exercise.
- Presenting a full project tree, terminal, dependency manager, or compiler dashboard.
- Rewarding fast guessing more than correct reasoning.

Real code submission can be added later as an optional advanced mode. The default learner experience remains guided, visual, and choice-driven.

### 8.5 Review and mastery

The client can:

- Show due-card count and review priority.
- Schedule cards using the current five-box Leitner behavior in the first migration.
- Move correct cards to a longer interval and incorrect cards back toward the first box.
- Explain the next review interval after an answer.
- Report mastery separately from mere exposure or completion.
- Support a future scheduler replacement without changing the exercise UI.

### 8.6 Motivation and habit

The first public release can include:

- Daily XP goal.
- Current and longest streak.
- Streak freezes with a clearly explained earning and spending rule.
- XP, lesson completion, review completion, and course progress.
- Milestone feedback and Octo Coach celebrations.
- Optional local notifications for due review, daily goal progress, and streak risk.
- Notification controls by category, quiet hours, and a single-tap disable path.

#### Pace bonus

The product rewards focused, correct completion without turning the lesson into a typing-speed test.

- Every lesson has an authored expected duration based on its number and type of exercises.
- After enough completed sessions, the learner also gets a personal baseline based on active learning time, not wall-clock time.
- A correct lesson completed faster than the applicable baseline can earn a capped pace bonus, for example 0–25% additional lesson XP.
- The bonus requires correctness and a completed lesson; it is not awarded for abandoning a run, repeatedly guessing, or skipping the mistake round.
- Hints can reduce or remove the pace bonus but never create a negative XP penalty.
- Time spent paused, backgrounded, reading optional explanations, or using accessibility features is excluded where the platform allows it.
- The bonus is capped so speed never dominates accuracy, review quality, or accessibility.
- The UI shows the bonus as a positive focus reward, not as a countdown or warning.

Lives/energy are a monetization experiment, not a prerequisite to learning. If enabled, the learner must always be able to access explanations, review history, and a meaningful free practice path.

### 8.7 Accounts, cloud sync, and online-friendly behavior

Cloud sync is part of the first mobile product, not a later add-on. The client should:

- Support anonymous or guest progress that can be upgraded to an account without losing history.
- Offer sign-in with email plus at least one platform identity provider when the backend is ready.
- Sync progress, installed packs, settings, streak state, entitlements, and notification preferences.
- Store a local copy of active courses and a durable outbound sync queue.
- Allow a lesson to complete offline and upload the settlement when connectivity returns.
- Make sync idempotent so retries, duplicate callbacks, and repeated app launches do not award XP twice.
- Merge independent exercise attempts and daily activity deterministically.
- Surface a recoverable conflict state instead of silently overwriting newer progress.
- Support account export and deletion from Settings.
- Keep subscription entitlements server-authoritative while allowing a short, explicit offline grace period.
- Track device sessions and revoke access after account deletion or security events.

The first backend should expose versioned APIs for identity, packs, progress events, entitlements, notifications, and analytics. The React Native client should depend on interfaces for these services rather than calling a vendor SDK from lesson components.

### 8.8 AI tutor

The product can offer:

- Explain my mistake.
- Hint ladder.
- Generate more practice, labeled as unverified.
- Review passing code for readability.

Requirements:

- AI is optional and never required to complete the core curriculum.
- User-provided API keys remain scoped to the user's device or are replaced by a managed service with explicit consent.
- Prompts minimize sensitive data and explain what is sent to a provider.
- Interview mode hides AI assistance during a timed mock.
- AI-generated cards do not enter verified curriculum or the normal reward economy until reviewed.
- Provider errors, quotas, unsafe outputs, and unavailable models produce understandable recovery states.

### 8.9 Interview preparation

The product can support:

- Timed multi-part problems.
- Clarifying-question and FAQ sections.
- Interview mode with no AI actions.
- Optional solution discussion after completion.
- A post-session review of accuracy, time, retries, and weak topics.
- A future mock interview playlist assembled across installed packs.

## 9. JSON Course-Pack Contract

### 9.1 Distribution model

The current YAML is a useful authoring format, but the product should standardize on a versioned JSON distribution contract. YAML may remain an authoring convenience for first-party repositories; the client should only need to understand JSON.

Use two related contracts:

1. **Public distribution schema:** safe to ship to a learner client.
2. **Authoring and verification schema:** may include reference solutions, hidden tests, answer annotations, and compiler-verification metadata that must not be exposed in a public grading environment.

The first migration can keep the current fields and add explicit versioning. Do not force a large content rewrite before the loader and validator are proven.

### 9.2 Pack metadata

Every pack must include:

- `schemaVersion`: contract version, such as `1.0`.
- `packId`: immutable lowercase identifier.
- `version`: semantic content version.
- `title`, `description`, `language`, `level`, and optional tags.
- `publisher`: name, homepage, and contact or repository URL.
- `license`: SPDX identifier or explicit proprietary notice.
- `source`: canonical URL and commit or release reference.
- `contentHash`: checksum of the normalized pack payload.
- `updatedAt`.
- `courses`: ordered course definitions.
- Optional `minimumClientVersion` and `capabilities`.

IDs are immutable once published. A correction creates a new pack version; it does not reuse an old exercise ID for a different question.

### 9.3 Example pack payload

```json
{
  "schemaVersion": "1.0",
  "packId": "kotlin-foundations",
  "version": "1.0.0",
  "title": "Kotlin Foundations",
  "description": "Practical Kotlin and interview drills for mobile engineering fundamentals.",
  "language": "kotlin",
  "level": "intermediate",
  "publisher": {
    "name": "Learn Code",
    "homepage": "https://example.com/learn-code"
  },
  "license": "CC-BY-4.0",
  "source": {
    "url": "https://github.com/learn-code/kotlin-foundations",
    "ref": "v1.0.0"
  },
  "contentHash": "sha256:replace-with-normalized-payload-hash",
  "updatedAt": "2026-11-04T00:00:00Z",
  "courses": [
    {
      "id": "kotlin-core",
      "title": "Kotlin for Mobile Engineers",
      "description": "Short lessons, review cards, and guided interview problems.",
      "chapters": [
        {
          "id": "ch01-syntax",
          "title": "Syntax, values, and control flow",
          "summary": "Translate core ideas into idiomatic Kotlin.",
          "lessons": [
            {
              "id": "lesson-values",
              "title": "Kotlin values and data models",
              "explanation": "Use `val`, data classes, and nullable access safely.",
              "exercises": [
                {
                  "type": "multiple_choice",
                  "id": "values-read-only",
                  "prompt": "Which declaration makes the reference read-only?",
                  "options": [
                    "val displayName = \"Mina\"",
                    "var displayName = \"Mina\"",
                    "readonly displayName = \"Mina\""
                  ],
                  "grading": {
                    "kind": "choice",
                    "correctIndex": 0
                  },
                  "explanation": "`val` prevents reassignment of the reference."
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

### 9.4 Question type requirements

The question renderer uses a discriminator such as `type` and a type-specific `grading` object. Initial supported types:

| Type | Learner interaction | Local grading | Public distribution note |
|---|---|---:|---|
| `multiple_choice` | Select one option | Yes | Correct index may ship for offline use |
| `cloze` | Fill or select code blanks | Yes | Accepted values and options may ship |
| `predict_output` | Select or enter program output | Yes | Expected output may ship |
| `guided_code` | Select code spans to reconstruct a solution | Yes | Canonical accepted values may ship |
| `multi_part_problem` | Solve staged interview parts | Usually hybrid | Public tests must not be relied on as secret grading |
| `find_the_bug` | Identify a defect and repair it | Yes or hybrid | Add after the base contract stabilizes |
| `code_submission` | Write and run arbitrary code | Server-backed | Requires a hardened, isolated runner |

Each exercise should also support optional common fields:

- `topicTags`.
- `difficulty`.
- `estimatedSeconds`.
- `explanation`.
- `hints`.
- `sourceNote` and attribution.
- `aiGenerated` and `verificationStatus`.
- `reviewPolicy` and XP policy overrides, constrained by product rules.

### 9.5 Validation rules

JSON Schema validates structure. A semantic validator validates meaning. Publication must fail when:

- Required fields are missing or have the wrong type.
- IDs are duplicated within a pack or changed unexpectedly across versions.
- A correct option is outside the option list.
- A cloze placeholder is undeclared, duplicated, or has ambiguous accepted values.
- Markdown is empty or contains unsafe active content.
- A declared capability is unsupported by the target client.
- A reference solution does not reconstruct or pass its author tests.
- Attribution, license, or source metadata is missing.
- A pack exceeds configured size, item-count, or nesting limits.

## 10. Community Marketplace and GitHub Course-Pack Installation

The course-pack model becomes the foundation for a Community Marketplace. GitHub is the initial publishing and versioning mechanism; the in-app marketplace is the discovery, trust, and installation layer.

### Marketplace roles

- **First-party:** maintained by the product team and eligible for the main learning path.
- **Verified community:** reviewed for schema validity, attribution, correctness, and safety.
- **Community:** installable with a visible verification status and report action.
- **Private:** installed from a learner-provided URL and visible only to that learner.

The app should make this distinction obvious. A valid JSON file is not automatically a high-quality course.

### Marketplace listing requirements

Every listing displays:

- Title, language, level, topics, and expected time.
- Publisher profile and GitHub source.
- License and attribution.
- Version, last updated date, pack size, and supported client version.
- Verification status and what was actually verified.
- Ratings, completion signals, and report history once there is enough usage data.
- Whether the pack is free, paid, part of a subscription, or private.

Community packs should not be allowed to imitate the first-party brand, manipulate XP, or request permissions outside the pack contract.

### Learner flow

1. Learner selects **Add course pack**.
2. Learner pastes a GitHub repository, release, or raw manifest URL.
3. Client resolves only supported URL forms and fetches the manifest over HTTPS.
4. Client displays publisher, license, version, content count, permissions, and update source.
5. Client downloads the pack and any explicitly declared static assets.
6. Client validates JSON Schema, semantic rules, hash, and compatibility.
7. Client installs atomically and keeps the prior version if activation fails.
8. Learner starts the pack offline; progress is written against immutable content IDs.

### Trust and safety requirements

- Never execute JavaScript, HTML, shell commands, or arbitrary code contained in a pack.
- Sanitize Markdown and restrict links and embedded assets.
- Limit pack size, asset types, image dimensions, and recursion depth.
- Support a signed manifest or trusted registry attestation before allowing a pack into a featured catalog.
- Show the source repository, commit/release, publisher, license, and verification state.
- Allow users to report, hide, or remove a pack.
- Keep community packs out of the first-party catalog until review.
- Treat course content as untrusted input even when hosted on GitHub.
- Do not expose private learner data to a pack or let a pack request arbitrary permissions.

### Updates and progress compatibility

- A patch update may fix wording or explanations without changing exercise identity.
- A minor update may add chapters or exercises.
- A major update may change learning order or grading behavior and must be presented as a migration.
- Existing progress maps by stable IDs; deleted content remains in history but is not shown in new sessions.
- If an exercise's grading contract changes materially, publish a new exercise ID.
- Pack removal deletes content files but keeps an exportable record of learner activity unless the learner asks to erase it.

## 11. React Migration and Cross-Platform Architecture

### Recommended target

Use a TypeScript monorepo with a shared domain, content, API, storage, and design-system layer. Build the first client with React Native for iOS and Android. Keep the package boundaries platform-neutral so a future web or Electron client can reuse the same contracts without making those platforms part of the first release.

```text
apps/
  mobile/       React Native app for iOS and Android
packages/
  core/         models, graders, XP, streaks, review, lesson settlement
  content/      JSON Schema, semantic validator, pack loader, GitHub installer
  api/          typed client for auth, sync, packs, entitlements, notifications
  storage/      local repository, sync queue, and platform adapters
  ui/           tokens, shared components, Octo Coach assets, feedback patterns
  runner/       language-runner interfaces and safe implementations
  authoring/    pack CLI, preview, lint, and verification tools
future/
  web/          deferred React web client
  desktop/      deferred Electron shell
```

### Boundary rules

- `core` must be pure TypeScript and have no React or platform imports.
- `content` must decode and validate packs without knowing how screens render them.
- `storage` must expose repositories and events, not SQLite or IndexedDB details.
- `ui` owns design tokens and reusable interaction patterns, with platform-specific implementations only where needed.
- Apps compose capabilities through dependency injection.
- Native APIs such as notifications, secure storage, files, and purchases are accessed through adapters.
- The mobile app must not make screen components responsible for authentication, sync, billing, or pack downloads.
- API contracts must be versioned and testable independently of React Native.

### Persistence and sync strategy

Keep a repository contract equivalent to the current Kotlin repositories. Use a local database implementation on mobile, a durable sync queue, and a server-side event or mutation API. The first migration should preserve the current entities:

- `exercise_attempt`.
- `daily_activity`.
- `lesson_completion`.
- `lesson_run_settlement`.
- `review_card`.
- `streak_freeze`.
- `settings`.
- `installed_pack` and `pack_version`.

Add cloud-facing entities or event envelopes for:

- `user` and `device_session`.
- `progress_event` or an equivalent idempotent mutation log.
- `sync_cursor` and failed-upload retry state.
- `subscription_entitlement`.
- `notification_preference`.
- `marketplace_install` and `pack_report`.

The app should write domain events or transactional records locally before calculating derived dashboard values. The sync layer uploads them with client-generated IDs and receives server acknowledgements. This preserves the current protection against abandoned lessons awarding XP and repeated completion callbacks paying twice while allowing the learner to work offline.

### React Native migration sequence

1. Freeze current content and export a golden JSON fixture from the existing course compiler.
2. Implement `@learn/core` with parity tests for answer grading, XP, daily goals, streaks, Leitner scheduling, and lesson settlement.
3. Implement `@learn/content` and validate the golden fixture against the new schema.
4. Build the React Native mobile shell, authentication boundary, local database, and sync queue.
5. Add Home, Path, Lesson, Review, Stats, and Settings using the same core, schema, tokens, and interaction components.
6. Add account linking, cloud progress sync, remote pack installation, and update handling after bundled-pack parity is proven.
7. Add subscriptions, notifications, analytics consent, and managed AI behind service interfaces.
8. Add deferred web and Electron clients only after the mobile product has proven retention and paid demand.

### Migration acceptance criteria

- A learner can complete the existing Kotlin course in the React Native client with the same answer results and XP totals as the Android app.
- A lesson abandoned before settlement adds no completion, XP, daily-goal credit, or review seed.
- Repeating a completion callback never pays twice.
- Progress survives app restart and syncs across two mobile devices.
- A lesson completed offline settles locally and uploads exactly once after reconnecting.
- The same JSON pack renders on iOS and Android without platform-specific content forks.
- Existing SVG assets render without loss of fidelity and remain usable in light and dark themes.
- The legacy Android app can remain available while the first React Native client reaches feature parity.

## 12. Asset Reuse and Brand System

The current `assets/brand` directory is a strong foundation for a shared package.

### Reuse plan

- Treat SVG masters as the source of truth.
- Move or mirror them into `packages/ui/assets/brand` without converting them to platform-specific formats prematurely.
- Render SVG directly on web and Electron.
- Render SVG through `react-native-svg` or a precompiled compatible asset path on React Native.
- Expose semantic names such as `MascotWave`, `MascotCelebrate`, `ReviewDone`, `StreakFlame`, and `LessonTrophy` instead of raw filenames.
- Recreate the current color and typography tokens in a platform-neutral theme package.
- Preserve the existing Octo Coach silhouette, warm palette, and accessibility labels while adding variants.

### Refinement backlog

- Add neutral, encouraging, focused, confused, and celebration poses for more learning states.
- Add high-contrast and reduced-motion variants.
- Add responsive crops for compact mobile cards, desktop empty states, and notifications.
- Add a reusable lesson-completion animation that can fall back to a static SVG.
- Add a small icon set for question types and pack status.
- Define a brand usage guide so community packs cannot accidentally impersonate first-party courses.

## 13. Monetization and Business Model

The business model should combine a generous free learning path with a clear paid upgrade. Revenue should not make the free product feel unusable.

### Free tier hypothesis

- Access to a rotating or starter set of first-party courses.
- Account creation and basic cloud sync so progress is not trapped on one phone.
- Daily lessons and review with a reasonable life/energy allowance.
- Non-intrusive ads at natural breaks, never inside a question, code editor, or explanation.
- Optional rewarded ad for a life refill or bonus practice, with an equivalent non-ad path over time.
- Local progress and basic streaks.
- Limited AI or bring-your-own-key tutor support.

### Paid subscription hypothesis

- No ads.
- Unlimited or substantially expanded practice and lives.
- Full first-party course catalog.
- Offline downloads and priority pack updates.
- Advanced interview modes, mock playlists, and insights.
- Managed AI tutor allowance with transparent usage limits, or a cheaper BYOK tier.
- Expanded cross-device history, exports, and account recovery.
- Subscriber-only courses or expert-reviewed learning paths.

### Future revenue streams

- Team or school plans with admin dashboards.
- Expert-authored premium courses with revenue sharing.
- Interview-prep bundles or time-limited cohorts.
- Marketplace fees for verified third-party course packs.

### Monetization guardrails

- No ad on app launch, during active recall, or immediately after a mistake.
- Never remove a learner's earned progress because they do not subscribe.
- Make subscription renewal, cancellation, and trial terms explicit.
- Do not sell learner code, answers, or progress data.
- Let learners disable non-essential notifications and ad personalization.
- Avoid unlimited-notification strategies; notification quality is a product feature.

Leaderboards and leagues are future engagement layers. The data model should record anonymized, opt-in XP and course events now, but public rankings should wait until there is enough content, anti-cheat protection, privacy control, and a healthy daily cohort.

## 14. Notifications and Habit Design

Notification categories:

- **Daily goal:** progress reminder only when useful.
- **Review due:** a compact count and direct deep link.
- **Streak risk:** only when the learner has opted in and has not met the goal.
- **Pack updates:** opt-in per pack.
- **Milestones:** occasional, user-controlled celebrations.

Defaults should be conservative. The product should learn the learner's active hours locally before sending reminders, and it should stop reminders after repeated dismissal.

## 15. Metrics and Success Criteria

### Product health

- Activation: percentage of new users who complete their first exercise.
- First-week retention: users returning on at least three separate days.
- Lesson completion rate.
- Review completion rate and review accuracy.
- Median session length and exercises per session.
- Percentage of learners who complete a course milestone.
- Pack install success rate and update failure rate.

### Learning quality

- Accuracy improvement on repeated topics.
- Time from first exposure to successful review.
- Mistake-retry success rate.
- Interview problem completion and test-pass rate.
- Self-reported confidence before and after a learning path.

### Business

- Free-to-paid conversion.
- Trial-to-paid conversion and cancellation rate.
- Subscription revenue per active learner.
- Ad impressions per free learner, monitored alongside session abandonment.
- AI cost per paid learner.
- Course-pack retention and repeat use.

### Guardrail metrics

- Notification opt-out rate.
- Ad-related abandonment.
- Content reports and unsafe-pack reports.
- Pack validation failures.
- Crash-free sessions.
- Data-loss incidents and sync conflicts.

Initial targets should be set after a private beta baseline rather than guessed from the existing personal-use app.

## 16. LinkedIn-ready public beta

The first public milestone should be a real mobile beta that demonstrates a focused learning experience, not a long feature list.

### Beta promise

> A mobile coach that helps developers understand the right programming idea, method, or logical step before they face a real interview problem.

### Beta must include

- React Native iOS and Android builds with a polished onboarding flow.
- Guest mode plus account creation and cloud progress sync.
- One excellent Kotlin/Android interview-prep course, not a shallow catalog of languages.
- Home, Path, Lesson, Review, Stats, and Settings flows.
- Code examples, guided reasoning questions, mistake explanations, and multi-part practice.
- Streak, daily goal, XP, review queue, and the capped pace bonus.
- Offline lesson and review sessions with reliable sync after reconnecting.
- A first-party course catalog plus a clearly labeled **Add community pack** flow.
- Crash reporting, analytics consent, account deletion, data export, support contact, and pack reporting.
- Reusable Octo Coach artwork, app icon, screenshots, and a 30–60 second product demo.

### Defer from the beta

- Public leaderboards and leagues.
- A large open marketplace with payments to creators.
- Ads inside lessons or a complicated lives economy.
- Arbitrary code execution and a full code editor.
- Multiple languages unless the Kotlin experience is already excellent.
- Web and Electron clients.

### Launch proof to collect

Run a small invite-only beta before broad promotion. Instrument the funnel and interview users rather than relying on downloads alone. Initial working gates could be:

- At least 25 testers complete onboarding and one lesson.
- At least 10 testers return on three separate days in the first week.
- At least five testers finish a review session without direct assistance.
- At least five testers say they would use the app before an interview or recommend it to a peer.
- At least three testers express willingness to pay for a clear upgrade such as ad-free access, more courses, or advanced interview practice.

These are validation gates, not claims about expected conversion. If the beta does not create repeat use without heavy reminders, adding ads, leagues, or more languages is premature.

### LinkedIn launch package

- One sentence describing the user and outcome.
- One short demo showing a concept, code example, guided question, mistake explanation, and progress sync.
- Three screenshots: Home, a reasoning exercise, and Review or Stats.
- A public beta link, a feedback form, and a transparent note that the product is early.
- One measurable learning or usage result from testers, with permission.

## 17. Roadmap

### Phase 0: Product foundation

- Freeze the existing Kotlin experience as the behavioral reference.
- Export the current course to a golden JSON fixture.
- Define the first JSON Schema and pack metadata contract.
- Create the TypeScript monorepo and `core`, `content`, `storage`, and `ui` packages.
- Port domain parity tests before porting screens.

**Gate:** React core produces the same grading, XP, streak, review, and settlement outcomes for golden fixtures.

### Phase 1: React Native mobile parity

- Build Home, Path, Lesson, Review, Stats, and Settings.
- Render all five current exercise families.
- Reuse Octo Coach SVG assets and theme tokens.
- Implement local persistence and offline loading.

**Gate:** A learner can finish the existing Kotlin path on iOS and Android with persisted progress and no critical parity gaps.

### Phase 2: Connected mobile beta

- Add guest-to-account upgrade, cloud progress sync, retryable offline mutations, and conflict recovery.
- Add push notifications, deep links, analytics consent, subscription entitlement checks, and account deletion.
- Add first-party catalog and supported GitHub/community-pack installation.
- Test reconnection, duplicate delivery, background resume, device replacement, accessibility, and offline mode.

**Gate:** Two mobile devices can complete lessons online and offline without losing or double-awarding progress.

### Phase 3: Course-pack platform

- Add pack installer, registry/catalog, version checking, rollback, and removal.
- Publish a public starter pack repository template.
- Add authoring CLI, schema lint, preview, and CI validation.
- Add provenance, licensing, pack reports, and moderation hooks.

**Gate:** An external author can publish a valid pack without modifying client code.

### Phase 4: Business beta and deferred platforms

- Add subscription billing and entitlement checks.
- Add ad and rewarded-ad experiments in the free tier.
- Add managed AI with budget controls or keep BYOK as the default.
- Evaluate a web or Electron client only after mobile retention and paid demand are proven.

**Gate:** Paid and free experiences are understandable, stable, and do not compromise the core learning loop.

### Phase 5: Expansion

- Add a second language with a real runner or a language-specific guided-puzzle pack.
- Add verified community packs and creator workflows.
- Add more question types such as find-the-bug and code submission.
- Add team plans, cohorts, and interview playlists.

## 18. Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Cross-platform UI parity takes longer than expected | Slow migration | Port pure domain behavior first; use web as the reference renderer; allow platform-specific code editor surfaces |
| Third-party packs contain unsafe or low-quality content | Trust and safety issues | Validate, sanitize, attribute, hash, report, and moderate; never execute pack code directly |
| Arbitrary code runner is exploitable | Severe security and cost risk | Keep guided puzzles local; isolate public runners with strong sandboxing, quotas, and no network |
| Gamification becomes coercive | Retention and brand damage | Use notification/ad/lives guardrails and measure learning quality beside retention |
| Content authors break old progress | Learner frustration | Immutable IDs, semantic versioning, migration rules, and last-known-good rollback |
| AI costs outpace subscription revenue | Margin pressure | BYOK, usage caps, model routing, caching, and transparent quotas |
| Sync creates conflicting progress | Data loss | Event or attempt-based merge strategy, idempotent writes, exports, and conflict visibility |
| Course quality does not scale | Weak learning outcomes | Verification pipeline, expert review, learner reports, and pack-level quality status |

## 19. Open Decisions

1. Product name and domain: keep Learn Code, evolve learn-kotlin, or introduce a new consumer brand.
2. Whether React Native Web is sufficient for the browser client or whether the web UI should have a dedicated renderer using the same core and tokens.
3. Whether course installation accepts arbitrary GitHub URLs in v1 or only repositories/releases registered in a catalog.
4. Whether public packs are free, paid, or both once creator tooling exists.
5. Whether managed AI is included in subscription pricing or remains BYOK initially.
6. Which languages are the first expansion after Kotlin: Python, JavaScript/TypeScript, Swift, Java, or interview-neutral algorithms.
7. What code execution capabilities are necessary for the first paid interview experience.
8. What learner age range and privacy obligations the product will support.
9. Whether lives/energy improve learning behavior or only improve monetization; validate with a small experiment and guardrails.

## 20. SWOT and Business Viability

### Market signal snapshot

The category has real willingness to engage and pay, but it is not empty:

- Duolingo reports 52.7 million daily active users, 133.1 million monthly active users, and 12.2 million paid subscribers for Q4 2025. That validates the power of a freemium habit product, but it also sets an enormous benchmark for brand, content, and retention. [Duolingo company strategy overview](https://investors.duolingo.com/company-strategy-overview-0)
- LeetCode sells a paid interview-prep product with company-specific questions, premium content, faster judging, and AI features. Its public subscription page lists a monthly plan and an annual plan, showing that serious interview preparation has willingness-to-pay. [LeetCode Premium](https://leetcode.com/subscribe/)
- Codecademy uses a free/basic funnel and paid tiers that include career paths, technical interview prep, projects, and AI assistance. Its public pricing page lists paid monthly and annual tiers. [Codecademy pricing](https://www.codecademy.com/pricing)
- Brilliant combines bite-sized interactive learning, a tutor, daily limits, ads on free access, and ad-free unlimited subscriptions across mobile and web. That is a close model for the habit and interaction pattern, even though its subject mix is broader. [Brilliant Premium](https://brilliant.org/subscribe/)

These signals support the category, not this specific product. The product still has to prove that its narrower programming-reasoning wedge creates repeat use and paid conversion.

### SWOT

| Area | Analysis |
|---|---|
| **Strengths** | Existing mobile prototype with a five-unit Kotlin map, eight-card practice lessons, a distinctive Octo Coach brand, guided reasoning instead of an intimidating IDE, strong content validation patterns, and GitHub-backed course packs. |
| **Weaknesses** | One founder or small team may struggle to produce enough high-quality content; learning programming is harder to grade than vocabulary; cloud sync, billing, notifications, and moderation add operational complexity; the product lacks a proven audience and outcomes today. |
| **Opportunities** | Interview-prep paths, language-switching packs, Android/Kotlin specialization, verified community authors, cohort or team plans, creator revenue sharing, AI-assisted explanations, and later leaderboards or leagues built on top of healthy activity data. |
| **Threats** | Duolingo owns the habit-learning mental model; LeetCode owns technical interview depth; Codecademy owns structured coding paths; Brilliant owns interactive conceptual learning; free YouTube, GitHub, documentation, and AI tools are abundant; low-quality community packs or unsafe code runners could damage trust. |

### How likely is it to sell?

The honest answer is **moderately plausible as a focused niche product, unlikely as a generic “Duolingo clone for programming.”** A numeric probability would be false precision before user testing.

The strongest positioning is not “learn to code with XP.” It is:

> **Practice the programming reasoning you need for interviews, one small decision at a time, with explanations that show why the answer works.**

That wedge can sell if the product proves three things:

1. Learners return without being chased by notifications.
2. Learners feel more confident solving real questions after using it.
3. A meaningful minority pays for more depth, less friction, or a concrete interview outcome.

The business is weaker if it depends on ads alone. Ads need high volume and can undermine concentration; a subscription, interview bundle, team plan, or verified course marketplace is more likely to support a focused product. The most sensible order is:

1. Prove one excellent mobile course and the daily loop.
2. Prove cloud sync and repeat use with a small beta.
3. Test a simple paid upgrade: ad-free, advanced interview path, offline packs, and richer insights.
4. Add community packs only after validation and trust tooling exist.
5. Add creator economics, leagues, and broad language expansion only when supply and demand are visible.

### Business go/no-go signals

Continue investing if the beta shows repeat use, qualitative learning value, organic referrals, and at least some unprompted willingness to pay. Pause or reposition if users describe it as a fun quiz app but cannot name a programming skill they understand better, or if retention depends entirely on streak anxiety and notifications.

## 21. Definition of Done for the First React Milestone

The first React Native milestone is complete when:

- The existing Kotlin course is represented by a versioned JSON pack.
- The JSON pack passes structural and semantic validation.
- The React Native app loads the pack without hardcoded Kotlin lesson data.
- Home, Path, Lesson, Review, Stats, and Settings work with local persistence.
- All current question types render and grade correctly.
- Lesson settlement, streak, XP, daily goal, and review behavior match the current app's golden tests.
- Guest progress can be upgraded to an account and synced across two mobile devices.
- A lesson completed offline uploads exactly once after reconnecting.
- Community-pack installation has a validated import path, provenance display, rollback, and removal.
- The default lesson flow teaches concepts through code examples and guided decisions rather than opening a full IDE.
- Octo Coach SVG assets and the warm theme are reused through a shared UI package.
- A pack can be installed from the catalog or a supported GitHub source.
- No commit, release, or remote publication is required for this milestone.
