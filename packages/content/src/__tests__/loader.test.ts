import assert from "node:assert/strict"
import test, { describe, it } from "node:test"
import {
  computeContentHash,
  computePackPayloadHash,
  loadCoursePack,
  normalizeCoursePack
} from "../loader.ts"
import { createValidCoursePack } from "./fixtures.ts"

describe("CoursePack Loader", () => {
  it("loads a valid pack from an object", () => {
    const raw = createValidCoursePack()
    const result = loadCoursePack(raw)
    assert.equal(result.ok, true, `Load failed: ${JSON.stringify(result.errors)}`)
    assert.ok(result.pack)
    assert.equal(result.pack.packId, "kotlin-sample-pack")
    assert.equal(result.pack.version, "1.0.0")
    assert.equal(result.errors.length, 0)
  })

  it("loads a valid pack from a JSON string", () => {
    const raw = createValidCoursePack()
    const jsonStr = JSON.stringify(raw)
    const result = loadCoursePack(jsonStr)
    assert.equal(result.ok, true)
    assert.ok(result.pack)
    assert.equal(result.pack.title, "Sample Kotlin Pack")
  })

  it("normalizes snake_case fields into camelCase properties", () => {
    const snakeCasePack = {
      schemaVersion: "1.0",
      packId: "snake-pack",
      version: "1.0.0",
      title: "Snake Pack",
      description: "Pack with snake_case fields.",
      language: "kotlin",
      publisher: { name: "Author" },
      license: "MIT",
      source: { url: "https://github.com/learn-code/snake" },
      contentHash: "sha256:abcd",
      updatedAt: "2026-11-04T00:00:00Z",
      courses: [
        {
          id: "c-snake",
          title: "Course",
          description: "Desc",
          chapters: [
            {
              id: "ch-snake",
              title: "Chapter",
              summary: "Summary",
              lessons: [
                {
                  id: "l-snake",
                  title: "Lesson",
                  explanation: "Exp",
                  exercises: [
                    {
                      type: "multiple_choice",
                      id: "ex-mc",
                      prompt: "Prompt",
                      options: ["A", "B"],
                      correct_index: 1,
                      explanation: "Exp"
                    },
                    {
                      type: "predict_output",
                      id: "ex-po",
                      prompt: "Prompt",
                      code: "fun main() { println(1) }",
                      expected_output: "1",
                      explanation: "Exp"
                    }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }

    const result = loadCoursePack(snakeCasePack)
    assert.equal(result.ok, true, `Normalization failed: ${JSON.stringify(result.errors)}`)
    assert.ok(result.pack)
    const lesson = result.pack.courses[0].chapters[0].lessons[0]
    const mc = lesson.exercises[0] as any
    assert.equal(mc.correctIndex, 1)
    assert.equal(mc.grading?.correctIndex, 1)

    const po = lesson.exercises[1] as any
    assert.equal(po.expectedOutput, "1")
  })

  it("handles malformed JSON syntax gracefully", () => {
    const badJson = '{"schemaVersion": "1.0", unquotedKey: 123}'
    const result = loadCoursePack(badJson)
    assert.equal(result.ok, false)
    assert.equal(result.errors.length, 1)
    assert.ok(result.errors[0].message.includes("JSON syntax error"))
  })

  it("rejects structurally incomplete course packs", () => {
    const incomplete = {
      schemaVersion: "1.0",
      packId: "incomplete-pack"
      // Missing title, description, language, publisher, source, courses, etc.
    }
    const result = loadCoursePack(incomplete)
    assert.equal(result.ok, false)
    assert.ok(result.errors.length > 0)
    assert.ok(result.errors.some((e) => e.message.includes("Missing required field 'courses'")))
  })

  it("computes deterministic content hashes regardless of key order", () => {
    const objA = { b: 2, a: 1, c: { y: "bar", x: "foo" } }
    const objB = { a: 1, c: { x: "foo", y: "bar" }, b: 2 }

    const hashA = computeContentHash(objA)
    const hashB = computeContentHash(objB)

    assert.equal(hashA, hashB)
    assert.ok(hashA.startsWith("sha256:"))
  })

  it("verifies content hash when expectedHash matches", () => {
    const pack = createValidCoursePack()
    const payloadHash = computePackPayloadHash(pack)
    pack.contentHash = payloadHash

    const result = loadCoursePack(pack, { verifyContentHash: true, expectedHash: payloadHash })
    assert.equal(result.ok, true)
    assert.equal(result.contentHash, payloadHash)
  })

  it("fails loading when expectedHash mismatches declared hash", () => {
    const pack = createValidCoursePack()
    pack.contentHash = "sha256:declared-hash"

    const result = loadCoursePack(pack, { expectedHash: "sha256:different-expected-hash" })
    assert.equal(result.ok, false)
    assert.ok(result.errors.some((e) => e.message.includes("Content hash mismatch")))
  })

  it("rejects a tampered pack when verification is enabled", () => {
    const pack = createValidCoursePack()
    pack.contentHash = computePackPayloadHash(pack)
    pack.description = "Tampered after publishing"

    const result = loadCoursePack(pack, { verifyContentHash: true })
    assert.equal(result.ok, false)
    assert.ok(result.errors.some((e) => e.message.includes("Content hash mismatch")))
  })

  it("enforces minimum client version during load", () => {
    const pack = createValidCoursePack()
    pack.minimumClientVersion = "2.0.0"

    const result = loadCoursePack(pack, { clientVersion: "1.5.0" })
    assert.equal(result.ok, false)
    assert.ok(
      result.errors.some((e) =>
        e.message.includes("pack requires client version at least 2.0.0, but current client version is 1.5.0")
      )
    )
  })
})
