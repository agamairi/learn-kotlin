import type {
  Blank,
  ClozeBlank,
  CodePuzzle,
  CourseDefinition,
  CoursePack,
  Exercise,
  FaqItem,
  MultipleChoiceExercise,
  ProblemPart,
  Severity,
  ValidationIssue,
  ValidationResult
} from "./types.ts"
import { SUPPORTED_SCHEMA_VERSIONS } from "./types.ts"

const KEBAB_CASE = /^[a-z0-9]+(-[a-z0-9]+)*$/
const SEMVER_REGEX =
  /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/
const PLACEHOLDER = /____\d+/g
const PLACEHOLDER_SINGLE = /^____\d+$/
const UNNUMBERED_BLANK = /____(?!\d)/
const MAIN_FUNCTION = /\bfun\s+main\s*\(/

const MIN_OPTIONS = 2
const MAX_OPTIONS = 5
const EXACT_BLANK_OPTIONS = 4
const MIN_CODE_PUZZLE_BLANKS = 3
const MAX_CODE_PUZZLE_BLANKS = 5
const MAX_HINTS = 3
const MAX_SHARE_OF_ONE_INDEX = 0.4

export interface SemanticValidatorOptions {
  clientVersion?: string
}

class UniqueIds {
  private readonly firstUse = new Map<string, string>()
  private readonly report: (
    path: string,
    message: string,
    severity?: Severity,
    exerciseId?: string
  ) => void

  constructor(
    report: (
      path: string,
      message: string,
      severity?: Severity,
      exerciseId?: string
    ) => void
  ) {
    this.report = report
  }

  register(path: string, id: string, exerciseId?: string) {
    if (typeof id !== "string" || id.trim().length === 0) {
      this.report(path, "id cannot be blank", "error", exerciseId)
      return
    }
    if (!KEBAB_CASE.test(id)) {
      this.report(path, `id '${id}' must be lowercase kebab-case`, "error", exerciseId)
    }
    const previous = this.firstUse.get(id)
    if (previous !== undefined) {
      this.report(path, `id '${id}' is already used at ${previous}`, "error", exerciseId)
    } else {
      this.firstUse.set(id, path)
    }
  }
}

function parseSemver(v: string) {
  const match = SEMVER_REGEX.exec(v.trim())
  if (!match) return null
  return {
    major: parseInt(match[1], 10),
    minor: parseInt(match[2], 10),
    patch: parseInt(match[3], 10)
  }
}

export function compareSemver(v1: string, v2: string): number {
  const p1 = parseSemver(v1)
  const p2 = parseSemver(v2)
  if (!p1 || !p2) {
    return v1.localeCompare(v2)
  }
  if (p1.major !== p2.major) return p1.major - p2.major
  if (p1.minor !== p2.minor) return p1.minor - p2.minor
  return p1.patch - p2.patch
}

function duplicates<T>(items: T[]): T[] {
  const counts = new Map<T, number>()
  for (const item of items) {
    counts.set(item, (counts.get(item) ?? 0) + 1)
  }
  const result: T[] = []
  for (const [item, count] of counts.entries()) {
    if (count > 1) result.push(item)
  }
  return result
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

/**
 * Dependency-free semantic validator for course packs.
 * Enforces IDs, cloze/guided blank rules, reconstruction, options, answers, and compatibility.
 */
export function validateCoursePack(
  pack: unknown,
  options: SemanticValidatorOptions = {}
): ValidationResult {
  const issues: ValidationIssue[] = []

  const report = (
    path: string,
    message: string,
    severity: Severity = "error",
    exerciseId?: string
  ) => {
    issues.push({ severity, path, message, exerciseId })
  }

  if (!isRecord(pack)) {
    report("$", "Course pack must be an object")
    return {
      valid: false,
      issues,
      errors: issues.filter((i) => i.severity === "error"),
      warnings: []
    }
  }

  const ids = new UniqueIds(report)

  // 1. Pack-level metadata and compatibility
  validatePackMetadata(pack, ids, report, options)

  // 2. Courses hierarchy
  if (Array.isArray(pack.courses)) {
    pack.courses.forEach((course, cIdx) => {
      validateCourse(course, `$.courses[${cIdx}]`, ids, report)
    })
  }

  const errors = issues.filter((i) => i.severity === "error")
  const warnings = issues.filter((i) => i.severity === "warning")

  return {
    valid: errors.length === 0,
    issues,
    errors,
    warnings
  }
}

function validatePackMetadata(
  pack: Record<string, unknown>,
  ids: UniqueIds,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  options: SemanticValidatorOptions
) {
  // schemaVersion
  if (typeof pack.schemaVersion === "string") {
    if (!SUPPORTED_SCHEMA_VERSIONS.includes(pack.schemaVersion as any)) {
      report(
        "$.schemaVersion",
        `unsupported schemaVersion '${pack.schemaVersion}'; supported versions: ${SUPPORTED_SCHEMA_VERSIONS.join(", ")}`
      )
    }
  }

  // packId
  if (typeof pack.packId === "string") {
    ids.register("$.packId", pack.packId)
  }

  // version
  if (typeof pack.version === "string") {
    if (!SEMVER_REGEX.test(pack.version.trim())) {
      report("$.version", `version '${pack.version}' is not valid semantic version`)
    }
  }

  // text fields
  requireText(pack.title, "$.title", "title", report)
  requireText(pack.description, "$.description", "description", report)
  requireText(pack.language, "$.language", "language", report)
  requireText(pack.license, "$.license", "license", report)
  requireText(pack.contentHash, "$.contentHash", "contentHash", report)
  requireText(pack.updatedAt, "$.updatedAt", "updatedAt", report)

  // minimumClientVersion check
  if (typeof pack.minimumClientVersion === "string") {
    if (!SEMVER_REGEX.test(pack.minimumClientVersion.trim())) {
      report(
        "$.minimumClientVersion",
        `minimumClientVersion '${pack.minimumClientVersion}' is not valid semantic version`
      )
    } else if (options.clientVersion) {
      if (compareSemver(options.clientVersion, pack.minimumClientVersion) < 0) {
        report(
          "$.minimumClientVersion",
          `pack requires client version at least ${pack.minimumClientVersion}, but current client version is ${options.clientVersion}`
        )
      }
    }
  }

  // source url
  if (isRecord(pack.source)) {
    if (typeof pack.source.url === "string") {
      const url = pack.source.url.trim()
      if (!url.startsWith("https://")) {
        report("$.source.url", `source url '${url}' must use https:// protocol`)
      }
    }
  }
}

function validateCourse(
  course: unknown,
  path: string,
  ids: UniqueIds,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void
) {
  if (!isRecord(course)) return

  if (typeof course.id === "string") {
    ids.register(`${path}.id`, course.id)
  }

  requireText(course.title, `${path}.title`, "course title", report)
  requireText(course.description, `${path}.description`, "course description", report)

  if (Array.isArray(course.chapters)) {
    course.chapters.forEach((chapter, chIdx) => {
      validateChapter(chapter, `${path}.chapters[${chIdx}]`, ids, report)
    })
  }
}

function validateChapter(
  chapter: unknown,
  path: string,
  ids: UniqueIds,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void
) {
  if (!isRecord(chapter)) return

  if (typeof chapter.id === "string") {
    ids.register(`${path}.id`, chapter.id)
  }

  requireText(chapter.title, `${path}.title`, "chapter title", report)
  requireText(chapter.summary, `${path}.summary`, "chapter summary", report)

  if (Array.isArray(chapter.lessons)) {
    chapter.lessons.forEach((lesson, lIdx) => {
      validateLesson(lesson, `${path}.lessons[${lIdx}]`, ids, report)
    })
  }

  checkAnswerSpread(chapter, path, report)
}

function validateLesson(
  lesson: unknown,
  path: string,
  ids: UniqueIds,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void
) {
  if (!isRecord(lesson)) return

  if (typeof lesson.id === "string") {
    ids.register(`${path}.id`, lesson.id)
  }

  requireText(lesson.title, `${path}.title`, "lesson title", report)
  requireText(lesson.explanation, `${path}.explanation`, "lesson explanation", report)

  if (Array.isArray(lesson.exercises)) {
    lesson.exercises.forEach((exercise, exIdx) => {
      validateExercise(exercise, `${path}.exercises[${exIdx}]`, ids, report)
    })
  }
}

function validateExercise(
  exercise: unknown,
  path: string,
  ids: UniqueIds,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void
) {
  if (!isRecord(exercise)) return

  const exerciseId = typeof exercise.id === "string" ? exercise.id : undefined

  if (typeof exercise.id === "string") {
    ids.register(`${path}.id`, exercise.id, exerciseId)
  }

  requireText(exercise.prompt, `${path}.prompt`, "prompt", report, exerciseId)

  switch (exercise.type) {
    case "multiple_choice":
      validateMultipleChoice(exercise, path, report, exerciseId)
      break
    case "cloze":
      validateCloze(exercise, path, report, exerciseId)
      break
    case "predict_output":
      validatePredictOutput(exercise, path, report, exerciseId)
      break
    case "write_function":
      validateWriteFunction(exercise, path, report, exerciseId)
      break
    case "multi_part_problem":
      validateMultiPartProblem(exercise, path, ids, report, exerciseId)
      break
  }
}

function validateMultipleChoice(
  ex: Record<string, unknown>,
  path: string,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  const options = Array.isArray(ex.options) ? ex.options : []
  const count = options.length
  if (count < MIN_OPTIONS || count > MAX_OPTIONS) {
    report(`${path}.options`, `has ${count} options; use ${MIN_OPTIONS} to ${MAX_OPTIONS}`, "error", exerciseId)
  }

  options.forEach((opt, idx) => {
    requireText(opt, `${path}.options[${idx}]`, "option", report, exerciseId)
  })

  const trimmedOptions = options.map((o) => (typeof o === "string" ? o.trim() : ""))
  const dups = duplicates(trimmedOptions)
  dups.forEach((dup) => {
    report(`${path}.options`, `option '${dup}' appears more than once`, "error", exerciseId)
  })

  // correctIndex resolution
  let correctIndex: number | undefined
  if (typeof ex.correctIndex === "number") {
    correctIndex = ex.correctIndex
  } else if (typeof ex.correct_index === "number") {
    correctIndex = ex.correct_index
  } else if (isRecord(ex.grading) && typeof ex.grading.correctIndex === "number") {
    correctIndex = ex.grading.correctIndex
  }

  if (correctIndex === undefined || correctIndex < 0 || correctIndex >= count) {
    report(
      `${path}.correct_index`,
      `correct_index ${correctIndex} is out of range for ${count} options`,
      "error",
      exerciseId
    )
  }

  if (ex.code !== undefined && ex.code !== null) {
    requireText(ex.code, `${path}.code`, "code", report, exerciseId)
  }

  requireText(ex.explanation, `${path}.explanation`, "explanation", report, exerciseId)
}

function validateCloze(
  ex: Record<string, unknown>,
  path: string,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  const template = typeof ex.template === "string" ? ex.template : ""
  requireText(template, `${path}.template`, "template", report, exerciseId)

  if (UNNUMBERED_BLANK.test(template)) {
    report(
      `${path}.template`,
      "has a blank written as ____ without a number; use ____1, ____2, ...",
      "error",
      exerciseId
    )
  }

  const blanks = Array.isArray(ex.blanks) ? ex.blanks : []
  if (blanks.length === 0) {
    report(`${path}.blanks`, "must declare at least one blank", "error", exerciseId)
  }

  const declaredPlaceholders: string[] = []
  blanks.forEach((b) => {
    if (isRecord(b) && typeof b.placeholder === "string") {
      declaredPlaceholders.push(b.placeholder)
    }
  })

  duplicates(declaredPlaceholders).forEach((placeholder) => {
    report(`${path}.blanks`, `placeholder '${placeholder}' is declared more than once`, "error", exerciseId)
  })

  const inTemplateMatches = template.match(PLACEHOLDER) ?? []

  const isAiGenerated = Boolean(ex.aiGenerated || ex.ai_generated)

  blanks.forEach((blank, bIdx) => {
    if (!isRecord(blank)) {
      report(`${path}.blanks[${bIdx}]`, "blank must be an object", "error", exerciseId)
      return
    }
    validateBlank(blank, `${path}.blanks[${bIdx}]`, inTemplateMatches, !isAiGenerated, report, exerciseId)
  })

  // Template contains undeclared placeholders
  const uniqueInTemplate = Array.from(new Set(inTemplateMatches))
  uniqueInTemplate
    .filter((ph) => !declaredPlaceholders.includes(ph))
    .forEach((ph) => {
      report(`${path}.template`, `template contains '${ph}' with no matching blank`, "error", exerciseId)
    })

  requireText(ex.explanation, `${path}.explanation`, "explanation", report, exerciseId)
}

function validateBlank(
  blank: Record<string, unknown>,
  blankPath: string,
  inTemplate: string[],
  requireOptions: boolean,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  const placeholder = typeof blank.placeholder === "string" ? blank.placeholder : ""
  if (!PLACEHOLDER_SINGLE.test(placeholder)) {
    report(`${blankPath}.placeholder`, `placeholder '${placeholder}' must look like ____1`, "error", exerciseId)
  }

  const occurrences = inTemplate.filter((p) => p === placeholder).length
  if (occurrences !== 1) {
    report(
      `${blankPath}.placeholder`,
      `placeholder '${placeholder}' appears ${occurrences} times in template; it must appear exactly once`,
      "error",
      exerciseId
    )
  }

  const accepted = Array.isArray(blank.accepted) ? blank.accepted : []
  if (accepted.length === 0) {
    report(`${blankPath}.accepted`, "must list at least one accepted answer", "error", exerciseId)
  }

  accepted.forEach((ans, aIdx) => {
    requireText(ans, `${blankPath}.accepted[${aIdx}]`, "accepted answer", report, exerciseId)
  })

  const options = Array.isArray(blank.options) ? blank.options : []
  const caseSensitive = blank.caseSensitive !== false && blank.case_sensitive !== false

  if (requireOptions || options.length > 0) {
    if (options.length !== EXACT_BLANK_OPTIONS) {
      report(
        `${blankPath}.options`,
        `must declare exactly ${EXACT_BLANK_OPTIONS} options, found ${options.length}`,
        "error",
        exerciseId
      )
    }

    options.forEach((opt, oIdx) => {
      requireText(opt, `${blankPath}.options[${oIdx}]`, "option", report, exerciseId)
    })

    const trimmedOptions = options.map((o) => (typeof o === "string" ? o.trim() : ""))
    const distinctTrimmed = Array.from(new Set(trimmedOptions))
    if (distinctTrimmed.length !== options.length) {
      report(`${blankPath}.options`, "options must be distinct", "error", exerciseId)
    }

    const acceptedTrimmed = accepted.map((a) => (typeof a === "string" ? a.trim() : ""))
    const matchingCount = trimmedOptions.filter((opt) =>
      acceptedTrimmed.some((acc) =>
        caseSensitive ? opt === acc : opt.toLowerCase() === acc.toLowerCase()
      )
    ).length

    if (matchingCount !== 1) {
      report(
        `${blankPath}.options`,
        `options must contain exactly one accepted equivalent, found ${matchingCount}`,
        "error",
        exerciseId
      )
    }
  }
}

function validateCodePuzzle(
  puzzle: unknown,
  path: string,
  referenceSolution: string,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  if (!isRecord(puzzle)) {
    report(path, "guided puzzle must be an object", "error", exerciseId)
    return
  }

  const template = typeof puzzle.template === "string" ? puzzle.template : ""
  requireText(template, `${path}.template`, "template", report, exerciseId)

  if (UNNUMBERED_BLANK.test(template)) {
    report(
      `${path}.template`,
      "has a blank written as ____ without a number; use ____1, ____2, ...",
      "error",
      exerciseId
    )
  }

  const blanks = Array.isArray(puzzle.blanks) ? puzzle.blanks : []
  if (blanks.length < MIN_CODE_PUZZLE_BLANKS || blanks.length > MAX_CODE_PUZZLE_BLANKS) {
    report(
      `${path}.blanks`,
      `must declare ${MIN_CODE_PUZZLE_BLANKS} to ${MAX_CODE_PUZZLE_BLANKS} blanks, found ${blanks.length}`,
      "error",
      exerciseId
    )
  }

  const declaredPlaceholders: string[] = []
  blanks.forEach((b) => {
    if (isRecord(b) && typeof b.placeholder === "string") {
      declaredPlaceholders.push(b.placeholder)
    }
  })

  duplicates(declaredPlaceholders).forEach((ph) => {
    report(`${path}.blanks`, `placeholder '${ph}' is declared more than once`, "error", exerciseId)
  })

  const inTemplateMatches = template.match(PLACEHOLDER) ?? []

  blanks.forEach((blank, bIdx) => {
    if (!isRecord(blank)) {
      report(`${path}.blanks[${bIdx}]`, "blank must be an object", "error", exerciseId)
      return
    }
    validateBlank(blank, `${path}.blanks[${bIdx}]`, inTemplateMatches, true, report, exerciseId)
  })

  const uniqueInTemplate = Array.from(new Set(inTemplateMatches))
  uniqueInTemplate
    .filter((ph) => !declaredPlaceholders.includes(ph))
    .forEach((ph) => {
      report(`${path}.template`, `template contains '${ph}' with no matching blank`, "error", exerciseId)
    })

  if (puzzle.explanation !== undefined && puzzle.explanation !== null) {
    requireText(puzzle.explanation, `${path}.explanation`, "explanation", report, exerciseId)
  }

  // Solution reconstruction check
  if (referenceSolution && referenceSolution.trim().length > 0) {
    let allBlanksHaveAcceptedOption = true
    const substitutions: Array<{ placeholder: string; replacement: string }> = []

    for (const b of blanks) {
      if (!isRecord(b)) continue
      const placeholder = typeof b.placeholder === "string" ? b.placeholder : ""
      const accepted = Array.isArray(b.accepted) ? (b.accepted as string[]) : []
      const options = Array.isArray(b.options) ? (b.options as string[]) : []
      const caseSensitive = b.caseSensitive !== false && b.case_sensitive !== false

      const matchingOption = options.find((opt) =>
        accepted.some((acc) =>
          caseSensitive
            ? String(opt).trim() === String(acc).trim()
            : String(opt).trim().toLowerCase() === String(acc).trim().toLowerCase()
        )
      )

      if (matchingOption !== undefined) {
        substitutions.push({ placeholder, replacement: String(matchingOption) })
      } else {
        allBlanksHaveAcceptedOption = false
      }
    }

    if (allBlanksHaveAcceptedOption && substitutions.length === blanks.length) {
      let reconstructed = template
      for (const { placeholder, replacement } of substitutions) {
        reconstructed = reconstructed.split(placeholder).join(replacement)
      }
      const normReconstructed = reconstructed.replace(/\r\n/g, "\n")
      const normRef = referenceSolution.replace(/\r\n/g, "\n")
      if (normReconstructed !== normRef) {
        report(
          `${path}.template`,
          "filling the accepted choices does not reconstruct reference_solution exactly",
          "error",
          exerciseId
        )
      }
    }
  }
}

function validatePredictOutput(
  ex: Record<string, unknown>,
  path: string,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  const code = typeof ex.code === "string" ? ex.code : ""
  requireText(code, `${path}.code`, "code", report, exerciseId)

  if (code.trim().length > 0 && !MAIN_FUNCTION.test(code)) {
    report(`${path}.code`, "must be a complete program with fun main()", "error", exerciseId)
  }

  const expectedOutput =
    typeof ex.expectedOutput === "string"
      ? ex.expectedOutput
      : typeof ex.expected_output === "string"
      ? ex.expected_output
      : ""
  if (expectedOutput.length === 0) {
    report(`${path}.expected_output`, "expected_output cannot be empty", "error", exerciseId)
  }

  requireText(ex.explanation, `${path}.explanation`, "explanation", report, exerciseId)
}

function validateWriteFunction(
  ex: Record<string, unknown>,
  path: string,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  const starterCode = ex.starterCode ?? ex.starter_code
  requireText(starterCode, `${path}.starter_code`, "starter_code", report, exerciseId)

  const hiddenTests = ex.hiddenTests ?? ex.hidden_tests
  requireHiddenTests(hiddenTests, `${path}.hidden_tests`, report, exerciseId)

  const refSolution = typeof (ex.referenceSolution ?? ex.reference_solution) === "string"
    ? String(ex.referenceSolution ?? ex.reference_solution)
    : ""
  requireText(refSolution, `${path}.reference_solution`, "reference_solution", report, exerciseId)

  if (Array.isArray(ex.hints)) {
    requireHints(ex.hints, `${path}.hints`, report, exerciseId)
  }

  if (ex.explanation !== undefined && ex.explanation !== null) {
    requireText(ex.explanation, `${path}.explanation`, "explanation", report, exerciseId)
  }

  if (!ex.guided) {
    report(`${path}.guided`, "must provide guided code puzzle data", "error", exerciseId)
  } else {
    validateCodePuzzle(ex.guided, `${path}.guided`, refSolution, report, exerciseId)
  }
}

function validateMultiPartProblem(
  ex: Record<string, unknown>,
  path: string,
  ids: UniqueIds,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  requireText(ex.story, `${path}.story`, "story", report, exerciseId)

  const parts = Array.isArray(ex.parts) ? ex.parts : []
  if (parts.length === 0) {
    report(`${path}.parts`, "must have at least one part", "error", exerciseId)
  } else {
    parts.forEach((part, pIdx) => {
      validatePart(part, `${path}.parts[${pIdx}]`, ids, report, exerciseId)
    })
  }

  if (Array.isArray(ex.faq)) {
    ex.faq.forEach((faqItem, fIdx) => {
      if (isRecord(faqItem)) {
        requireText(faqItem.question, `${path}.faq[${fIdx}].question`, "question", report, exerciseId)
        requireText(faqItem.answer, `${path}.faq[${fIdx}].answer`, "answer", report, exerciseId)
      }
    })
  }

  if (ex.discussion !== undefined && ex.discussion !== null) {
    requireText(ex.discussion, `${path}.discussion`, "discussion", report, exerciseId)
  }

  const sourceNote = ex.sourceNote ?? ex.source_note
  if (sourceNote !== undefined && sourceNote !== null) {
    requireText(sourceNote, `${path}.source_note`, "source_note", report, exerciseId)
  }
}

function validatePart(
  part: unknown,
  path: string,
  ids: UniqueIds,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  if (!isRecord(part)) {
    report(path, "part must be an object", "error", exerciseId)
    return
  }

  if (typeof part.id === "string") {
    ids.register(`${path}.id`, part.id, exerciseId)
  }

  requireText(part.prompt, `${path}.prompt`, "prompt", report, exerciseId)

  const starterCode = part.starterCode ?? part.starter_code
  if (starterCode !== undefined && starterCode !== null) {
    requireText(starterCode, `${path}.starter_code`, "starter_code", report, exerciseId)
  }

  const hiddenTests = part.hiddenTests ?? part.hidden_tests
  requireHiddenTests(hiddenTests, `${path}.hidden_tests`, report, exerciseId)

  const refSolution = typeof (part.referenceSolution ?? part.reference_solution) === "string"
    ? String(part.referenceSolution ?? part.reference_solution)
    : ""
  requireText(refSolution, `${path}.reference_solution`, "reference_solution", report, exerciseId)

  requireText(part.expected, `${path}.expected`, "expected", report, exerciseId)

  if (Array.isArray(part.hints)) {
    requireHints(part.hints, `${path}.hints`, report, exerciseId)
  }

  if (!part.guided) {
    report(`${path}.guided`, "must provide guided code puzzle data", "error", exerciseId)
  } else {
    validateCodePuzzle(part.guided, `${path}.guided`, refSolution, report, exerciseId)
  }
}

function requireHiddenTests(
  value: unknown,
  path: string,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  const tests = typeof value === "string" ? value : ""
  requireText(tests, path, "hidden_tests", report, exerciseId)
  if (tests.includes("kotlin.test")) {
    report(path, "must use JUnit 4 (org.junit), not kotlin.test", "error", exerciseId)
  }
}

function requireHints(
  hints: unknown[],
  path: string,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  if (hints.length > MAX_HINTS) {
    report(
      path,
      `has ${hints.length} hints; use at most ${MAX_HINTS}, in increasing order of help`,
      "error",
      exerciseId
    )
  }
  hints.forEach((hint, idx) => {
    requireText(hint, `${path}[${idx}]`, "hint", report, exerciseId)
  })
}

function requireText(
  value: unknown,
  path: string,
  label: string,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void,
  exerciseId?: string
) {
  if (typeof value !== "string" || value.trim().length === 0) {
    report(path, `${label} cannot be blank`, "error", exerciseId)
  }
}

function checkAnswerSpread(
  chapter: Record<string, unknown>,
  path: string,
  report: (path: string, msg: string, severity?: Severity, exerciseId?: string) => void
) {
  const answers: number[] = []
  if (Array.isArray(chapter.lessons)) {
    for (const lesson of chapter.lessons) {
      if (isRecord(lesson) && Array.isArray(lesson.exercises)) {
        for (const ex of lesson.exercises) {
          if (isRecord(ex) && ex.type === "multiple_choice") {
            const idx =
              typeof ex.correctIndex === "number"
                ? ex.correctIndex
                : typeof ex.correct_index === "number"
                ? ex.correct_index
                : isRecord(ex.grading) && typeof ex.grading.correctIndex === "number"
                ? ex.grading.correctIndex
                : undefined
            if (idx !== undefined) answers.push(idx)
          }
        }
      }
    }
  }

  if (answers.length === 0) return

  const counts = new Map<number, number>()
  for (const ans of answers) {
    counts.set(ans, (counts.get(ans) ?? 0) + 1)
  }

  let maxCount = 0
  let maxIndex = -1
  for (const [idx, count] of counts.entries()) {
    if (count > maxCount) {
      maxCount = count
      maxIndex = idx
    }
  }

  const share = maxCount / answers.length
  if (share > MAX_SHARE_OF_ONE_INDEX) {
    const percent = Math.round(share * 100)
    const limit = Math.round(MAX_SHARE_OF_ONE_INDEX * 100)
    report(
      `${path}.lessons`,
      `${maxCount} of ${answers.length} multiple-choice answers (${percent}%) have correct_index ${maxIndex}; keep any one index at or below ${limit}%`,
      "warning"
    )
  }
}
