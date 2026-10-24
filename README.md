# learn-kotlin

`learn-kotlin` is a Kotlin learning and practice platform combining bite-sized conceptual lessons, fill-in-the-blank cards, predicted outputs, and compiler-verified coding drills with gamified streaks, XP, Leitner spaced repetition, and optional AI tutoring.

## Architecture & Module Map

The project is structured as a multi-module Kotlin Multiplatform (KMP) and Android project using Gradle 9.6.0 and Android Gradle Plugin 9.4.1:

```
learn-kotlin/
├── shared/           Kotlin Multiplatform (JVM + Android library via AGP 9)
│   ├── model/        @Serializable Course, Chapter, Lesson, Exercise hierarchy, Progress types
│   ├── domain/       StreakCalculator, XpPolicy, DailyGoalTracker, ReviewScheduler, AnswerGrader
│   ├── runner/       CodeRunner, LanguageSupport, RunOutcome, Diagnostic contracts
│   ├── ai/           AiTutor, AiProvider, AiSettings, AiResult contracts
│   ├── content/      CourseRepository and Json CourseLoader
│   └── data/         SQLDelight LearnDatabase schema & ProgressRepository interface
├── runtime-kotlin/   KMP (JVM + Android) runtime binding for kotlin-compiler-server
│   ├── KotlinCompilerServerRunner (delegates to compiler server)
│   └── KotlinLanguageSupport (syntax keywords and runner binding)
├── content/          Plain Kotlin JVM tool & course compiler
│   ├── ContentCompiler (YAML -> JsonElement -> @Serializable Course -> JSON bundle)
│   ├── ContentValidator (enforces CONTENT_SCHEMA rules 1-3, 6)
│   └── Gradle task :content:compileContent
└── app-android/      Jetpack Compose Android application (namespace: dev.agam.learn)
    ├── Material 3 theme & navigation
    ├── DataStore, Lifecycle ViewModel Compose
    └── Assets bundle directory (assets/courses/*.json)
```

## Pinned Toolchain & Versions

- **Gradle:** 9.6.0
- **Android Gradle Plugin (AGP):** 9.4.1
- **Kotlin:** 2.2.10
- **JDK:** 21
- **compileSdk / targetSdk:** 36
- **minSdk:** 26
- **SQLDelight:** 2.0.2
- **Ktor:** 3.1.1
- **Compose BOM:** 2024.12.01

## How to Build

### Prerequisites
- JDK 21 installed (`java -version`)
- Android SDK installed with `sdk.dir` set in `local.properties` (e.g. `sdk.dir=/Users/agamairi/Library/Android/sdk`)

### Commands

- **Full Project Build & Verification:**
  ```bash
  ./gradlew build
  ```

- **Compile YAML Content to Android Assets:**
  ```bash
  ./gradlew :content:compileContent
  ```

- **Run Shared Multiplatform Tests (JVM):**
  ```bash
  ./gradlew :shared:jvmTest
  ```

- **Run Content Compiler Unit Tests:**
  ```bash
  ./gradlew :content:test
  ```

- **Assemble Android Debug APK:**
  ```bash
  ./gradlew :app-android:assembleDebug
  ```
