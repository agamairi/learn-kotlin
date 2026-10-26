# Content format spec (v2: guided code puzzles)

This is the contract between content authors and the app. The content compiler validates every file against it, and the app's `@Serializable` models mirror it field for field. Field names are snake_case in YAML and JSON; Kotlin properties use camelCase with `@SerialName`.

## Files and folders

- `content/<language>/course.yaml`: one per course. Fields: `id`, `language` (for example `kotlin`), `title`, `description`, `chapters` (an ordered list of chapter file names, without extension).
- `content/<language>/chapters/<chapter-id>.yaml`: one per chapter. Fields: `id` (must equal the file name without `.yaml`), `title`, `summary`, `lessons`.
- Ids are lowercase kebab-case, unique across the whole course, and never reused once published.

## Lesson

- `id`, `title`
- `explanation`: Markdown. Short (aim for under 250 words). Code in fenced blocks.
- `exercises`: an ordered list, each item one of the exercise types below.

## Exercise types

Every exercise has `type`, `id` and `prompt` (Markdown). Other fields by type:

### `multiple_choice`
- `code` (optional): a code snippet shown above the options; it must compile if it is complete code
- `options`: 2 to 5 strings, exactly one correct
- `correct_index`: zero-based index into `options`. Across a chapter, correct answers must be spread over positions (no more than about 40% at any one index)
- `explanation`: shown after answering

### `cloze`
- `template`: code containing one or more blanks, each written as `____` followed by a number, for example `____1`, `____2`
- `blanks`: a list, in order, each with `placeholder` (for example `____1`), `accepted` (a list of accepted answers), `case_sensitive` (boolean, default true), and `options` (exactly four distinct, nonempty code strings). Exactly one option must match an accepted answer under the blank's trim/case rules. Authored course cards require choices; legacy AI-generated cloze cards may omit options and use text input.
- `explanation`

### `predict_output`
- `code`: a complete program with `fun main()`
- `expected_output`: exact text printed, lines separated by `\n`. Graders compare after normalizing line endings and ignoring trailing whitespace on each line and trailing newlines at the end
- `explanation`

### `write_function`
- `guided`: a code-puzzle object described below; this is the learner-facing exercise.
- `starter_code`: original function signatures retained for author tests and optional AI context (can contain `TODO()`)
- `hidden_tests`: a Kotlin JUnit 4 test class (`import org.junit.Test`, `import org.junit.Assert.assertEquals` and friends; no `kotlin.test`), run with the learner's file. Tests may only call functions and types declared in `starter_code`, so `starter_code` must declare every signature the tests use (bodies can be `TODO()`)
- `reference_solution`: a complete solution that must pass `hidden_tests` (checked by the optional live content verifier)
- `hints`: 0 to 3 strings, in increasing order of help
- `explanation` (optional)

### `multi_part_problem`
- `story`: Markdown context for the whole problem
- `parts`: an ordered list. Each part has `guided` (the learner-facing code puzzle), `id`, `prompt`, `starter_code` (optional; later parts start from the learner's previous code, so new parts add new functions instead of changing earlier signatures), `hidden_tests` (testing this part), `reference_solution` (the complete file through this part, so it passes this part's tests and every earlier part's tests), `expected` (Markdown describing the expected result shown to the learner after passing), `hints` (optional, 0 to 3 strings). Every part is a coding part with tests; a discussion-only final part goes in the problem-level `discussion` field, never in `parts`
- `faq`: a list of `question` / `answer` pairs (the clarifying answers)
- `discussion` (optional): Markdown for a discussion-only final part
- `source_note` (optional): where the problem comes from, for example "Stripe's published sample, paraphrased"

### Guided code-puzzle object

`write_function.guided` and every `multi_part_problem.parts[].guided` contain:

- `template`: the complete original `reference_solution` scaffold with three to five meaningful code spans replaced by numbered blanks.
- `blanks`: the same placeholder/accepted/case/options structure as choice-based cloze. Each placeholder occurs once and has four authored choices with exactly one accepted equivalent.
- `explanation` (optional): concise feedback about the chosen code decisions.

Substituting each blank's canonical accepted value must reconstruct the owning reference solution exactly, allowing only line-ending normalization. The content validator checks that relationship. Keep the original prompt, story, expected result, IDs, reference solution and tests; do not replace the original algorithm with a smaller unrelated example. Long cumulative scaffolds can be shown through focused blank context in the app.

## Rules the content compiler enforces

1. Every id is unique across the course.
2. `correct_index` is within `options`.
3. Every cloze or guided-code placeholder appears exactly once, every `____N` has a declaration, and each authored blank has four distinct nonempty options with exactly one accepted equivalent. All function/part exercises require guided data that reconstructs their original reference solution.
4. `predict_output` programs compile and print exactly `expected_output` (checked by the optional live content verifier).
5. Every `reference_solution` passes its `hidden_tests` (checked by the optional live content verifier).
6. Markdown fields are non-empty.

## Source material for the Kotlin course

The existing study page at `/path/to/stripe-android-terminal/stripe-screen-sprint.html` holds the verified content: 8 lessons with code and drills, 17 multi-part problems with verified expected outputs and clarifying answers, the playbook and the mock rubric. All expected outputs there were checked by running code; copy them exactly.
