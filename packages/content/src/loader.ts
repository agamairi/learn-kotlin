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
 * A small synchronous SHA-256 implementation used by the shared content
 * loader. The loader is imported by the React Native bundle, so it cannot
 * depend on Node's `crypto` module. Keeping this implementation local also
 * preserves the existing synchronous loader contract used by the CLI and
 * tests.
 */
function sha256Hex(value: string): string {
  const bytes = utf8Bytes(value)
  const bitLength = bytes.length * 8
  const paddedLength = (((bytes.length + 9) + 63) >> 6) << 6
  const message = new Uint8Array(paddedLength)
  message.set(bytes)
  message[bytes.length] = 0x80
  for (let index = 0; index < 8; index += 1) {
    message[paddedLength - 1 - index] = Math.floor(bitLength / 2 ** (index * 8)) & 0xff
  }

  const roundConstants = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1,
    0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
    0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
    0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
    0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
    0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ]
  let h0 = 0x6a09e667
  let h1 = 0xbb67ae85
  let h2 = 0x3c6ef372
  let h3 = 0xa54ff53a
  let h4 = 0x510e527f
  let h5 = 0x9b05688c
  let h6 = 0x1f83d9ab
  let h7 = 0x5be0cd19
  const word = new Uint32Array(64)
  const rotateRight = (value: number, amount: number) => (value >>> amount) | (value << (32 - amount))

  for (let offset = 0; offset < paddedLength; offset += 64) {
    for (let index = 0; index < 16; index += 1) {
      const position = offset + index * 4
      word[index] =
        (message[position] << 24) |
        (message[position + 1] << 16) |
        (message[position + 2] << 8) |
        message[position + 3]
    }
    for (let index = 16; index < 64; index += 1) {
      const value = word[index - 15]
      const smallSigma0 = rotateRight(value, 7) ^ rotateRight(value, 18) ^ (value >>> 3)
      const previous = word[index - 2]
      const smallSigma1 = rotateRight(previous, 17) ^ rotateRight(previous, 19) ^ (previous >>> 10)
      word[index] = (word[index - 16] + smallSigma0 + word[index - 7] + smallSigma1) >>> 0
    }

    let a = h0
    let b = h1
    let c = h2
    let d = h3
    let e = h4
    let f = h5
    let g = h6
    let h = h7
    for (let index = 0; index < 64; index += 1) {
      const bigSigma1 = rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25)
      const choose = (e & f) ^ (~e & g)
      const first = (h + bigSigma1 + choose + roundConstants[index] + word[index]) >>> 0
      const bigSigma0 = rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22)
      const majority = (a & b) ^ (a & c) ^ (b & c)
      const second = (bigSigma0 + majority) >>> 0
      h = g
      g = f
      f = e
      e = (d + first) >>> 0
      d = c
      c = b
      b = a
      a = (first + second) >>> 0
    }
    h0 = (h0 + a) >>> 0
    h1 = (h1 + b) >>> 0
    h2 = (h2 + c) >>> 0
    h3 = (h3 + d) >>> 0
    h4 = (h4 + e) >>> 0
    h5 = (h5 + f) >>> 0
    h6 = (h6 + g) >>> 0
    h7 = (h7 + h) >>> 0
  }

  return [h0, h1, h2, h3, h4, h5, h6, h7]
    .map((value) => value.toString(16).padStart(8, "0"))
    .join("")
}

function utf8Bytes(value: string): Uint8Array {
  const bytes: number[] = []
  for (let index = 0; index < value.length; index += 1) {
    let codePoint = value.charCodeAt(index)
    if (codePoint >= 0xd800 && codePoint <= 0xdbff && index + 1 < value.length) {
      const next = value.charCodeAt(index + 1)
      if (next >= 0xdc00 && next <= 0xdfff) {
        codePoint = 0x10000 + ((codePoint - 0xd800) << 10) + (next - 0xdc00)
        index += 1
      }
    }
    if (codePoint <= 0x7f) {
      bytes.push(codePoint)
    } else if (codePoint <= 0x7ff) {
      bytes.push(0xc0 | (codePoint >> 6), 0x80 | (codePoint & 0x3f))
    } else if (codePoint <= 0xffff) {
      bytes.push(0xe0 | (codePoint >> 12), 0x80 | ((codePoint >> 6) & 0x3f), 0x80 | (codePoint & 0x3f))
    } else {
      bytes.push(
        0xf0 | (codePoint >> 18),
        0x80 | ((codePoint >> 12) & 0x3f),
        0x80 | ((codePoint >> 6) & 0x3f),
        0x80 | (codePoint & 0x3f)
      )
    }
  }
  return new Uint8Array(bytes)
}

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
  return `sha256:${sha256Hex(content)}`
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
