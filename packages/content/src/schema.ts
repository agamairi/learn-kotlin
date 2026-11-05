import type { ValidationIssue, ValidationResult } from "./types.ts"

export const COURSE_PACK_JSON_SCHEMA = {
  $schema: "https://json-schema.org/draft/2020-12/schema",
  $id: "https://learn-code.dev/schemas/course-pack-v1.json",
  title: "CoursePack",
  description: "Schema definition for Learn Code versioned JSON course packs.",
  type: "object",
  required: [
    "schemaVersion",
    "packId",
    "version",
    "title",
    "description",
    "language",
    "publisher",
    "license",
    "source",
    "contentHash",
    "updatedAt",
    "courses"
  ],
  properties: {
    schemaVersion: { type: "string" },
    packId: { type: "string", pattern: "^[a-z0-9]+(-[a-z0-9]+)*$" },
    version: { type: "string" },
    title: { type: "string" },
    description: { type: "string" },
    language: { type: "string" },
    level: { type: "string", enum: ["beginner", "intermediate", "advanced", "all_levels"] },
    tags: { type: "array", items: { type: "string" } },
    publisher: {
      type: "object",
      required: ["name"],
      properties: {
        name: { type: "string" },
        homepage: { type: "string" },
        url: { type: "string" },
        email: { type: "string" }
      }
    },
    license: { type: "string" },
    source: {
      type: "object",
      required: ["url"],
      properties: {
        url: { type: "string" },
        ref: { type: "string" },
        commit: { type: "string" },
        path: { type: "string" }
      }
    },
    contentHash: { type: "string" },
    updatedAt: { type: "string" },
    minimumClientVersion: { type: "string" },
    capabilities: { type: "array", items: { type: "string" } },
    courses: {
      type: "array",
      minItems: 1
    }
  }
} as const

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0
}

/**
 * Validates the raw JSON structural shape against the course pack schema contract.
 */
