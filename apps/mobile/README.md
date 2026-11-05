# Learn Kotlin Mobile App Shell (`@learn/mobile`)

The initial focused, mobile-first beta shell for Learn Kotlin built with Expo-compatible React Native and TypeScript. It implements the warm, approachable learning experience designed for mobile devices—avoiding intimidating terminal or IDE interfaces in favor of bite-sized explanations, clean code examples, and guided reasoning cards.

---

## Features & Screens

1. **Onboarding (Guest / Account Choice)**
   - Welcoming screen featuring **Octo Coach**, the purple octopus learning mascot.
   - Choice between **Continue as Guest** (offline local session, no login required) and **Sign In / Create Account** (enables cloud sync).
   - Easily switch or sign out anytime from Settings.

2. **Home Dashboard**
   - **Header**: Top-level streak counter (`🔥 3`), streak freeze balance (`❄ 1`), and a visible **Sync-Status Affordance**.
   - **Morning Check-In Card**: Friendly greeting with Octo Coach and daily focus prompt.
   - **Next Lesson Hero Card**: Prominent one-tap entry into the next curriculum lesson (`Continue lesson →`).
   - **Daily Practice Goal Card**: Real-time progress bar tracking XP earned towards the daily target (e.g. `20 / 30 XP`) powered by `@learn/core`'s `DefaultDailyGoalTracker`.
   - **Spaced Review Card**: Summary of flashcards due today with direct jump to the Review tab.
   - **Curriculum Path Stepper**: Tactile stepping nodes visualizing progress across chapters.

3. **Curriculum Path**
   - Island-map path across five Kotlin foundations units with tappable lesson islands.
   - Tactile lesson nodes displaying status:
     - **Completed** (sage checkmark `✓`)
     - **Current / Unlocked** (active highlighted border `▶`)
     - **Locked** (muted lock icon `🔒`)
   - Tapping any unlocked lesson opens the interactive Lesson Screen.

4. **Guided Reasoning Lesson Screen**
   - **Approachable, Not an IDE**: Free of arbitrary code execution, terminal emulators, or complex editors.
   - **Header Bar**: Progress ratio across eight practice cards, close confirmation, and session XP counter.
   - **Octo Coach Bubble**: Pedagogical guidance and gentle reasoning hints.
   - **Bite-Sized Explanation**: Concise theory introducing concepts like `val` vs `var`, nullable types, collections, and `data class`.
   - **Clean Code Snippet Box**: Warm code surface (`#F2EFE9`) with legible syntax typography.
   - **Guided Question Families**:
     - Multiple choice, cloze, predict-output, guided code-puzzle, and multipart cards.
     - Instant deterministic grading via `@learn/core`'s shared graders.
     - Contextual feedback banners with explanations and per-exercise XP policy.
   - **Celebration View**: Mascot celebration upon completing all exercises, updating streak and settling progress into the local outbox.

5. **Spaced Review (Leitner 5-Box Queue)**
   - Spaced repetition scheduler based on the 5-box Leitner intervals (`1`, `2`, `4`, `8`, `16` days) from `@learn/core`.
   - **Distribution Visualizer**: 5-column chart showing card counts across all Leitner boxes.
   - **Interactive Flashcard Drill**:
     - Question and code snippet prompt.
     - "Reveal Explanation" toggle.
     - Recall completion advances the card using `@learn/core`'s `LeitnerScheduler`.

6. **Stats & Analytics**
   - Metrics grid: Total XP, Current Streak, Best Streak, Freezes Held.
   - **7-Day Activity Chart**: Bar chart illustrating daily XP vs daily target.
   - **Curriculum Mastery**: Progress percentages across all chapters.

7. **Settings & Session Management**
   - Account switcher: Guest mode vs authenticated user.
   - **Daily Goal Selector**: Adaptive targets (`10`, `20`, `30`, `50` XP).
   - **Sync & Storage Management**: View pending outbox records, last synced timestamp, and trigger manual sync.
   - **Native Platform Adapters Status Matrix**: Inspect active stubs and required native dependencies.
   - Reset local demo state.

8. **Visible Sync-Status Affordance**
   - Docked badge in headers showing current sync state:
     - `Synced` (green check)
     - `Syncing` (pulsing indicator)
     - `Queued` (amber pending counter)
     - `Local guest` (clay badge)
     - `Offline` (offline indicator)
   - Tapping the badge opens a modal sheet with outbox statistics and quick sync actions.

---

## Design System & Palette

Directly derived from `Color.kt` and the Stitch design system:

| Token | Hex | Role |
| --- | --- | --- |
| `background` | `#F7F4EE` | Light warm ivory paper |
| `surface` | `#FFFDFA` | Off-white card surface |
| `primary` | `#4C6B5A` | Light sage primary action |
| `primaryContainer` | `#E7EBE6` | Soft sage container |
| `secondary` | `#946654` | Light clay / terra-cotta |
| `secondaryContainer`| `#F0E7E1` | Soft clay container |
| `tertiary` | `#806126` | Light ochre (streak flames, badges) |
| `text` | `#2D302C` | Warm charcoal ink |
| `codeSurface` | `#F2EFE9` | Warm neutral code container |

---

## Development & Verification Commands

All commands can be executed from the monorepo root or inside `apps/mobile`:

### Typecheck Commands

```bash
# Typecheck the mobile shell specifically
npm --workspace @learn/mobile run typecheck

# Or via tsc directly
npx tsc -p apps/mobile/tsconfig.json

# Typecheck all workspaces in the monorepo
npm run typecheck
```

### Start Commands

```bash
# Start Expo development server from monorepo root
npm run mobile:start

# Or start from workspace directly
npm --workspace @learn/mobile run start

# Launch on Android emulator / connected device
npm --workspace @learn/mobile run android

# Launch on iOS simulator (macOS)
npm --workspace @learn/mobile run ios
```

---

## Native Platform Adapters Status

To ensure zero native crash risks during early development, native platform bindings are cleanly separated into documented adapter interfaces. The table below details current implementation status:

| Adapter | Status | Active Fallback in Shell | Required Native Module |
| --- | --- | --- | --- |
| **Local sync storage** | `async_storage` | `AsyncStorageLocalSyncStore` persists the outbox, cursor, and applied event IDs | Migrate to `expo-sqlite` when queryable progress projections are added |
| **SecureStore Keychain**| `stubbed_memory` | In-memory token cache in `AppContext` | `expo-secure-store` / Android Keystore |
| **Haptic Feedback** | `unimplemented_native`| Safe silent no-op (no vibration) | `expo-haptics` / Android Vibrator |
| **Companion Audio / SFX**| `unimplemented_native`| Visual badges & emojis only | `expo-av` / Android SoundPool |
| **Network Reachability**| `mock_ready` | Assumes online state; manual offline toggle in store | `@react-native-community/netinfo` |

---

## Safety & Constraints Checklist

- [x] **Scoped strictly to `apps/mobile/**`**: No edits to root `package.json`, tsconfigs, packages, Kotlin files, or docs outside `apps/mobile`.
- [x] **No arbitrary code execution**: Code snippets are displayed for human reasoning; questions are evaluated deterministically using `@learn/core`'s `MultipleChoiceGrader`.
- [x] **No ads or trackers**: Clean pedagogical focus.
- [x] **No competitive leaderboards**: Encouragement focuses on individual daily goals and streaks.
- [x] **Mobile-first React Native**: Uses standard React Native components (`View`, `Text`, `TouchableOpacity`, `ScrollView`, `Modal`) wrapped in Expo configuration.
