import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { computePackPayloadHash } from '../src/loader.ts'
import { validateCoursePack } from '../src/validator.ts'
import { buildPack, type CourseDraft } from './format.ts'
import { kotlin } from './kotlin.ts'
import { swift } from './swift.ts'
import { flutter } from './flutter.ts'
import { reactNative } from './react-native.ts'
import { java } from './java.ts'

const drafts: CourseDraft[] = [kotlin, swift, flutter, reactNative, java]
const packDir = resolve(dirname(fileURLToPath(import.meta.url)), '../packs')
const seenPrompts = new Map<string, string>()
const seenExerciseIds = new Set<string>()

for (const draft of drafts) {
  const pack = buildPack(draft)
  const exercises = pack.courses[0].chapters.flatMap((chapter) => chapter.lessons.flatMap((lesson) => lesson.exercises))
  if (exercises.length !== 80) throw new Error(`${draft.filename}: expected 80 exercises, found ${exercises.length}`)
  const types = new Set(exercises.map((exercise) => exercise.type))
  for (const type of ['multiple_choice', 'cloze', 'write_function', 'multi_part_problem']) {
    if (!types.has(type as typeof exercises[number]['type'])) throw new Error(`${draft.filename}: missing ${type}`)
  }
  for (const exercise of exercises) {
    if (seenExerciseIds.has(exercise.id)) throw new Error(`Duplicate exercise ID across beta packs: ${exercise.id}`)
    seenExerciseIds.add(exercise.id)
    const key = exercise.prompt.toLowerCase().replace(/[^a-z0-9]/g, '')
    const previous = seenPrompts.get(key)
    if (previous) throw new Error(`Duplicate beta prompt: ${previous} and ${exercise.id}`)
    seenPrompts.set(key, exercise.id)
    if (exercise.type !== 'multi_part_problem' && (!exercise.explanation || exercise.explanation.length < 50)) {
      throw new Error(`${exercise.id}: explanation must give meaningful feedback`)
    }
  }
  pack.contentHash = computePackPayloadHash(pack as unknown as Record<string, unknown>)
  const result = validateCoursePack(pack, { clientVersion: '0.1.0' })
  if (!result.valid || result.warnings.length) {
    throw new Error(`${draft.filename}: ${result.issues.map((issue) => `${issue.path}: ${issue.message}`).join('; ')}`)
  }
  mkdirSync(packDir, { recursive: true })
  writeFileSync(resolve(packDir, draft.filename), `${JSON.stringify(pack, null, 2)}\n`)
  console.log(`${draft.filename}: ${pack.courses[0].chapters.length} units, ${pack.courses[0].chapters.flatMap((chapter) => chapter.lessons).length} lessons, ${exercises.length} exercises, ${pack.contentHash}`)
}
