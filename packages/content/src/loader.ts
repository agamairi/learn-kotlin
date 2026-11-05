import crypto from "node:crypto"
import { validateCoursePackStructure } from "./schema.ts"
import type {
  CourseDefinition,
  CoursePack,
  Exercise,
  LoadCoursePackOptions,
  LoadCoursePackResult,
  ValidationIssue
} from "./types.ts"
import { validateCoursePack } from "./validator.ts"

/**
 * Deterministically sorts object keys for canonical JSON serialization.
 */
function canonicalize(value: unknown): unknown {
  if (value === null || typeof value !== "object") {
    return value
  }
  if (Array.isArray(value)) {
    return value.map(canonicalize)
  }
  const obj = value as Record<string, unknown>
  const sortedKeys = Object.keys(obj).sort()
  const result: Record<string, unknown> = {}
  for (const key of sortedKeys) {
    result[key] = canonicalize(obj[key])
  }
  return result
}

/**
 * Computes a SHA-256 hash formatted as 'sha256:<hex>' for a UTF-8 string or payload object.
 */
export function computeContentHash(payload: string | object): string {
  const content =
    typeof payload === "string"
      ? payload
      : JSON.stringify(canonicalize(payload))
  const hash = crypto.createHash("sha256").update(content, "utf8").digest("hex")
  return `sha256:${hash}`
}

/**
 * Computes the normalized content hash of a course pack object, omitting the self-referential
 * 'contentHash' field so the payload can be verified after receipt.
 */
export function computePackPayloadHash(pack: Record<string, unknown>): string {
  const { contentHash: _, ...rest } = pack
  return computeContentHash(rest)
}

/**
 * Normalizes snake_case exercise and pack fields to canonical camelCase types.
 */
export function normalizeCoursePack(raw: Record<string, unknown>): CoursePack {
  const courses = Array.isArray(raw.courses)
    ? (raw.courses as Record<string, unknown>[]).map(normalizeCourse)
    : []

  return {
    schemaVersion: String(raw.schemaVersion ?? "1.0"),
    packId: String(raw.packId ?? ""),
    version: String(raw.version ?? ""),
    title: String(raw.title ?? ""),
    description: String(raw.description ?? ""),
    language: String(raw.language ?? ""),
    level: raw.level !== undefined ? String(raw.level) : undefined,
    publisher: (raw.publisher as any) ?? { name: "" },
    license: String(raw.license ?? ""),
    source: (raw.source as any) ?? { url: "" },
    contentHash: String(raw.contentHash ?? ""),
    updatedAt: String(raw.updatedAt ?? ""),
    minimumClientVersion:
      raw.minimumClientVersion !== undefined ? String(raw.minimumClientVersion) : undefined,
    capabilities: Array.isArray(raw.capabilities) ? (raw.capabilities as string[]) : undefined,
    tags: Array.isArray(raw.tags) ? (raw.tags as string[]) : undefined,
    courses,
    ...(raw.metadata ? { metadata: raw.metadata as Record<string, unknown> } : {})
  }
}

function normalizeCourse(raw: Record<string, unknown>): CourseDefinition {
  const chapters = Array.isArray(raw.chapters)
    ? (raw.chapters as Record<string, unknown>[]).map((ch) => ({
        id: String(ch.id ?? ""),
        title: String(ch.title ?? ""),
        summary: String(ch.summary ?? ""),
        lessons: Array.isArray(ch.lessons)
          ? (ch.lessons as Record<string, unknown>[]).map((l) => ({
              id: String(l.id ?? ""),
              title: String(l.title ?? ""),
              explanation: String(l.explanation ?? ""),
              exercises: Array.isArray(l.exercises)
                ? (l.exercises as Record<string, unknown>[]).map(normalizeExercise)
                : []
            }))
          : []
      }))
    : []

  return {
    id: String(raw.id ?? ""),
    title: String(raw.title ?? ""),
    description: String(raw.description ?? ""),
    language: raw.language !== undefined ? String(raw.language) : undefined,
    chapters
  }
}

