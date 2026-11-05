import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { loadLegacyCourseJson } from "../legacy.ts"

test("the existing bundled Kotlin course can load through the versioned pack envelope", () => {
  const json = readFileSync(
    new URL("../../../../app-android/src/main/assets/courses/kotlin-stripe.json", import.meta.url),
    "utf8"
  )
  const result = loadLegacyCourseJson(json)

  assert.equal(result.ok, true)
  assert.equal(result.pack?.packId, "kotlin-stripe-pack")
  assert.equal(result.pack?.courses.length, 1)
  assert.equal(result.pack?.courses[0]?.chapters.length, 12)
  assert.equal(
    result.pack?.courses[0]?.chapters.reduce(
      (count, chapter) => count + chapter.lessons.reduce((sum, lesson) => sum + lesson.exercises.length, 0),
      0
    ),
    222
  )
})
