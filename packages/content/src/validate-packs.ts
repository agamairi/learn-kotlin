import { readdir, readFile } from "node:fs/promises"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { loadCoursePack } from "./loader.ts"

const here = dirname(fileURLToPath(import.meta.url))
const packsDir = resolve(here, "../packs")
const files = (await readdir(packsDir)).filter((file) => file.endsWith(".json"))
let failed = false

for (const file of files.sort()) {
  const input = await readFile(resolve(packsDir, file), "utf8")
  const result = loadCoursePack(input, { verifyContentHash: true, clientVersion: "0.1.0" })
  if (!result.ok) {
    failed = true
    console.error(`✗ ${file}`)
    for (const issue of result.errors) console.error(`  ${issue.path}: ${issue.message}`)
    continue
  }
  const courseCount = result.pack?.courses.length ?? 0
  const lessonCount = result.pack?.courses.reduce((sum, course) => sum + course.chapters.reduce((chapterSum, chapter) => chapterSum + chapter.lessons.length, 0), 0) ?? 0
  const exerciseCount = result.pack?.courses.reduce((sum, course) => sum + course.chapters.reduce((chapterSum, chapter) => chapterSum + chapter.lessons.reduce((lessonSum, lesson) => lessonSum + lesson.exercises.length, 0), 0), 0) ?? 0
  console.log(`✓ ${file}: ${courseCount} course(s), ${lessonCount} lesson(s), ${exerciseCount} exercise(s), hash verified`)
}

if (files.length === 0) {
  console.error(`No course packs found in ${packsDir}`)
  process.exitCode = 1
} else if (failed) {
  process.exitCode = 1
}
