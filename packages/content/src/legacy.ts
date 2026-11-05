import { computePackPayloadHash, loadCoursePack } from "./loader.ts"
import type { CoursePack, CoursePackPublisher, CoursePackSource, LoadCoursePackResult } from "./types.ts"

export type LegacyCourseMetadata = {
  packId?: string
  version?: string
  publisher?: CoursePackPublisher
  license?: string
  source?: CoursePackSource
  updatedAt?: string
}

/**
 * Wraps the existing bundled Kotlin Course JSON in the versioned pack envelope.
 * This keeps the current Android-generated asset usable while the authoring
 * pipeline transitions from bare Course objects to CoursePack documents.
 */
export function wrapLegacyCourse(
  input: Record<string, unknown>,
  metadata: LegacyCourseMetadata = {}
): CoursePack {
  const courseId = String(input.id ?? "legacy-course")
  const pack: CoursePack = {
    schemaVersion: "1.0",
    packId: metadata.packId ?? `${courseId}-pack`,
    version: metadata.version ?? "0.0.0-legacy",
    title: String(input.title ?? courseId),
    description: String(input.description ?? "Imported legacy course"),
    language: String(input.language ?? "unknown"),
    publisher: metadata.publisher ?? { name: "Learn Code" },
    license: metadata.license ?? "UNSPECIFIED",
    source: metadata.source ?? {
      url: "https://github.com/agamairi/learn-kotlin",
      ref: "legacy-bundled-course",
      path: "app-android/src/main/assets/courses/kotlin-stripe.json"
    },
    contentHash: "",
    updatedAt: metadata.updatedAt ?? new Date(0).toISOString(),
    courses: [input as unknown as CoursePack["courses"][number]]
  }

  pack.contentHash = computePackPayloadHash(pack as unknown as Record<string, unknown>)
  return pack
}

export function loadLegacyCourseJson(
  input: string | Record<string, unknown>,
  metadata: LegacyCourseMetadata = {}
): LoadCoursePackResult {
  const parsed = typeof input === "string" ? JSON.parse(input) as Record<string, unknown> : input
  const pack = wrapLegacyCourse(parsed, metadata)
  return loadCoursePack(pack, {
    verifyContentHash: true,
    expectedHash: pack.contentHash,
    validateSemantics: true
  })
}