function normalizeExercise(raw: Record<string, unknown>): Exercise {
  const base = {
    id: String(raw.id ?? ""),
    prompt: String(raw.prompt ?? ""),
    topicTags: Array.isArray(raw.topicTags) ? (raw.topicTags as string[]) : undefined,
    difficulty: raw.difficulty !== undefined ? String(raw.difficulty) : undefined,
    estimatedSeconds: typeof raw.estimatedSeconds === "number" ? raw.estimatedSeconds : undefined,
    explanation: raw.explanation !== undefined ? String(raw.explanation) : undefined,
    aiGenerated: Boolean(raw.aiGenerated || raw.ai_generated),
    sourceNote:
      raw.sourceNote !== undefined
        ? String(raw.sourceNote)
        : raw.source_note !== undefined
        ? String(raw.source_note)
        : undefined
  }

  switch (raw.type) {
    case "multiple_choice": {
      const correctIndex =
        typeof raw.correctIndex === "number"
          ? raw.correctIndex
          : typeof raw.correct_index === "number"
          ? raw.correct_index
          : typeof (raw.grading as any)?.correctIndex === "number"
          ? (raw.grading as any).correctIndex
          : 0
      return {
        ...base,
        type: "multiple_choice",
        code: raw.code !== undefined ? String(raw.code) : undefined,
        options: Array.isArray(raw.options) ? (raw.options as string[]) : [],
        correctIndex,
        grading: {
          kind: (raw.grading as any)?.kind ?? "choice",
          correctIndex
        },
        explanation: String(raw.explanation ?? "")
      }
    }
    case "cloze": {
      const blanks = Array.isArray(raw.blanks)
        ? (raw.blanks as Record<string, unknown>[]).map((b) => ({
            placeholder: String(b.placeholder ?? ""),
            accepted: Array.isArray(b.accepted) ? (b.accepted as string[]) : [],
            caseSensitive: b.caseSensitive !== false && b.case_sensitive !== false,
            options: Array.isArray(b.options) ? (b.options as string[]) : undefined
          }))
        : []
      return {
        ...base,
        type: "cloze",
        template: String(raw.template ?? ""),
        blanks,
        explanation: String(raw.explanation ?? "")
      }
    }
    case "predict_output": {
      const expectedOutput =
        raw.expectedOutput !== undefined
          ? String(raw.expectedOutput)
          : raw.expected_output !== undefined
          ? String(raw.expected_output)
          : ""
      return {
        ...base,
        type: "predict_output",
        code: String(raw.code ?? ""),
        expectedOutput,
        explanation: String(raw.explanation ?? "")
      }
    }
    case "write_function": {
      return {
        ...base,
        type: "write_function",
        starterCode:
          raw.starterCode !== undefined
            ? String(raw.starterCode)
            : raw.starter_code !== undefined
            ? String(raw.starter_code)
            : undefined,
        hiddenTests:
          raw.hiddenTests !== undefined
            ? String(raw.hiddenTests)
            : raw.hidden_tests !== undefined
            ? String(raw.hidden_tests)
            : undefined,
        referenceSolution:
          raw.referenceSolution !== undefined
            ? String(raw.referenceSolution)
            : raw.reference_solution !== undefined
            ? String(raw.reference_solution)
            : undefined,
        hints: Array.isArray(raw.hints) ? (raw.hints as string[]) : undefined,
        explanation: raw.explanation !== undefined ? String(raw.explanation) : undefined,
        guided: normalizeGuided(raw.guided as Record<string, unknown>)
      }
    }
    case "multi_part_problem": {
      const parts = Array.isArray(raw.parts)
        ? (raw.parts as Record<string, unknown>[]).map((p) => ({
            id: String(p.id ?? ""),
            prompt: String(p.prompt ?? ""),
            starterCode:
              p.starterCode !== undefined
                ? String(p.starterCode)
                : p.starter_code !== undefined
                ? String(p.starter_code)
                : undefined,
            hiddenTests:
              p.hiddenTests !== undefined
                ? String(p.hiddenTests)
                : p.hidden_tests !== undefined
                ? String(p.hidden_tests)
                : undefined,
            referenceSolution:
              p.referenceSolution !== undefined
                ? String(p.referenceSolution)
                : p.reference_solution !== undefined
                ? String(p.reference_solution)
                : undefined,
            expected: String(p.expected ?? ""),
            hints: Array.isArray(p.hints) ? (p.hints as string[]) : undefined,
            guided: normalizeGuided(p.guided as Record<string, unknown>)
          }))
        : []
      const faq = Array.isArray(raw.faq)
        ? (raw.faq as Record<string, unknown>[]).map((item) => ({
            question: String(item.question ?? ""),
            answer: String(item.answer ?? "")
          }))
        : undefined
      return {
        ...base,
        type: "multi_part_problem",
        story: String(raw.story ?? ""),
        parts,
        faq,
        discussion: raw.discussion !== undefined ? String(raw.discussion) : undefined
      }
    }
    default:
      return base as any
  }
}

