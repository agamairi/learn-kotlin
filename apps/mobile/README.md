# Learn Code Mobile

The mobile client for Learn Code. It uses Expo-compatible React Native and TypeScript, with platform-neutral learning logic supplied by the workspace packages.

## Included experience

- First-run onboarding with guest mode and language selection.
- Kotlin, Swift, Flutter/Dart, React Native/TypeScript, and Java interview tracks.
- Five-unit island-map path with tappable lessons.
- Fifty interview lessons, each with eight reasoning cards, across five courses.
- Guided reasoning cards with code examples instead of a full IDE.
- Failed-question queueing: missed cards move to the back of the lesson.
- Home, Review, Stats, and Settings tabs with floating navigation.
- XP, daily goals, streaks, streak freezes, review scheduling, pace bonuses, and lesson summaries.
- Octo Coach mascot, streak celebrations, and SVG pose assets.
- Local-first guest progress in SQLite with transactional settlement.
- Manual `.learncode` backup/restore through the native file/share UI.
- No account, cloud-sync, AI, marketplace, or BYOK controls in the beta surface.

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
- `src/domain/state/AppContext.tsx` owns the guest learner session, selected language, onboarding state, durable progress hydration, and lesson settlement.
- `src/data/courseCatalog.ts` registers bundled course packs.
- `src/domain/persistence/sqliteProgressRepository.ts` owns the durable SQLite schema, migrations, backup payloads, and duplicate-safe transactions.
- `src/domain/persistence/backupFiles.ts` bridges versioned backups to the Android document/share UI.
- `packages/core` owns grading, XP, streak, review, and settlement rules.
- `packages/content` owns the JSON contract, validation, hashes, and pack lifecycle.
- `packages/storage` owns local persistence and outbox storage adapters.
- `assets/octopus` contains reusable SVG pose masters rendered by the mascot component.

## Current prototype limits

The app is intentionally guest-only for this beta. Progress is local SQLite data and manual backups are user-controlled files; there is no cloud sync. GitHub pack downloading, marketplace moderation, notifications, AdMob/UMP, lifetime ad removal, and competitive features are planned in [`docs/PRD.md`](../../docs/PRD.md), but are not enabled in this build.
