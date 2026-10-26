# learn-kotlin

## What it is

learn-kotlin is an Android app for learning Kotlin. Lessons are grouped into chapters. Lessons use multiple choice, tap-to-fill code blanks, predict-the-output questions, and guided multipart code puzzles. Code puzzles are graded locally from authored choices and need no keyboard or compiler connection. Lesson XP stays pending until you finish the entire lesson, including its mistake round; leaving early discards that run. The daily XP goal is 50 XP by default. A streak counts a day when that day's goal is met. The effective day rolls over at 4 am local time, so early-morning practice counts toward the day before. You earn one streak freeze for every 7 consecutive goal days, hold at most 2, and a freeze is spent automatically on a missed day. Review uses a five-box Leitner schedule. A correct answer moves a card to a longer gap of 1, 2, 4, 8 or 16 days. A wrong answer sends the card back to the first box. An optional AI tutor can explain mistakes and give hints, using your own API key. The first course, Kotlin for the Stripe Android screen, is built for interview prep.

## Install a release

Download the APK from the newest release on the GitHub Releases page: https://github.com/agamairi/learn-kotlin/releases

v0.1.0 is the first stable release for personal use. Its release APK is non-debuggable and keeps the alpha builds' signing certificate, so you can install it over an alpha without uninstalling or losing progress. Physical-device smoke testing was confirmed by Agam on 2026-10-24.

v0.1.1 adds Koda, an original fox mascot, an adaptive launcher icon with Android 13 themed-icon support, and matching vector artwork for streaks, XP, lessons and review. Editable SVG masters and the shared SVG/Android-vector generator are in [`assets/brand`](assets/brand/README.md). Home, lesson completion and review use the new artwork. The release assets also include a downloadable vector pack and preview sheet.

v0.1.3 fixes lesson feedback briefly turning red when advancing after a correct answer, and prevents repeated Continue taps from skipping a question.

v0.2.0 brings a restrained warm ivory, sage, and clay palette with matching light and dark modes, a curved learning path, more Koda poses, compact question guides, and a flying-mascot celebration every five consecutive correct lesson answers. Code blocks and the code editor wrap within the screen while preserving the original code. Settings now includes independent **Sound effects**, **Haptics**, and **Reduce motion** switches; the app also respects the device's animation setting. Correct answers use a light vibration and mistakes use a stronger pattern. This release fixes Android Keystore rejecting an AI key save with “Caller-provided IV not permitted.”

v0.3.0 adds an end-of-lesson mistake round. Missed quiz questions wait until the remaining questions are finished. A clay-coloured Koda banner says **Let’s review your mistakes**, then you can review them in order. A question missed again returns to the back of that review queue. The lesson completes when those questions are corrected; retries do not inflate XP or the completed-exercise count. Home, Review, Stats, and Settings now follow actual Google Stitch screen designs; the quiz layout keeps the existing style. Design exports and implementation notes are in [`docs/design/stitch`](docs/design/stitch/README.md).

v0.4.0 fixes the AI tutor treating normal provider response metadata as a connection failure. AI Settings accepts a custom model ID for every provider, fetches available models after saving a key, and supports refresh and search. Lesson answers and code-studio results now stay pending until the entire lesson, including the mistake round, is finished; quitting discards that run without adding XP, daily-goal credit, or a lesson completion. Code Studio also offers local Kotlin autocomplete using the current source and common language/library suggestions.

v0.5.0 replaces the learner-facing Code Studio with guided code puzzles. Choose one of four code options for each numbered blank, check your answers, and work through multipart stages without running a server. The original exercise and part IDs retain progress compatibility. Navigation, dashboard, status, and action icons are larger. The original compiler-verified solutions remain the source for the puzzle scaffolds.

The app needs Android 8.0 (API 26) or newer. Install the APK by sideloading it. When Android asks, allow installs from the app you opened the file with, such as your browser or file manager.

Lessons, code puzzles, and review work offline. The optional AI tutor needs internet access and your own API key.

## Optional compiler tooling for content authors

The app's code puzzles do not use a compiler server. The server remains available to verify course reference solutions and expected outputs during content development.

Follow [`tools/compiler-server/README.md`](tools/compiler-server/README.md) to build the pinned stable Kotlin 2.4.21 server and run it on the laptop's loopback interface. Run `./gradlew :content:verifyContent` against it after changing reference code or expected outputs. Normal app builds validate puzzle structure and reconstruction locally; GitHub CI does not start the compiler server.

The developer runner has no authentication or code isolation suitable for public hosting. Keep it on loopback for author verification. Legacy runtime clients and tests remain in the repository, but the learner UI has no compiler URL/token setup or compiler-dependent exercise controls.

## AI tutor

The AI tutor is optional. It needs an API key from Anthropic, OpenAI, Google Gemini or OpenRouter. Without a key, the AI controls show a message that asks you to add one.

Open **Settings**, then **AI Tutor (Bring Your Own Key)**. Choose a provider, type a model ID or choose a suggestion, paste your key, and tap **Save AI Settings**. Saving a key fetches the provider's model list; use **Refresh models** to retry and search to narrow the choices. You can enter a model manually even if discovery fails. **Test connection** checks the saved key without generating a paid completion: it uses the provider's model list, or OpenRouter's authenticated key endpoint. **Remove key** deletes the saved key.

For **OpenRouter**, the default model is `openrouter/auto`. You can enter any model slug from the OpenRouter catalog, such as `openai/gpt-6-luna`. Save after changing the model, provider or key before testing the connection. A successful key test does not guarantee model access or sufficient credits for a completion.

