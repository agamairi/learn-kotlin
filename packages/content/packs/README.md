# Interview question packs

The mobile beta registers five interview courses. Every beta pack has five units, two lessons per unit, and eight exercises per lesson.

| Pack | Course | Units | Lessons | Exercises |
| --- | --- | ---: | ---: | ---: |
| `kotlin-foundations.json` | Kotlin for Android Interviews | 5 | 10 | 80 |
| `swift-foundations.json` | Swift for iOS Interviews | 5 | 10 | 80 |
| `flutter-dart-interviews.json` | Flutter & Dart Interviews | 5 | 10 | 80 |
| `react-native-typescript-interviews.json` | React Native & TypeScript Interviews | 5 | 10 | 80 |
| `java-android-interviews.json` | Java for Android Interviews | 5 | 10 | 80 |

The previous `react-foundations.json` and `typescript-foundations.json` files remain for historical ID/reference purposes, but they are not beta catalog entries. Existing Kotlin and Swift pack IDs and course IDs remain stable; their interview curriculum uses new chapter, lesson, and exercise IDs. An existing completion on a starter lesson does not count as completion of a different interview lesson.

The authored source is in `packages/content/authoring/`. Regenerate the five beta JSON files and their canonical SHA-256 hashes with:

```bash
node --experimental-strip-types packages/content/authoring/build-beta-packs.ts
node --experimental-strip-types packages/content/authoring/verify-guided.ts
```

Only authored, supported exercise families are used: code-based `multiple_choice`, guided `cloze`, guided `write_function`, and `multi_part_problem`. `predict_output` currently requires a Kotlin `fun main()` program in the validator, so other-language output reasoning is written as code-based choice questions. Guided puzzles require three to five numbered blanks, four distinct options per blank, and exact reconstruction of the reference solution. The `hiddenTests` text describes boundary cases for content review; it is not executed in the app. Grading uses the guided choices.

IDs are lowercase kebab-case and must never be reused for a different question. Adding a new question should get a new ID even if it replaces an older exercise. Keep course, unit, and lesson order deliberate. Each explanation should justify the answer and address a tempting wrong approach.

Before publishing a community pack, run the loader with `verifyContentHash: true` and keep the `contentHash` in sync with the canonical payload.

From the repository root, validate every bundled pack in one command:

```bash
npm run validate:packs
```

The command checks structural and semantic rules, verifies declared SHA-256 hashes, and reports course, lesson, and exercise counts. A new pack becomes visible in the app after it is imported and registered in `apps/mobile/src/data/courseCatalog.ts`, its language ID is added to `apps/mobile/src/types/languages.ts`, and the ID is accepted by the selected-language guard in `apps/mobile/src/domain/state/AppContext.tsx`. Remote GitHub installation remains deferred for this Android beta.
