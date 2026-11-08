# Learn Code

Learn Code is a mobile-first, Duolingo-style programming and interview-prep app built with React Native and Expo. It teaches the reasoning behind a solution before asking learners to write or choose code. The default experience is a short guided lesson, not a full IDE.

The current client is mobile-first and runs on iOS and Android. Expo web is available as a development preview. Desktop and a production web client are intentionally deferred.

## What we built today

This milestone implements the first React Native product foundation from [`docs/PRD.md`](docs/PRD.md):

- **Onboarding:** welcome flow, guest mode, and first-course selection.
- **Language tracks:** Kotlin, Swift, Flutter/Dart, React Native/TypeScript, and Java selectors that switch the active course, home content, lesson path, and map.
- **Learning path:** a five-unit island map with tappable lesson nodes and five validated interview course packs.
- **Guided lessons:** eight practice cards per lesson, code examples, multiple-choice, cloze, predict-output, guided puzzle, and multipart question support.
- **Mistake queue:** failed cards move to the back of the current lesson queue instead of forcing an immediate retry.
- **Learning loop:** daily XP, streaks, streak freezes, review cards, lesson summaries, pace bonuses, and Octo Coach celebrations.
- **Mobile UI:** floating bottom navigation, Octopus mascot poses, milestone animations, warm visual system, Home, Path, Review, Stats, and Settings screens.
- **Local-first state:** guest progress in SQLite, transactional lesson settlement, duplicate-safe XP, streaks, hearts, review schedules, and a portable `.learncode` backup/restore flow.
- **Honest beta surface:** no fake sign-in, cloud-sync, AI, marketplace, or BYOK controls are exposed.
- **Course-pack foundation:** versioned JSON schema, validation and lifecycle helpers, content hashes, and 400 interview exercises across five beta packs.
- **Shared packages:** platform-neutral core learning rules, content loading, sync contracts, storage adapters, and UI tokens.

## Current boundaries

This is a polished product prototype, not the complete subscription business described in the PRD. The following are deliberately next steps:

- Real authentication and cloud sync backend.
- Secure native key storage and production AI provider requests.
- GitHub/community pack download, signature verification, moderation, updates, and marketplace discovery.
- Push notifications, AdMob/UMP, lifetime purchase entitlements, leaderboards, and leagues.
- Production web and desktop clients.

## Repository layout

```text
apps/mobile/                 Expo React Native application
packages/core/               Grading, XP, streak, review, and learning rules
packages/content/            JSON schema, loaders, validation, lifecycle, and packs
packages/api/                Sync and account contract boundary
packages/storage/            Local persistence and sync-store adapters
packages/ui/                 Shared brand and theme tokens
packages/content/packs/      Five beta interview packs plus historical starter packs
docs/PRD.md                  Product requirements, roadmap, and business analysis
```

The repository no longer contains the previous Kotlin/Gradle Android application. The React Native app and TypeScript packages are the source of truth.

## Requirements

- Node.js 20 or newer.
- npm 10 or newer.
- For iOS: macOS with Xcode and an iOS simulator or device.
- For Android: Android Studio, Android SDK, and an emulator or device.
- For native builds: the Expo-compatible native toolchain for the target platform.

## Install

From the repository root:

```bash
npm install
```

## Run the app

Start the Expo development server:

```bash
npm run mobile:start
```

Then scan the QR code with Expo Go, or use one of the platform commands:

```bash
# Web development preview
npm --workspace @learn/mobile run start -- --web

# iOS simulator or connected device
npm --workspace @learn/mobile run ios

# Android emulator or connected device
npm --workspace @learn/mobile run android
```

The web preview is useful for fast UI review, but mobile behavior is the release target.

## Verify the project

Run these from the repository root:

```bash
# Typecheck every workspace
npm run typecheck

# Run all TypeScript and Node test suites
npm test

# Validate only the content package
npm --workspace @learn/content test

# Validate all bundled packs and verify their declared hashes
npm run validate:packs

# Validate only the API package
npm run test:api

# Build a static Expo web export for bundling checks
npm --workspace @learn/mobile exec expo -- export --platform web --output-dir /tmp/learn-code-web-export
```

## Build an Android APK

The configured EAS preview profile produces an internal APK. Install EAS CLI if needed, authenticate with the Expo account that owns the project, and run:

```bash
npx eas build --platform android --profile preview
```

The profile is defined in [`apps/mobile/eas.json`](apps/mobile/eas.json). The first build requires `npx eas login` and `npx eas init` in `apps/mobile` so Expo can create a real project and Android signing credentials. Local native development can also use `npm --workspace @learn/mobile run android`.

## Adding course content

Course packs are JSON documents validated against [`packages/content/schema/course-pack.schema.json`](packages/content/schema/course-pack.schema.json). Beta packs live in [`packages/content/packs`](packages/content/packs):

- `kotlin-foundations.json`
- `swift-foundations.json`
- `flutter-dart-interviews.json`
- `react-native-typescript-interviews.json`
- `java-android-interviews.json`

Each beta pack contains five units, ten lessons, and 80 distinct exercises. The pack loader verifies structure, semantic rules, and the declared content hash. Run `npm run validate:packs` before opening a content PR. The current mobile catalog bundles known packs at build time; remote GitHub/community installation is a planned marketplace capability, not yet enabled in the client. See [`packages/content/packs/README.md`](packages/content/packs/README.md) for authoring and stable-ID rules.

When adding a bundled language, add the pack, register it in [`apps/mobile/src/data/courseCatalog.ts`](apps/mobile/src/data/courseCatalog.ts), and add its language type to [`apps/mobile/src/types/languages.ts`](apps/mobile/src/types/languages.ts). Keep IDs stable so progress remains compatible across content updates.

## Product direction

The PRD describes the path from this mobile foundation to a connected learning business: cloud sync, verified community packs, notifications, ethical monetization, optional AI coaching, interview paths, and later leagues and leaderboards. The current design principle remains consistent throughout: teach the method, show a concrete code example, and use practice to build durable understanding rather than putting a full IDE in front of a learner.

## License and status

This is an active product prototype. See [`docs/PRD.md`](docs/PRD.md) for the full product requirements, roadmap, SWOT analysis, business viability assessment, and open decisions.