The key is encrypted with an AES-256-GCM key in the Android Keystore. The encrypted copy is stored in the app's database. The Keystore key never leaves the device. Android backup is on in the app manifest, so a backup can include the encrypted copy. A restored backup cannot decrypt it, so you add the key again on a new device.

If an earlier version could not save your key, update to v0.2.0 and save it again. Existing encrypted keys and progress retain their format; no database migration is needed for this update.

Requests use your key and go to the provider you chose. When you choose OpenRouter, it also routes requests to the model providers it selects. OpenRouter's and those providers' data policies apply. Each request includes this text:

- **Explain my mistake:** the exercise text, its starter code, your code, and the reference solution.
- **Hint:** the exercise text and its starter code.
- **Review my code:** the exercise text, its starter code, and your code.

Hidden tests are not sent. Your provider's terms and data policy apply to these requests.

The AI menu appears on guided code puzzles and multi-part problems. Checked card answers can also offer **More like this**. Multi-part problems hide AI controls while the mock timer runs.

- **Explain my mistake** asks for an explanation in three sentences or fewer.
- On guided code puzzles, choose **Hint 1**, **Hint 2**, or **Hint 3**: a nudge, an approach, or a near-solution hint. Authored course hints are separate. Multi-part problems offer a level-1 nudge.
- AI hints on guided code puzzles deduct 2, 5 and 10 XP for levels 1, 2 and 3 from the first-pass award; each distinct level is charged once. Multi-part nudges do not change the part award.
- **More like this** makes 3 extra AI-generated practice cards labeled unverified. They award no XP and do not enter review.
- **Review my code** asks for up to three short bullets on readability and idiomatic Kotlin.

Interview Mode turns off every AI action. Turn it on in Settings under **Interview Mode Default**, or with the **Interview Mode** switch on a multi-part problem.

## Build from source

You need:

- JDK 21. Check it with `java -version`.
- The Android SDK. Create `local.properties` in the repo root and set your SDK path. Android Studio creates this file for you. The file is not committed.

  ```properties
  sdk.dir=/path/to/Android/sdk
  ```

Build the full project, as CI does:

```sh
./gradlew build
```

Install the debug app on a connected phone or emulator. Run `adb devices` to check that the device is listed.

```sh
./gradlew :app-android:installDebug
```

Run the tests:

```sh
./gradlew :shared:jvmTest :runtime-kotlin:jvmTest :content:test :app-android:testDebugUnitTest
```

The Gradle wrapper is in the repo, so you do not install Gradle yourself. CI runs `./gradlew build` on pushes to `main` and `feat/*` branches, and on pull requests to `main`.

## Content

Course content is YAML in `content/kotlin/`. `course.yaml` lists the chapters in order. Each file in `content/kotlin/chapters/` holds one chapter. The format is described in `docs/CONTENT_SCHEMA.md`. Every id must be unique across the course. Never reuse an id after it is published.

Two Gradle tasks work with the content.

`./gradlew :content:compileContent` checks the YAML against the schema. It then writes `app-android/src/main/assets/courses/kotlin-stripe.json`, which the app reads. Every app build runs this task first. Commit the regenerated JSON with your YAML changes.

`./gradlew :content:verifyContent` checks the exercises against the running compiler server, using their reference solutions and expected outputs. It needs the optional author-verification server described above. The default URL is `http://127.0.0.1:8080`. For another port, add `-PcompilerServerUrl=http://127.0.0.1:8090`. The task rewrites `docs/content-verification.md`, so review that report before you commit it. The task fails if any check fails. Warnings appear in the report and do not fail the task.

## Project layout

- `shared/`: Kotlin Multiplatform (JVM and Android). Course models, study rules (streak, XP, daily goal, Leitner review, answer grading), the course loader, the AI tutor clients, the SQLDelight database, and the Android Keystore code in `src/androidMain`.
- `runtime-kotlin/`: Kotlin Multiplatform (JVM and Android). The Ktor client for kotlin-compiler-server, and the Kotlin language support.
- `content/`: JVM tool that compiles and verifies the course YAML.
- `app-android/`: The Jetpack Compose app, package `dev.agam.learn`. Screens, view models, navigation, dependency setup, and the course JSON in `src/main/assets/courses/`.

Outside the modules, `docs/` holds the plan, the content schema, the spike report, the AI provider notes, and the content verification report. `tools/compiler-server/` holds the scripts and Dockerfile for the local compiler server.

## Adding another language

This is not built yet. The steps are in `docs/PLAN.md`, section 4 ("Adding a language later"), and phase 4 is in section 9. In short, add a `content/<language>/` folder with a `course.yaml`. Then implement `LanguageSupport` and `CodeRunner` for the new language. The plan aims to leave the shared domain and UI code unchanged. `runtime-kotlin/src/commonMain/kotlin/dev/agam/learn/runtime/kotlin/KotlinLanguageSupport.kt` shows the Kotlin version.

## Known limits

- Kotlin is the only language, and there is one course.
- The app is for one person. There are no accounts and no sync. Progress is stored on the device.
- Code puzzles teach through authored choices rather than arbitrary code execution. There is no learner-facing compiler or hosted runner.
- The compiler server is for personal practice only. It has no authentication, learner code runs as root with internet access, and learner code can forge a pass. Learner code can also stop the server from answering until you restart it with `tools/compiler-server/run.sh`. The server handles one request at a time.
- Puzzle answer keys, reference code and author tests ship in the app for offline self-study; they are readable from the APK.
- AI answers are not verified. Only the course exercises are checked against the compiler.
- The stable APK is a non-debuggable release build signed with the same development certificate as the alpha builds for update compatibility. A separate private production signing key would require a new installation; this release remains for personal use.
- Provider model names and API details can change. Check `docs/AI_PROVIDERS.md` before a wider release.
