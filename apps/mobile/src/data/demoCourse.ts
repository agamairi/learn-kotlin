import type {
  Course,
  Chapter,
  Exercise,
  Lesson,
  MultipleChoiceExercise,
} from '@learn/core';

/**
 * Built-in demo course data matching the general Kotlin foundations curriculum.
 * Provides rich local demo data for the mobile beta shell before external pack loading is wired.
 */
const DEMO_COURSE_DRAFT: Course = {
  id: 'kotlin-foundations',
  language: 'kotlin',
  title: 'Kotlin for the Android Screen',
  description:
    'A practical Kotlin course for mobile engineers: clear syntax, reliable null safety, idiomatic data classes, collections, and testable functions.',
  chapters: [
    {
      id: 'ch01-syntax',
      title: 'Chapter 1 · Syntax, values & control flow',
      summary:
        'Translate familiar ideas into Kotlin. Practise immutable values, nullable types, data classes, and when expressions.',
      lessons: [
        {
          id: 'ch01-syntax-values',
          title: 'Kotlin values and data models',
          explanation:
            'Kotlin uses `val` for read-only references and `var` for reassigned ones. A `data class` models value-like app data and automatically generates `equals`, `hashCode`, `toString`, and `copy`.',
          exercises: [
            {
              id: 'ch01-mc-val',
              type: 'multiple_choice',
              prompt:
                'Which declaration makes the reference read-only after it is initialized?',
              code: 'val displayName = "Mina"',
              options: [
                'val displayName = "Mina"',
                'var displayName = "Mina"',
                'readonly displayName = "Mina"',
              ],
              correctIndex: 0,
              explanation:
                '`val` prevents reassignment of the reference; it is Kotlin’s closest match to a simple constant local binding.',
            } as MultipleChoiceExercise,
            {
              id: 'ch01-mc-data',
              type: 'multiple_choice',
              prompt:
                'What does a Kotlin `data class` provide automatically for ordinary value comparisons?',
              code: 'data class Profile(val id: String, val displayName: String)',
              options: [
                'Reference identity only',
                'equals, hashCode, toString, and copy',
                'An automatic network request',
              ],
              correctIndex: 1,
              explanation:
                'Data classes are designed for value-like structures and generate those useful members out of the box.',
            } as MultipleChoiceExercise,
            {
              id: 'ch01-mc-elvis',
              type: 'multiple_choice',
              prompt:
                'How do you provide a fallback of "Guest" when nickname is null?',
              code: 'val nickname: String? = null\nval label = nickname ?: "Guest"',
              options: [
                'nickname ?? "Guest"',
                'nickname ?: "Guest"',
                'nickname || "Guest"',
                'nickname.unwrapOr("Guest")',
              ],
              correctIndex: 1,
              explanation:
                'The Elvis operator `?:` evaluates to its right-hand operand if the left-hand operand is null.',
            } as MultipleChoiceExercise,
          ],
        },
        {
          id: 'ch01-syntax-when',
          title: 'Control flow with when expressions',
          explanation:
            'In Kotlin, `when` replaces the traditional switch statement and can be used as an expression returning a value. Each branch is evaluated in order until a match is found.',
          exercises: [
            {
              id: 'ch01-mc-when-exhaustive',
              type: 'multiple_choice',
              prompt:
                'When used as an expression, what must a `when` construct guarantee?',
              code: 'val label = when (state) {\n  UiState.Ready -> "Ready"\n  else -> "Loading"\n}',
              options: [
                'It must cover all possible cases (exhaustive) or include an else branch',
                'It must only compare integer literals',
                'It must execute asynchronously in a coroutine',
              ],
              correctIndex: 0,
              explanation:
                'When used as an expression, `when` must be exhaustive so the compiler knows a value is always produced.',
            } as MultipleChoiceExercise,
          ],
        },
        {
          id: 'ch01-syntax-nulls',
          title: 'Safe calls and null safety',
          explanation:
            'Kotlin type system strictly differentiates nullable types (`String?`) from non-nullable types (`String`). Use `?.` to invoke methods safely without throwing NullPointerException.',
          exercises: [
            {
              id: 'ch01-mc-safe-call',
              type: 'multiple_choice',
              prompt:
                'What is the resulting type of `profile?.displayName` if `profile` is `Profile?` and `displayName` is `String`?',
              code: 'val name = profile?.displayName',
              options: [
                'String?',
                'String',
                'Optional<String>',
                'Nothing',
              ],
              correctIndex: 0,
              explanation:
                'Safe navigation on a nullable receiver produces a nullable result `String?`.',
            } as MultipleChoiceExercise,
          ],
        },
      ],
    },
    {
      id: 'ch02-strings',
      title: 'Chapter 2 · Strings & text parsing',
      summary:
        'Practise string templates, multiline strings, trimming, and safe parsing for user input.',
      lessons: [
        {
          id: 'ch02-templates',
          title: 'String templates and formatting',
          explanation:
            'Kotlin string templates evaluate expressions directly inside double quotes using `$variable` or `${expression}`.',
          exercises: [
            {
              id: 'ch02-mc-template',
              type: 'multiple_choice',
              prompt:
                'Which syntax correctly inserts a user name into a greeting?',
              code: 'val userName = "Mina"\nval message = "Hello, $userName"',
              options: [
                '"Hello, $userName"',
                '"Hello, $(userName)"',
                '"Hello, %{userName}"',
              ],
              correctIndex: 0,
              explanation:
                'Expressions inside Kotlin string templates require curly braces `${expression}`.',
            } as MultipleChoiceExercise,
          ],
        },
      ],
    },
    {
      id: 'ch03-collections',
      title: 'Chapter 3 · Collections & transformations',
      summary:
        'Filter, map, associate, and fold task or profile data using standard Kotlin collection operations.',
      lessons: [
        {
          id: 'ch03-filter-map',
          title: 'Filter and Map on lists',
          explanation:
            'Kotlin collections provide eager functional transformations like `filter` and `map`. `listOf` creates an immutable read-only List.',
          exercises: [
            {
              id: 'ch03-mc-list',
              type: 'multiple_choice',
              prompt:
                'What type does `listOf(1, 2, 3)` return by default in Kotlin?',
              code: 'val tasks = listOf("read", "code")',
              options: [
                'List<String> (read-only interface)',
                'MutableList<String>',
                'ArrayList<String>',
                'Array<String>',
              ],
              correctIndex: 0,
              explanation:
                '`listOf` returns an immutable/read-only `List<String>`. Use `mutableListOf` if mutation is required.',
            } as MultipleChoiceExercise,
          ],
        },
      ],
    },
    {
      id: 'ch04-testing',
      title: 'Chapter 4 · Testing and verification',
      summary:
        'Write crisp unit tests with assertEquals, test boundary cases, and verify pure app logic.',
      lessons: [
        {
          id: 'ch04-assertions',
          title: 'Unit testing app logic',
          explanation:
            'Clear test assertions make app logic verifiable. In Kotlin/JUnit, `assertEquals(expected, actual)` verifies values.',
          exercises: [
            {
              id: 'ch04-mc-assert',
              type: 'multiple_choice',
              prompt:
                'In standard JUnit assertions, which argument comes first in `assertEquals`?',
              code: 'assertEquals(expected, actual)',
              options: [
                'expected',
                'actual',
                'message',
              ],
              correctIndex: 0,
              explanation:
                'In JUnit convention, `assertEquals(expected, actual)` expects the known good baseline first.',
            } as MultipleChoiceExercise,
          ],
        },
      ],
    },
    {
      id: 'ch05-interviews',
      title: 'Chapter 5 · Interview judgment',
      summary:
        'Use Kotlin fluency to explain trade-offs, edge cases, and the reasoning behind a clean implementation.',
      lessons: [
        {
          id: 'ch05-interview-tradeoffs',
          title: 'Explain the trade-off',
          explanation:
            'Strong interview answers connect a language feature to a constraint: correctness, readability, performance, or maintainability. Practise saying what you would choose and why.',
          exercises: [
            {
              id: 'ch05-mc-tradeoff',
              type: 'multiple_choice',
              prompt: 'Which first step makes an unfamiliar coding problem easier to reason about?',
              code: 'fun solve(input: List<Int>): Int { /* ... */ }',
              options: [
                'Clarify the input, output, and edge cases',
                'Write the longest implementation you can',
                'Skip examples and optimize immediately',
                'Choose a random collection type',
              ],
              correctIndex: 0,
              explanation:
                'Clarifying the contract turns a vague prompt into a small set of testable decisions before implementation begins.',
            } as MultipleChoiceExercise,
          ],
        },
      ],
    },
  ],
};