function normalizeGuided(raw: Record<string, unknown> | undefined) {
  if (!raw) return { template: "", blanks: [] }
  const blanks = Array.isArray(raw.blanks)
    ? (raw.blanks as Record<string, unknown>[]).map((b) => ({
        placeholder: String(b.placeholder ?? ""),
        accepted: Array.isArray(b.accepted) ? (b.accepted as string[]) : [],
        caseSensitive: b.caseSensitive !== false && b.case_sensitive !== false,
        options: Array.isArray(b.options) ? (b.options as string[]) : undefined
      }))
    : []
  return {
    template: String(raw.template ?? ""),
    blanks,
    explanation: raw.explanation !== undefined ? String(raw.explanation) : undefined
  }
}

/**
 * Loads, structurally verifies, and semantically validates a JSON course pack.
 */
export function loadCoursePack(
  input: string | unknown,
  options: LoadCoursePackOptions = {}
): LoadCoursePackResult {
  let parsed: unknown
  let rawJsonString: string | undefined

  if (typeof input === "string") {
    rawJsonString = input
    try {
      parsed = JSON.parse(input)
    } catch (err) {
      const syntaxError: ValidationIssue = {
        severity: "error",
        path: "$",
        message: `JSON syntax error: ${(err as Error).message}`
      }
      return {
        ok: false,
        errors: [syntaxError],
        warnings: []
      }
    }
  } else {
    parsed = input
  }

  // 1. Structural Schema Validation
  const structuralResult = validateCoursePackStructure(parsed)
  if (!structuralResult.valid) {
    return {
      ok: false,
      errors: structuralResult.errors,
      warnings: structuralResult.warnings
    }
  }

  // 2. Semantic Validation
  const semanticResult = validateCoursePack(parsed, { clientVersion: options.clientVersion })
  if (!semanticResult.valid) {
    return {
      ok: false,
      errors: semanticResult.errors,
      warnings: semanticResult.warnings
    }
  }

  const rawRecord = parsed as Record<string, unknown>

  // 3. Optional Content Hash Verification
  if (options.verifyContentHash || options.expectedHash) {
    const declaredHash = typeof rawRecord.contentHash === "string" ? rawRecord.contentHash : ""
    const payloadHash = computePackPayloadHash(rawRecord)
    const rawStringHash = rawJsonString ? computeContentHash(rawJsonString) : undefined
    const expectedHashMatches =
      !options.expectedHash ||
      options.expectedHash === payloadHash ||
      options.expectedHash === rawStringHash
    const declaredHashMatches = !options.verifyContentHash || declaredHash === payloadHash

    if (!expectedHashMatches || !declaredHashMatches) {
      const expected = options.expectedHash ?? payloadHash
      const actual = declaredHash || payloadHash
      const hashError: ValidationIssue = {
        severity: "error",
        path: "$.contentHash",
        message: `Content hash mismatch: declared '${actual}', expected '${expected}'`
      }
      return {
        ok: false,
        errors: [hashError],
        warnings: semanticResult.warnings
      }
    }
  }

  const normalized = normalizeCoursePack(rawRecord)
  const computedHash = computePackPayloadHash(rawRecord)

  return {
    ok: true,
    pack: normalized,
    contentHash: normalized.contentHash || computedHash,
    errors: [],
    warnings: semanticResult.warnings
  }
}
