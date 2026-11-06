# Learn Code Mobile

The mobile client for Learn Code. It uses Expo-compatible React Native and TypeScript, with platform-neutral learning logic supplied by the workspace packages.

## Included experience

- First-run onboarding with guest mode and language selection.
- Kotlin, Swift, React, and TypeScript learning tracks.
- Five-unit island-map path with tappable lessons.
- Eight-card starter lessons with repeated same-unit practice.
- Guided reasoning cards with code examples instead of a full IDE.
- Failed-question queueing: missed cards move to the back of the lesson.
- Home, Review, Stats, and Settings tabs with floating navigation.
- XP, daily goals, streaks, streak freezes, review scheduling, pace bonuses, and lesson summaries.
- Octo Coach mascot, streak celebrations, and SVG pose assets.
- Local-first guest progress and sync-ready outbox boundaries.
- Language switching and a local-only BYOK settings prototype.

## Run from the repository root

```bash
npm install
npm run typecheck
npm test
npm run mobile:start
```

For a browser preview:

```bash
npm --workspace @learn/mobile run start -- --web
```

For native development:

```bash
npm --workspace @learn/mobile run ios
npm --workspace @learn/mobile run android
```

Native commands require the matching Xcode or Android Studio toolchain. The web preview is for development and UI review; mobile is the product target.

## Important implementation notes

- `App.tsx` currently composes the mobile screens and lesson player.
- `src/domain/state/AppContext.tsx` owns the local learner session, selected language, onboarding state, and sync boundary.
- `src/data/courseCatalog.ts` registers bundled course packs.
- `packages/core` owns grading, XP, streak, review, and settlement rules.
- `packages/content` owns the JSON contract, validation, hashes, and pack lifecycle.
- `packages/storage` owns local persistence and outbox storage adapters.
- `assets/octopus` contains reusable SVG pose masters; the in-app mascot is code-native for animation and portability.

## Current prototype limits

The account flow is a product shell, not real authentication. Guest progress is local. BYOK is stored in AsyncStorage for prototype purposes and must move to `expo-secure-store` before real provider requests. The sync API is an in-memory contract implementation. GitHub pack downloading, marketplace moderation, notifications, subscriptions, ads, and competitive features are planned in [`docs/PRD.md`](../../docs/PRD.md), but are not enabled yet.
