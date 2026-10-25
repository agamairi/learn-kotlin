# learn-kotlin

## What it is

learn-kotlin is an Android app for learning Kotlin. Lessons are grouped into chapters. Lessons use these exercise types: multiple choice, fill in the blank, predict the output, write a function, and multi-part problems. Code exercises run on a real Kotlin compiler, so your code is compiled and tested. You earn XP for correct answers and work toward a daily XP goal, 50 XP by default. A streak counts a day when that day's goal is met. The effective day rolls over at 4 am local time, so early-morning practice counts toward the day before. You earn one streak freeze for every 7 consecutive goal days, hold at most 2, and a freeze is spent automatically on a missed day. Review uses a five-box Leitner schedule. A correct answer moves a card to a longer gap of 1, 2, 4, 8 or 16 days. A wrong answer sends the card back to the first box. An optional AI tutor can explain mistakes and give hints, using your own API key. The first course, Kotlin for the Stripe Android screen, is built for interview prep.

## Install a release

Download the APK from the newest release on the GitHub Releases page: https://github.com/agamairi/learn-kotlin/releases

v0.1.0 is the first stable release for personal use. Its release APK is non-debuggable and keeps the alpha builds' signing certificate, so you can install it over an alpha without uninstalling or losing progress. Physical-device smoke testing was confirmed by Agam on 2026-10-24.

v0.1.1 adds Koda, an original fox mascot, an adaptive launcher icon with Android 13 themed-icon support, and matching vector artwork for streaks, XP, lessons and review. Editable SVG masters and the shared SVG/Android-vector generator are in [`assets/brand`](assets/brand/README.md). Home, lesson completion and review use the new artwork. The release assets also include a downloadable vector pack and preview sheet.

v0.1.3 fixes lesson feedback briefly turning red when advancing after a correct answer, and prevents repeated Continue taps from skipping a question.

v0.2.0 brings a warmer jade, coral, and gold palette in light and dark modes, a curved learning path with tactile lesson nodes, more Koda poses, question guides, and a flying-mascot celebration every five consecutive correct lesson answers. Code blocks and the code editor wrap within the screen while preserving the original code. Settings now includes independent **Sound effects**, **Haptics**, and **Reduce motion** switches; the app also respects the device's animation setting. Correct answers use a light vibration and mistakes use a stronger pattern. This release fixes Android Keystore rejecting an AI key save with “Caller-provided IV not permitted.”

The app needs Android 8.0 (API 26) or newer. Install the APK by sideloading it. When Android asks, allow installs from the app you opened the file with, such as your browser or file manager.

Lessons and review work offline. Code exercises need the compiler server, and the AI tutor needs internet access and your own API key. Sections 3 and 4 explain both.

## Code exercises and the compiler server

Code exercises send your code to kotlin-compiler-server. You run this server on your laptop. Code exercises do not work unless the server is running.

To start the server:

1. Start Docker Desktop.
2. Clone https://github.com/JetBrains/kotlin-compiler-server and check out commit `325cdb872475891da66fc4c9b05763de8f011e16`. The upstream project has no release tags, so pin the commit.
3. Build the clone on your Mac, not in Docker. Follow `tools/compiler-server/README.md`. It needs JDK 17 and JDK 21.
4. From the repo root, start the container. Set `KCS_CLONE` to the path of your clone:

   ```sh
   KCS_CLONE=/path/to/kotlin-compiler-server tools/compiler-server/run.sh
   ```

   The script builds the Docker image the first time. Run it with `--rebuild` after you rebuild the clone.

5. Check that the server is up. It prints `OK`.

   ```sh
   curl -s http://127.0.0.1:8080/health
   ```

The container restarts with Docker Desktop, unless you stop it. Stop it with `docker rm -f kotlin-compiler-server`. Running `tools/compiler-server/run.sh` again also clears a stuck learner process. If port 8080 is in use, run `KCS_PORT=8090 tools/compiler-server/run.sh` and use port 8090 in the app URL.

The server listens on 127.0.0.1 only. Docker publishes the port on the loopback address, so other devices on your network cannot reach it. Keep it that way. The server has no authentication. Learner code runs as root in the container and can reach the internet. Learner code can also forge a passing result. `docs/spike-report.md` has the details. Use this setup for one person's practice only.