const MIN_LESSON_EXERCISES = 8;

function expandLessonForPractice(lesson: Lesson): Lesson {
  const sourceExercises = lesson.exercises;
  const exercises: Exercise[] = [...sourceExercises];
  let repeatIndex = 0;

  while (exercises.length < MIN_LESSON_EXERCISES && sourceExercises.length > 0) {
    const source = sourceExercises[repeatIndex % sourceExercises.length];
    const copyNumber = repeatIndex + 1;
    const repeatedFields = {
      id: `${lesson.id}-recall-${String(copyNumber).padStart(2, '0')}`,
      prompt: `Quick recall: ${source.prompt}`,
    };
    if ('explanation' in source) {
      exercises.push({
        ...source,
        ...repeatedFields,
        explanation: `${source.explanation} This repeat is intentional: retrieval from the same unit helps the idea stick.`,
      } as Exercise);
    } else {
      exercises.push({ ...source, ...repeatedFields } as Exercise);
    }
    repeatIndex += 1;
  }

  return { ...lesson, exercises };
}

export const DEMO_COURSE: Course = {
  ...DEMO_COURSE_DRAFT,
  chapters: DEMO_COURSE_DRAFT.chapters.map((chapter) => ({
    ...chapter,
    lessons: chapter.lessons.map(expandLessonForPractice),
  })),
};
