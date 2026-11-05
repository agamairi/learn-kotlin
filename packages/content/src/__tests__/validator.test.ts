import assert from "node:assert/strict"
import test, { describe, it } from "node:test"
import { validateCoursePackStructure } from "../schema.ts"
import { validateCoursePack } from "../validator.ts"
import { createValidCoursePack } from "./fixtures.ts"

describe("CoursePack Validator", () => {
  it("passes validation for a well-formed course pack with all exercise types", () => {
    const pack = createValidCoursePack()
    const structural = validateCoursePackStructure(pack)
    assert.equal(structural.valid, true, `Structural validation failed: ${JSON.stringify(structural.errors)}`)

    const semantic = validateCoursePack(pack)
    assert.equal(semantic.valid, true, `Semantic validation failed: ${JSON.stringify(semantic.errors)}`)
    assert.equal(semantic.errors.length, 0)
  })

  describe("Duplicate IDs and ID Formats", () => {
    it("reports duplicate exercise IDs and names the first location", () => {
      const pack = createValidCoursePack()
      const lesson = pack.courses[0].chapters[0].lessons[0]
      // Add a duplicate exercise id
      lesson.exercises.push({
        ...lesson.exercises[0],
        id: "mc-val-var" // already used
      })

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const dupIssue = result.errors.find((e) => e.message.includes("is already used at"))
      assert.ok(dupIssue, "Should report duplicate ID issue")
      assert.ok(dupIssue.message.includes("mc-val-var"))
      assert.ok(dupIssue.message.includes("$.courses[0].chapters[0].lessons[0].exercises[0].id"))
      assert.equal(dupIssue.exerciseId, "mc-val-var")
    })

    it("reports duplicate problem part IDs across parts", () => {
      const pack = createValidCoursePack()
      const mpp = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "multi_part_problem"
      ) as any
      // Add duplicate part id
      mpp.parts.push({
        ...mpp.parts[0]
      })

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const dupIssue = result.errors.find((e) => e.message.includes("is already used at"))
      assert.ok(dupIssue, "Should report duplicate part ID")
      assert.ok(dupIssue.message.includes("part-counter-init"))
    })

    it("rejects non-kebab-case and blank IDs", () => {
      const pack = createValidCoursePack()
      pack.courses[0].chapters[0].lessons[0].exercises[0].id = "Invalid_CamelCase"

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const formatIssue = result.errors.find((e) => e.message.includes("must be lowercase kebab-case"))
      assert.ok(formatIssue, "Should flag non-kebab-case ID")
    })
  })

  describe("Cloze Blanks Validation", () => {
    it("rejects cloze with unnumbered blanks in template", () => {
      const pack = createValidCoursePack()
      const cloze = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "cloze"
      ) as any
      cloze.template = "val x = ____ + 1"

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("written as ____ without a number"))
      assert.ok(issue, "Should flag unnumbered blank")
    })

    it("rejects cloze with template placeholder missing in blanks list", () => {
      const pack = createValidCoursePack()
      const cloze = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "cloze"
      ) as any
      cloze.template = "val x = ____1 + ____99"

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("template contains '____99' with no matching blank"))
      assert.ok(issue, "Should flag missing blank definition")
    })

    it("rejects cloze when placeholder appears more than once in template", () => {
      const pack = createValidCoursePack()
      const cloze = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "cloze"
      ) as any
      cloze.template = "val x = ____1 + ____1"
      cloze.blanks = [cloze.blanks[0]]

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("appears 2 times in template"))
      assert.ok(issue, "Should report placeholder appearing multiple times")
    })

    it("rejects cloze when blank options count is not 4", () => {
      const pack = createValidCoursePack()
      const cloze = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "cloze"
      ) as any
      cloze.blanks[0].options = ["val", "var", "const"] // 3 options

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("must declare exactly 4 options"))
      assert.ok(issue, "Should flag non-4 options count")
    })

    it("rejects cloze when blank options have no accepted equivalent", () => {
      const pack = createValidCoursePack()
      const cloze = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "cloze"
      ) as any
      cloze.blanks[0].accepted = ["val"]
      cloze.blanks[0].options = ["let", "var", "const", "def"] // none is "val"

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("options must contain exactly one accepted equivalent, found 0"))
      assert.ok(issue, "Should flag missing accepted option")
    })

    it("rejects cloze when blank options have multiple accepted equivalents", () => {
      const pack = createValidCoursePack()
      const cloze = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "cloze"
      ) as any
      cloze.blanks[0].accepted = ["val", "var"]
      cloze.blanks[0].options = ["val", "var", "const", "def"] // 2 match

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("options must contain exactly one accepted equivalent, found 2"))
      assert.ok(issue, "Should flag multiple accepted options")
    })

    it("rejects cloze when blank options contain duplicates", () => {
      const pack = createValidCoursePack()
      const cloze = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "cloze"
      ) as any
      cloze.blanks[0].options = ["val", "var", "var", "def"]

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("options must be distinct"))
      assert.ok(issue, "Should flag duplicate options")
    })

    it("allows AI-generated cloze without options", () => {
      const pack = createValidCoursePack()
      const cloze = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "cloze"
      ) as any
      cloze.aiGenerated = true
      cloze.blanks[0].options = []
      cloze.blanks[1].options = []

      const result = validateCoursePack(pack)
      assert.equal(result.valid, true)
    })
  })

  describe("Guided Code Puzzle Validation and Reconstruction", () => {
    it("rejects guided code puzzle with fewer than 3 blanks", () => {
      const pack = createValidCoursePack()
      const wf = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "write_function"
      ) as any
      wf.guided.blanks = [wf.guided.blanks[0], wf.guided.blanks[1]] // only 2 blanks

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("must declare 3 to 5 blanks"))
      assert.ok(issue, "Should require 3 to 5 blanks")
    })

    it("rejects guided code puzzle when reconstructed code does not match reference solution", () => {
      const pack = createValidCoursePack()
      const wf = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "write_function"
      ) as any
      // Tamper with the template so reconstruction fails
      wf.guided.template = `fun add(a: Int, b: Int): Int {\n    ____1 a ____2 b * 2____3\n}`

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) =>
        e.message.includes("filling the accepted choices does not reconstruct reference_solution exactly")
      )
      assert.ok(issue, "Should detect reconstruction mismatch")
    })

    it("allows line ending differences when reconstructing reference solution", () => {
      const pack = createValidCoursePack()
      const wf = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "write_function"
      ) as any
      // Reference solution uses CRLF, template uses LF
      wf.referenceSolution = wf.referenceSolution.replace(/\n/g, "\r\n")

      const result = validateCoursePack(pack)
      assert.equal(result.valid, true, "CRLF normalization should pass reconstruction")
    })
  })

  describe("Multiple Choice and Answer Spread Warning", () => {
    it("reports warning when > 40% of MC questions in chapter share the same correct index", () => {
      const pack = createValidCoursePack()
      const lesson = pack.courses[0].chapters[0].lessons[0]
      // Add two more MC exercises all having correctIndex = 0 (making 4 out of 4 = 100% at index 0)
      lesson.exercises.push(
        {
          type: "multiple_choice",
          id: "mc-extra-1",
          prompt: "Extra question 1",
          options: ["A", "B", "C"],
          correctIndex: 0,
          explanation: "Explanation."
        },
        {
          type: "multiple_choice",
          id: "mc-extra-2",
          prompt: "Extra question 2",
          options: ["A", "B", "C"],
          correctIndex: 0,
          explanation: "Explanation."
        }
      )

      const result = validateCoursePack(pack)
      assert.equal(result.valid, true, "Should still be valid despite warning")
      assert.ok(result.warnings.length > 0, "Should emit answer spread warning")
      assert.ok(result.warnings[0].message.includes("multiple-choice answers"))
      assert.ok(result.warnings[0].message.includes("keep any one index at or below 40%"))
    })

    it("rejects correct_index out of options bounds", () => {
      const pack = createValidCoursePack()
      const mc = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "multiple_choice"
      ) as any
      mc.correctIndex = 10

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("is out of range"))
      assert.ok(issue)
    })
  })

  describe("Predict Output and Hidden Tests", () => {
    it("rejects predict_output missing fun main()", () => {
      const pack = createValidCoursePack()
      const po = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "predict_output"
      ) as any
      po.code = "val greeting = \"hello\""

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("must be a complete program with fun main()"))
      assert.ok(issue)
    })

    it("rejects hidden tests that use kotlin.test instead of JUnit 4", () => {
      const pack = createValidCoursePack()
      const wf = pack.courses[0].chapters[0].lessons[0].exercises.find(
        (e) => e.type === "write_function"
      ) as any
      wf.hiddenTests = "import kotlin.test.Test\nimport kotlin.test.assertEquals\nclass Test1"

      const result = validateCoursePack(pack)
      assert.equal(result.valid, false)
      const issue = result.errors.find((e) => e.message.includes("must use JUnit 4 (org.junit), not kotlin.test"))
      assert.ok(issue)
    })
  })
})