The server uses stable Kotlin 2.4.21. Apply the pinned version patch and build it as described in `tools/compiler-server/README.md`. The Android app uses Kotlin 2.2.10, so compiler messages can differ between the two.

To point the app at the server:

- **Android emulator:** The default server URL is `http://10.0.2.2:8080`. That address reaches your laptop's loopback interface, so you do not need to change it.
- **Phone over USB:** Run `adb reverse tcp:8080 tcp:8080`. Then open Settings, set **Server URL** to `http://localhost:8080`, and tap **Save Compiler Settings**. Run the `adb reverse` command again after you reconnect the phone.

The app allows plain HTTP only for `localhost`, `127.0.0.1` and `10.0.2.2`. See `app-android/src/main/res/xml/network_security_config.xml`.

The **Bearer Token (optional)** field in Settings is optional. The local server does not check it.

## AI tutor

The AI tutor is optional. It needs an API key from Anthropic, OpenAI, Google Gemini or OpenRouter. Without a key, the AI controls show a message that asks you to add one.

Open **Settings**, then **AI Tutor (Bring Your Own Key)**. Choose a provider and a model, paste your key, and tap **Save AI Settings**. **Test connection** checks the saved key without generating a paid completion: it uses the provider's model list, or OpenRouter's authenticated key endpoint. **Remove key** deletes the saved key.

For **OpenRouter**, the default model is `openrouter/auto`. You can enter any model slug from the OpenRouter catalog, such as `openai/gpt-6-luna`. Save after changing the model, provider or key before testing the connection. A successful key test does not guarantee model access or sufficient credits for a completion.

The key is encrypted with an AES-256-GCM key in the Android Keystore. The encrypted copy is stored in the app's database. The Keystore key never leaves the device. Android backup is on in the app manifest, so a backup can include the encrypted copy. A restored backup cannot decrypt it, so you add the key again on a new device.

If an earlier version could not save your key, update to v0.2.0 and save it again. Existing encrypted keys and progress retain their format; no database migration is needed for this update.

Requests use your key and go to the provider you chose. When you choose OpenRouter, it also routes requests to the model providers it selects. OpenRouter's and those providers' data policies apply. Each request includes this text:

- **Explain my mistake:** the exercise text, its starter code, your code, and the reference solution.
- **Hint:** the exercise text and its starter code.
- **Review my code:** the exercise text, its starter code, and your code.

Hidden tests are not sent. Your provider's terms and data policy apply to these requests.

The AI menu appears on code exercises and multi-part problems. Card lessons have no AI buttons.

- **Explain my mistake** asks for an explanation in three sentences or fewer.
- **Hint** asks for a hint at level 1, 2 or 3. Level 1 is a nudge. Level 2 is an approach. Level 3 is close to the solution. On code exercises, the level is one more than the number of hint cards you have opened, up to 3.
- AI hints on code exercises cost 2, 5 and 10 XP for levels 1, 2 and 3.
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

`./gradlew :content:verifyContent` checks the exercises against the running compiler server, using their reference solutions and expected outputs. It needs the server from section 3. The default URL is `http://127.0.0.1:8080`. For another port, add `-PcompilerServerUrl=http://127.0.0.1:8090`. The task rewrites `docs/content-verification.md`, so review that report before you commit it. The task fails if any check fails. Warnings appear in the report and do not fail the task.

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
- Code exercises need the compiler server on your laptop. There is no hosted runner.
- The compiler server is for personal practice only. It has no authentication, learner code runs as root with internet access, and learner code can forge a pass. Learner code can also stop the server from answering until you restart it with `tools/compiler-server/run.sh`. The server handles one request at a time.
- The hidden tests for code exercises ship inside the app. Anyone with the APK can read them. That is fine for one person. A public release needs server-side tests.
- AI answers are not verified. Only the course exercises are checked against the compiler.
- The stable APK is a non-debuggable release build signed with the same development certificate as the alpha builds for update compatibility. A separate private production signing key would require a new installation; this release remains for personal use.
- Provider model names and API details can change. Check `docs/AI_PROVIDERS.md` before a wider release.
