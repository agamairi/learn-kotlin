import type { CoursePack } from "../types.ts"

export function createValidCoursePack(): CoursePack {
  const writeFuncRef = `fun add(a: Int, b: Int): Int {\n    return a + b\n}`
  const writeFuncTemplate = `fun ____1(a: Int, b: Int): Int {\n    ____2 a ____3 b\n}`

  const partRef = `class Counter(var count: Int = 0) {\n    fun inc() { count++ }\n}`
  const partTemplate = `class Counter(var count: Int = ____1) {\n    fun inc() { ____2____3 }\n}`

  return {
    schemaVersion: "1.0",
    packId: "kotlin-sample-pack",
    version: "1.0.0",
    title: "Sample Kotlin Pack",
    description: "Verified sample course pack for learning Kotlin.",
    language: "kotlin",
    level: "beginner",
    publisher: {
      name: "Learn Code Team",
      homepage: "https://learn-code.dev",
      url: "https://github.com/learn-code"
    },
    license: "Apache-2.0",
    source: {
      url: "https://github.com/learn-code/kotlin-sample-pack",
      ref: "v1.0.0"
    },
    contentHash: "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    updatedAt: "2026-11-04T12:00:00.000Z",
    minimumClientVersion: "0.1.0",
    tags: ["kotlin", "intro"],
    courses: [
      {
        id: "kotlin-course-intro",
        title: "Introduction to Kotlin",
        description: "Fundamentals of idiomatic Kotlin syntax and problem solving.",
        chapters: [
          {
            id: "ch01-basics",
            title: "Basics",
            summary: "Core language features.",
            lessons: [
              {
                id: "l01-intro",
                title: "Values and Functions",
                explanation: "Understand immutability and basic declarations.",
                exercises: [
                  {
                    type: "multiple_choice",
                    id: "mc-val-var",
                    prompt: "Which keyword creates a read-only variable?",
                    code: "val x = 10",
                    options: ["val", "var", "let", "const"],
                    correctIndex: 0,
                    explanation: "val defines a read-only local variable."
                  },
                  {
                    type: "multiple_choice",
                    id: "mc-types",
                    prompt: "Which type is non-nullable by default?",
                    options: ["String", "String?", "Any?", "Nothing?"],
                    correctIndex: 0,
                    explanation: "Types in Kotlin are non-nullable unless marked with ?."
                  },
                  {
                    type: "cloze",
                    id: "cloze-val",
                    prompt: "Complete the immutable variable declaration.",
                    template: "____1 total: Int = ____2",
                    blanks: [
                      {
                        placeholder: "____1",
                        accepted: ["val"],
                        caseSensitive: true,
                        options: ["val", "var", "const", "def"]
                      },
                      {
                        placeholder: "____2",
                        accepted: ["100"],
                        caseSensitive: true,
                        options: ["100", "null", "void", "NaN"]
                      }
                    ],
                    explanation: "Use val for immutable bindings."
                  },
                  {
                    type: "predict_output",
                    id: "predict-print",
                    prompt: "What does this code print?",
                    code: "fun main() {\n    val greeting = \"hello\"\n    println(greeting)\n}",
                    expectedOutput: "hello",
                    explanation: "println prints the string followed by a newline."
                  },
                  {
                    type: "write_function",
                    id: "wf-add",
                    prompt: "Write a function that adds two integers.",
                    starterCode: "fun add(a: Int, b: Int): Int = TODO()",
                    hiddenTests: "import org.junit.Test\nimport org.junit.Assert.assertEquals\nclass AddTest {\n  @Test fun test() { assertEquals(3, add(1, 2)) }\n}",
                    referenceSolution: writeFuncRef,
                    hints: ["Use the + operator.", "Return the result directly."],
                    explanation: "Addition returns the sum.",
                    guided: {
                      template: writeFuncTemplate,
                      blanks: [
                        {
                          placeholder: "____1",
                          accepted: ["add"],
                          caseSensitive: true,
                          options: ["add", "sum", "plus", "calc"]
                        },
                        {
                          placeholder: "____2",
                          accepted: ["return"],
                          caseSensitive: true,
                          options: ["return", "yield", "val", "echo"]
                        },
                        {
                          placeholder: "____3",
                          accepted: ["+"],
                          caseSensitive: true,
                          options: ["+", "-", "*", "/"]
                        }
                      ],
                      explanation: "Return expression adds a and b."
                    }
                  },
                  {
                    type: "multi_part_problem",
                    id: "mpp-counter",
                    prompt: "Implement a simple counter class.",
                    story: "Build an incremental counter class step by step.",
                    parts: [
                      {
                        id: "part-counter-init",
                        prompt: "Define the counter class and inc method.",
                        starterCode: "class Counter(var count: Int) { fun inc(): Unit = TODO() }",
                        hiddenTests: "import org.junit.Test\nimport org.junit.Assert.assertEquals\nclass CounterTest {\n  @Test fun test() { val c = Counter(); c.inc(); assertEquals(1, c.count) }\n}",
                        referenceSolution: partRef,
                        expected: "Counter increments its internal count.",
                        hints: ["Default value is 0."],
                        guided: {
                          template: partTemplate,
                          blanks: [
                            {
                              placeholder: "____1",
                              accepted: ["0"],
                              caseSensitive: true,
                              options: ["0", "1", "-1", "null"]
                            },
                            {
                              placeholder: "____2",
                              accepted: ["count"],
                              caseSensitive: true,
                              options: ["count", "this", "val", "num"]
                            },
                            {
                              placeholder: "____3",
                              accepted: ["++"],
                              caseSensitive: true,
                              options: ["++", "--", "+=0", " = 0"]
                            }
                          ]
                        }
                      }
                    ],
                    faq: [
                      {
                        question: "Can count be private?",
                        answer: "For this exercise, keep it public or use the default visibility."
                      }
                    ]
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
}
