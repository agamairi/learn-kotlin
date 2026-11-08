# Learn Code Android Beta Release

Status: **TESTER BUILD READY — STORE SETUP REQUIRED**. This handoff records a locally verified Android beta. It is not evidence that Google Play has accepted, published, or distributed the app.

## Build identity

- App: Learn Code
- Version: `0.1.0` (`versionCode 1`)
- Application ID: `com.learncode.mobile`
- Branch: `beta/android-mvp`
- Current repository HEAD: `39f89e0a33b78478eceb9bed46ef65ac7a3ef94e`
- Working tree: uncommitted changes; no commit or push was performed
- Minimum Android SDK: `24` (Android 7.0)
- Target Android API: `36`
- Compile SDK/build tools: `37` / `37.0.0`
- Native project: `apps/mobile/android`

## Verified artifacts

- APK: [`apps/mobile/android/app/build/outputs/apk/release/app-release.apk`](apps/mobile/android/app/build/outputs/apk/release/app-release.apk) — 83 MB; rebuilt after the five-course content integration; optimized release APK; installs and launches on the test emulator; APK Signature Scheme v2 verified.
- AAB: [`apps/mobile/android/app/build/outputs/bundle/release/app-release.aab`](apps/mobile/android/app/build/outputs/bundle/release/app-release.aab) — 59 MB; rebuilt after the five-course content integration; bundle task succeeded and JAR signature verified.
- Signing: both artifacts use the generated Android debug keystore (`CN=Android Debug`). They are suitable for local/tester installation, but this is not the final Play upload key. Configure a real release/upload keystore before Play submission.

## What is in this beta

- Guest onboarding and language/course selection.
- Bundled, validated Kotlin, Swift, Flutter/Dart, React Native/TypeScript, and Java interview-practice packs.
- Five beta packs contain 400 newly authored interview exercises: 5 units, 10 lessons, and 80 exercises per course.
- Five-unit learning paths with eight guided reasoning cards per lesson.
- Deterministic multiple-choice, cloze, guided-code, and output grading; free-text is not accepted as proof of correctness.
- Mistakes move to the back of the active queue and seed spaced review.
- SQLite-backed XP, streaks, hearts, review schedules, lesson completion, preferences, and course-isolated progress.
- Versioned `.learncode` backup export through Android’s share UI and transactional restore through the system document picker.
- Settings, daily goals, local reset, privacy/terms links, accessible touch targets, safe-area handling, and Koda mascot feedback.
- Optional AdMob/UMP and RevenueCat integration is guarded by configuration and remains safe when credentials are absent.
- No account, cloud sync, Firebase, remote course installation, marketplace, AI grading, social features, or leaderboard claims are exposed in the beta.

## SDK, emulator, and native verification

Installed SDK: `/Users/agamairi/Library/Android/sdk`.

Installed system image and AVD:

- `system-images;android-36;google_apis;arm64-v8a`
- AVD `learn-code-api36`, Pixel 6 profile, ARM64, API 36
- `adb devices -l`: `emulator-5554 device`
- `getprop sys.boot_completed`: `1`

The native project was synchronized without cleaning existing work:

```bash
cd apps/mobile
env ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  PATH="$HOME/Library/Android/sdk/platform-tools:$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/cmdline-tools/latest/bin:$HOME/Library/Android/sdk/build-tools/37.0.0:$PATH" \
  npx expo prebuild --platform android --no-install --no-clean
```

Release build commands:

```bash
cd apps/mobile/android
env ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  PATH="$HOME/Library/Android/sdk/platform-tools:$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/cmdline-tools/latest/bin:$HOME/Library/Android/sdk/build-tools/37.0.0:$PATH" \
  ./gradlew assembleRelease

env ANDROID_HOME="$HOME/Library/Android/sdk" \
  ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
  PATH="$HOME/Library/Android/sdk/platform-tools:$HOME/Library/Android/sdk/emulator:$HOME/Library/Android/sdk/cmdline-tools/latest/bin:$HOME/Library/Android/sdk/build-tools/37.0.0:$PATH" \
  ./gradlew bundleRelease
```

The release APK was installed with `adb install -r` and launched with `am start -n com.learncode.mobile/.MainActivity`. The optimized release build initially exposed an R8/WorkManager startup crash; explicit keep rules for WorkManager/Room/SQLite were added, the APK/AAB were rebuilt, and the release app then launched with `ReactNativeJS: Running "main"` and no fatal, JS, or linker errors.

## Verification results

- `npm test`: **112 passed, 0 failed** across API, content, core, and storage workspaces.
- `npm run typecheck`: **passed** for all workspaces, including mobile.
- `npm run validate:packs`: **7/7 bundled packs passed**, hashes verified; each beta pack has 1 course, 5 units, 10 lessons, and 80 exercises. Two historical starter packs also remain valid.
- `node --experimental-strip-types packages/content/authoring/verify-guided.ts`: Swift, Dart, Java, and TypeScript guided reference checks passed; Kotlin reconstruction passed schema validation and its executable check was skipped because `kotlinc` is unavailable.
- `npx expo-doctor`: **19/20 checks passed**. The one warning is Expo’s expected CNG/non-CNG notice because the generated `apps/mobile/android` folder exists alongside app config fields. Native Gradle build and runtime verification passed; this is not a build failure.
- `./gradlew assembleRelease`: **BUILD SUCCESSFUL**.
- `./gradlew bundleRelease`: **BUILD SUCCESSFUL**.
- APK install: **success** on `emulator-5554`.
- Release launch: **success** after R8 keep-rule fix; no fatal crash or React Native JS resolution error.