export function validateCoursePackStructure(data: unknown): ValidationResult {
  const issues: ValidationIssue[] = []

  const report = (path: string, message: string, exerciseId?: string) => {
    issues.push({ severity: "error", path, message, exerciseId })
  }

  if (!isRecord(data)) {
    return {
      valid: false,
      issues: [{ severity: "error", path: "$", message: "Course pack must be an object" }],
      errors: [{ severity: "error", path: "$", message: "Course pack must be an object" }],
      warnings: []
    }
  }

  // Check top-level required fields
  const requiredTopLevel = [
    "schemaVersion",
    "packId",
    "version",
    "title",
    "description",
    "language",
    "publisher",
    "license",
    "source",
    "contentHash",
    "updatedAt",
    "courses"
  ]

  for (const field of requiredTopLevel) {
    if (data[field] === undefined || data[field] === null) {
      report(`$.${field}`, `Missing required field '${field}'`)
    }
  }

  if (data.schemaVersion !== undefined && typeof data.schemaVersion !== "string") {
    report("$.schemaVersion", "schemaVersion must be a string")
  }

  if (data.packId !== undefined && typeof data.packId !== "string") {
    report("$.packId", "packId must be a string")
  }

  if (data.version !== undefined && typeof data.version !== "string") {
    report("$.version", "version must be a string")
  }

  if (data.title !== undefined && typeof data.title !== "string") {
    report("$.title", "title must be a string")
  }

  if (data.description !== undefined && typeof data.description !== "string") {
    report("$.description", "description must be a string")
  }

  if (data.language !== undefined && typeof data.language !== "string") {
    report("$.language", "language must be a string")
  }

  if (data.license !== undefined && typeof data.license !== "string") {
    report("$.license", "license must be a string")
  }

  if (data.contentHash !== undefined && typeof data.contentHash !== "string") {
    report("$.contentHash", "contentHash must be a string")
  }

  if (data.updatedAt !== undefined && typeof data.updatedAt !== "string") {
    report("$.updatedAt", "updatedAt must be a string")
  }

  // Publisher
  if (data.publisher !== undefined) {
    if (!isRecord(data.publisher)) {
      report("$.publisher", "publisher must be an object")
    } else if (typeof data.publisher.name !== "string" || data.publisher.name.trim().length === 0) {
      report("$.publisher.name", "publisher.name must be a non-empty string")
    }
  }

  // Source
  if (data.source !== undefined) {
    if (!isRecord(data.source)) {
      report("$.source", "source must be an object")
    } else if (typeof data.source.url !== "string" || data.source.url.trim().length === 0) {
      report("$.source.url", "source.url must be a non-empty string")
    }
  }

  // Courses
  if (data.courses !== undefined) {
    if (!Array.isArray(data.courses)) {
      report("$.courses", "courses must be an array")
    } else if (data.courses.length === 0) {
      report("$.courses", "courses array must not be empty")
    } else {
      data.courses.forEach((course, cIdx) => {
        validateCourseStructure(course, `$.courses[${cIdx}]`, report)
      })
    }
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

function validateCourseStructure(
  course: unknown,
  path: string,
  report: (path: string, msg: string, exerciseId?: string) => void
) {
  if (!isRecord(course)) {
    report(path, "course must be an object")
    return
  }

  if (typeof course.id !== "string") report(`${path}.id`, "course id must be a string")
  if (typeof course.title !== "string") report(`${path}.title`, "course title must be a string")
  if (typeof course.description !== "string") report(`${path}.description`, "course description must be a string")

  if (!Array.isArray(course.chapters)) {
    report(`${path}.chapters`, "chapters must be an array")
  } else if (course.chapters.length === 0) {
    report(`${path}.chapters`, "chapters must contain at least one chapter")
  } else {
    course.chapters.forEach((chapter, chIdx) => {
      validateChapterStructure(chapter, `${path}.chapters[${chIdx}]`, report)
    })
  }
}

function validateChapterStructure(
  chapter: unknown,
  path: string,
  report: (path: string, msg: string, exerciseId?: string) => void
) {
  if (!isRecord(chapter)) {
    report(path, "chapter must be an object")
    return
  }

  if (typeof chapter.id !== "string") report(`${path}.id`, "chapter id must be a string")
  if (typeof chapter.title !== "string") report(`${path}.title`, "chapter title must be a string")
  if (typeof chapter.summary !== "string") report(`${path}.summary`, "chapter summary must be a string")

  if (!Array.isArray(chapter.lessons)) {
    report(`${path}.lessons`, "lessons must be an array")
  } else if (chapter.lessons.length === 0) {
    report(`${path}.lessons`, "lessons must contain at least one lesson")
  } else {
    chapter.lessons.forEach((lesson, lIdx) => {
      validateLessonStructure(lesson, `${path}.lessons[${lIdx}]`, report)
    })
  }
}

function validateLessonStructure(
  lesson: unknown,
  path: string,
  report: (path: string, msg: string, exerciseId?: string) => void
) {
  if (!isRecord(lesson)) {
    report(path, "lesson must be an object")
    return
  }

  if (typeof lesson.id !== "string") report(`${path}.id`, "lesson id must be a string")
  if (typeof lesson.title !== "string") report(`${path}.title`, "lesson title must be a string")
  if (typeof lesson.explanation !== "string") report(`${path}.explanation`, "lesson explanation must be a string")

  if (!Array.isArray(lesson.exercises)) {
    report(`${path}.exercises`, "exercises must be an array")
  } else {
    lesson.exercises.forEach((exercise, exIdx) => {
      validateExerciseStructure(exercise, `${path}.exercises[${exIdx}]`, report)
    })
  }
}

const KNOWN_EXERCISE_TYPES = new Set([
  "multiple_choice",
  "cloze",
  "predict_output",
  "write_function",
  "multi_part_problem"
])

function validateExerciseStructure(
  exercise: unknown,
  path: string,
  report: (path: string, msg: string, exerciseId?: string) => void
) {
  if (!isRecord(exercise)) {
    report(path, "exercise must be an object")
    return
  }

  const exId = typeof exercise.id === "string" ? exercise.id : undefined

  if (typeof exercise.id !== "string") report(`${path}.id`, "exercise id must be a string", exId)
  if (typeof exercise.prompt !== "string") report(`${path}.prompt`, "exercise prompt must be a string", exId)
  if (typeof exercise.type !== "string") {
    report(`${path}.type`, "exercise type must be a string", exId)
    return
  }

  if (!KNOWN_EXERCISE_TYPES.has(exercise.type)) {
    report(
      `${path}.type`,
      `unknown exercise type '${exercise.type}'. Supported types: ${Array.from(KNOWN_EXERCISE_TYPES).join(", ")}`,
      exId
    )
    return
  }

  switch (exercise.type) {
    case "multiple_choice": {
      if (!Array.isArray(exercise.options)) {
        report(`${path}.options`, "multiple_choice options must be an array", exId)
      }
      const hasCorrect =
        typeof exercise.correctIndex === "number" ||
        typeof exercise.correct_index === "number" ||
        (isRecord(exercise.grading) && typeof exercise.grading.correctIndex === "number")
      if (!hasCorrect) {
        report(`${path}.correctIndex`, "multiple_choice requires correctIndex or correct_index", exId)
      }
      if (typeof exercise.explanation !== "string") {
        report(`${path}.explanation`, "multiple_choice explanation must be a string", exId)
      }
      break
    }
    case "cloze": {
      if (typeof exercise.template !== "string") {
        report(`${path}.template`, "cloze template must be a string", exId)
      }
      if (!Array.isArray(exercise.blanks)) {
        report(`${path}.blanks`, "cloze blanks must be an array", exId)
      }
      if (typeof exercise.explanation !== "string") {
        report(`${path}.explanation`, "cloze explanation must be a string", exId)
      }
      break
    }
    case "predict_output": {
      if (typeof exercise.code !== "string") {
        report(`${path}.code`, "predict_output code must be a string", exId)
      }
      const hasExpected =
        typeof exercise.expectedOutput === "string" || typeof exercise.expected_output === "string"
      if (!hasExpected) {
        report(`${path}.expectedOutput`, "predict_output requires expectedOutput or expected_output", exId)
      }
      if (typeof exercise.explanation !== "string") {
        report(`${path}.explanation`, "predict_output explanation must be a string", exId)
      }
      break
    }
    case "write_function": {
      if (!isRecord(exercise.guided)) {
        report(`${path}.guided`, "write_function requires guided puzzle object", exId)
      }
      break
    }
    case "multi_part_problem": {
      if (typeof exercise.story !== "string") {
        report(`${path}.story`, "multi_part_problem story must be a string", exId)
      }
      if (!Array.isArray(exercise.parts)) {
        report(`${path}.parts`, "multi_part_problem parts must be an array", exId)
      } else if (exercise.parts.length === 0) {
        report(`${path}.parts`, "multi_part_problem must contain at least one part", exId)
      }
      break
    }
  }
}
