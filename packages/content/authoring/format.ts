import type { ChapterDefinition, CoursePack, Exercise, LessonDefinition } from '../src/types.ts'

type Difficulty = 'easy' | 'medium' | 'hard'
type Draft = Exercise

function rotate<T>(items: T[], by: number): T[] {
  return [...items.slice(by), ...items.slice(0, by)]
}

export function m(id: string, prompt: string, code: string, correct: string, wrong1: string, wrong2: string, wrong3: string, explanation: string, difficulty: Difficulty = 'medium'): Draft {
  return { id, type: 'multiple_choice', prompt, ...(code ? { code } : {}), options: [correct, wrong1, wrong2, wrong3], correctIndex: 0, explanation, difficulty }
}

export function c(id: string, prompt: string, template: string, correct: string, wrong1: string, wrong2: string, wrong3: string, explanation: string, difficulty: Difficulty = 'medium'): Draft {
  return { id, type: 'cloze', prompt, template, blanks: [{ placeholder: '____1', accepted: [correct], options: [correct, wrong1, wrong2, wrong3] }], explanation, difficulty }
}

type Choice = [string, string, string, string]

function guided(template: string, choices: [Choice, Choice, Choice]) {
  const blanks = choices.map((options, index) => ({ placeholder: `____${index + 1}`, accepted: [options[0]], options: rotate(options, index) }))
  let solution = template
  blanks.forEach((blank) => { solution = solution.replace(blank.placeholder, blank.accepted[0]) })
  return { guided: { template, blanks }, referenceSolution: solution }
}

export function g(id: string, prompt: string, template: string, choices: [Choice, Choice, Choice], tests: string, explanation: string, difficulty: Difficulty = 'hard'): Draft {
  const puzzle = guided(template, choices)
  return { id, type: 'write_function', prompt, starterCode: template, hiddenTests: tests, referenceSolution: puzzle.referenceSolution, guided: { ...puzzle.guided, explanation }, explanation, difficulty }
}

export function p(id: string, prompt: string, story: string, parts: Array<{ id: string; prompt: string; template: string; choices: [Choice, Choice, Choice]; tests: string; expected: string }>, difficulty: Difficulty = 'hard'): Draft {
  return {
    id, type: 'multi_part_problem', prompt, story, difficulty,
    parts: parts.map((part) => {
      const puzzle = guided(part.template, part.choices)
      return { id: part.id, prompt: part.prompt, starterCode: part.template, hiddenTests: part.tests, referenceSolution: puzzle.referenceSolution, guided: { ...puzzle.guided, explanation: part.expected }, expected: part.expected }
    })
  }
}

export function lesson(id: string, title: string, explanation: string, exercises: Draft[]): LessonDefinition {
  if (exercises.length !== 8) throw new Error(`${id} must contain exactly eight exercises; found ${exercises.length}`)
  return { id, title, explanation, exercises: exercises as Exercise[] }
}

export function unit(id: string, title: string, summary: string, first: LessonDefinition, second: LessonDefinition): ChapterDefinition {
  let choiceIndex = 0
  const lessons = [first, second]
  for (const exercise of lessons.flatMap((item) => item.exercises)) {
    if (exercise.type === 'multiple_choice') {
      const rotated = rotate(exercise.options, choiceIndex % 4)
      exercise.options = rotated
      // The authored correct answer was first before rotation.
      exercise.correctIndex = (4 - (choiceIndex % 4)) % 4
      choiceIndex += 1
    }
  }
  return { id, title, summary, lessons }
}

export interface CourseDraft {
  filename: string
  packId: string
  courseId: string
  title: string
  description: string
  language: string
  chapters: ChapterDefinition[]
}

export function buildPack(draft: CourseDraft): CoursePack {
  if (draft.chapters.length !== 5) throw new Error(`${draft.packId} must contain five units`)
  for (const chapter of draft.chapters) {
    for (const exercise of chapter.lessons.flatMap((lesson) => lesson.exercises)) {
      exercise.topicTags = [chapter.id.replace(/-unit-.*/, '') || draft.language, chapter.id]
      exercise.estimatedSeconds = exercise.difficulty === 'hard' ? 85 : exercise.difficulty === 'easy' ? 35 : 55
    }
  }
  return {
    schemaVersion: '1.0', packId: draft.packId, version: '2.0.0', title: draft.title,
    description: draft.description, language: draft.language, level: 'intermediate',
    publisher: { name: 'Learn Code' }, license: 'CC BY 4.0',
    source: { url: 'https://github.com/agamairi/learn-kotlin', path: `packages/content/packs/${draft.filename}` },
    contentHash: '', updatedAt: '2026-11-06T00:00:00Z', minimumClientVersion: '0.1.0',
    capabilities: ['mobile', 'interview-reasoning', 'guided-puzzles'],
    tags: ['interviews', 'mobile', draft.language],
    courses: [{ id: draft.courseId, title: draft.title, description: draft.description, language: draft.language, chapters: draft.chapters }]
  }
}