Real emulator smoke coverage performed:

1. Fresh onboarding, guest start, Kotlin selection, and Home navigation.
2. Lesson start, correct and incorrect answers, heart consumption, back-of-queue retry, 3/5 streak celebrations, lesson settlement, result summary, accuracy/perfect state, XP and pace bonus.
3. Force-stop/relaunch persistence; completed lesson and XP remained associated with the selected course.
4. Path map, Review, Stats, Settings, and floating bottom navigation.
5. Backup export through the Android share sheet, reset local progress, restore from Downloads, and restored progress verification.
6. Post-fix backup round-trip recovered 80 total XP, 2 lessons, 176 daily XP, streak 1, and the restored hearts state on Stats.
7. Final optimized release APK install and launch without Metro dependency.
8. Updated release APK opened the first lesson for all five beta courses; each rendered its course-specific prompt, code example, answer choices, and 1/8 lesson state. A Kotlin card was answered incorrectly and displayed deterministic feedback while moving the card to the back of the queue.

## Monetization and privacy configuration

Default beta behavior is safe without external credentials:

- AdMob/UMP package is present, but ads are disabled unless `EXPO_PUBLIC_ADS_ENABLED=true`.
- When enabled for development, `EXPO_PUBLIC_ADS_USE_TEST_UNITS` defaults to test units. Use only official test units and test devices during QA.
- Native test app IDs are in `apps/mobile/app.json`: Android `ca-app-pub-3940256099942544~3347511713`, iOS `ca-app-pub-3940256099942544~1458002511`.
- Runtime ad configuration uses `EXPO_PUBLIC_ADMOB_BANNER_UNIT_ID` and `EXPO_PUBLIC_ADMOB_REWARDED_UNIT_ID` only when test units are explicitly disabled.
- RevenueCat is guarded by `EXPO_PUBLIC_REVENUECAT_ANDROID_API_KEY`; default product/entitlement IDs are `remove_ads_lifetime`. No store sandbox purchase or verified entitlement was run in this environment.
- Ad reward handling only grants a heart after the SDK reward callback; missing ads, cancellation, offline use, and unconfigured billing leave lessons usable.

Required before public distribution:

- RevenueCat project, Google Play non-consumable product, entitlement, offering, and store test account.
- Production AdMob app/ad-unit IDs, UMP consent configuration, privacy-options verification, and test-device QA.
- Public HTTPS Privacy Policy and Terms URLs with owner/entity/contact details completed. Drafts are in [`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md) and [`docs/TERMS_OF_SERVICE.md`](docs/TERMS_OF_SERVICE.md).
- Final signing/upload key and Play App Signing configuration.
- Final Play Data safety declarations based on the signed AAB and enabled SDK configuration; see [`docs/GOOGLE_PLAY_DATA_SAFETY.md`](docs/GOOGLE_PLAY_DATA_SAFETY.md).

## Google Play closed testing

1. Create the Play Console app with package `com.learncode.mobile`.
2. Configure Play App Signing and upload the AAB signed with the approved upload key. Do not upload this locally debug-signed AAB as the final production artifact.
3. Complete the store listing, app-content declarations, target-audience details, Data safety form, privacy URL, and terms/support details.
4. Create a **closed testing** track and upload the signed AAB.
5. Add at least **12 genuine testers** and distribute the opt-in link.
6. Keep those testers continuously opted in for at least **14 days**.
7. Monitor crashes, ANRs, reviews, and feedback; upload incremented versions if needed.
8. After the requirement is satisfied, apply for production access. Internal testing alone does not satisfy the stated requirement.

No Play Console submission, review, approval, or distribution was performed from this environment.

## Content pack integration

Course authors should add a versioned JSON pack under `packages/content/packs/`, run `npm run validate:packs`, and add the pack filename to the documented catalog registration in `packages/content/packs/README.md` / `apps/mobile/src/data/courseCatalog.ts`. Stable kebab-case IDs are required for pack, course, unit, lesson, exercise, and review-card records. Supported guided exercise types and validation rules are enforced by `packages/content/src/validator.ts`; malformed packs are rejected before use.

## Known limitations

- Final Play signing, Play Console setup, and the 12-tester/14-day period require owner action.
- AdMob production IDs, UMP production consent configuration, and RevenueCat store configuration are not available here; only guarded/test configuration was exercised.
- Privacy and Terms files are publication-ready drafts, not legal advice or published URLs. Owner details and HTTPS hosting are required.
- Expo Doctor’s single CNG warning remains while the generated native Android project is present; this does not prevent the verified local Gradle build.
- Existing deprecation warnings come from Expo/React Native/third-party native dependencies; they do not block this build.
- Full end-to-end completion and backup round-trip were previously exercised with the Kotlin flow; the other four courses have representative lesson-load smoke coverage, while their complete eight-card settlement flows still merit separate QA before public distribution.
