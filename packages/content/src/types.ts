export const CURRENT_SCHEMA_VERSION = "1.0"
export const SUPPORTED_SCHEMA_VERSIONS = ["1.0"] as const

export type Severity = "error" | "warning"

export interface ValidationIssue {
  severity: Severity
  path: string
  message: string
  exerciseId?: string
  file?: string
}

export interface ValidationResult {
  valid: boolean
  issues: ValidationIssue[]
  errors: ValidationIssue[]
  warnings: ValidationIssue[]
}

export interface CoursePackPublisher {
  name: string
  homepage?: string
  email?: string
  url?: string
  [key: string]: unknown
}

export interface CoursePackSource {
  url: string
  ref?: string
  commit?: string
  path?: string
  [key: string]: unknown
}

export type ExerciseType =
  | "multiple_choice"
  | "cloze"
  | "predict_output"
  | "write_function"
  | "multi_part_problem"

export interface BaseExercise {
  id: string
  prompt: string
  type: ExerciseType
  topicTags?: string[]
  difficulty?: "easy" | "medium" | "hard" | string
  estimatedSeconds?: number
  explanation?: string
  aiGenerated?: boolean
  sourceNote?: string
  [key: string]: unknown
}

export interface MultipleChoiceGrading {
  kind?: "choice" | string
  correctIndex: number
}

export interface MultipleChoiceExercise extends BaseExercise {
  type: "multiple_choice"
  code?: string
  options: string[]
  correctIndex: number
  grading?: MultipleChoiceGrading
  explanation: string
}

export interface ClozeBlank {
  placeholder: string
  accepted: string[]
  caseSensitive?: boolean
  options?: string[]
  [key: string]: unknown
}

export interface ClozeExercise extends BaseExercise {
  type: "cloze"
  template: string
  blanks: ClozeBlank[]
  explanation: string
}

export interface PredictOutputExercise extends BaseExercise {
  type: "predict_output"
  code: string
  expectedOutput: string
  explanation: string
}

export interface CodePuzzle {
  template: string
  blanks: ClozeBlank[]
  explanation?: string
  [key: string]: unknown
}

export interface WriteFunctionExercise extends BaseExercise {
  type: "write_function"
  starterCode?: string
  hiddenTests?: string
  referenceSolution?: string
  hints?: string[]
  explanation?: string
  guided: CodePuzzle
}

export interface ProblemPart {
  id: string
  prompt: string
  starterCode?: string
  hiddenTests?: string
  referenceSolution?: string
  expected: string
  hints?: string[]
  guided: CodePuzzle
  [key: string]: unknown
}

export interface FaqItem {
  question: string
  answer: string
  [key: string]: unknown
}

export interface MultiPartProblemExercise extends BaseExercise {
  type: "multi_part_problem"
  story: string
  parts: ProblemPart[]
  faq?: FaqItem[]
  discussion?: string
  sourceNote?: string
}

export type Exercise =
  | MultipleChoiceExercise
  | ClozeExercise
  | PredictOutputExercise
  | WriteFunctionExercise
  | MultiPartProblemExercise

export interface LessonDefinition {
  id: string
  title: string
  explanation: string
  exercises: Exercise[]
  [key: string]: unknown
}

export interface ChapterDefinition {
  id: string
  title: string
  summary: string
  lessons: LessonDefinition[]
  [key: string]: unknown
}

export interface CourseDefinition {
  id: string
  title: string
  description: string
  language?: string
  chapters: ChapterDefinition[]
  [key: string]: unknown
}

export interface CoursePack {
  schemaVersion: string
  packId: string
  version: string
  title: string
  description: string
  language: string
  level?: string
  publisher: CoursePackPublisher
  license: string
  source: CoursePackSource
  contentHash: string
  updatedAt: string
  minimumClientVersion?: string
  capabilities?: string[]
  tags?: string[]
  courses: CourseDefinition[]
  [key: string]: unknown
}

export type InstalledPackStatus = "active" | "updating" | "rollback" | "disabled"

export interface InstalledPackRecord {
  packId: string
  version: string
  source: CoursePackSource
  contentHash: string
  installedAt: string
  lastKnownGood: boolean
  schemaVersion: string
  minimumClientVersion?: string
  status: InstalledPackStatus
  previousVersion?: string
  metadata?: Record<string, unknown>
}

export interface PackRollbackRecord {
  packId: string
  failedVersion: string
  rolledBackToVersion: string
  reason: string
  rolledBackAt: string
}

export type PackUpdateType = "patch" | "minor" | "major" | "downgrade" | "equal"

export interface PackUpdateCheckResult {
  packId: string
  hasUpdate: boolean
  currentVersion: string
  candidateVersion: string
  updateType: PackUpdateType
  isCompatible: boolean
  incompatibilityReason?: string
  contentChanged: boolean
}

export interface PackCompatibilityResult {
  compatible: boolean
  reason?: string
}

export interface LoadCoursePackOptions {
  verifyContentHash?: boolean
  expectedHash?: string
  clientVersion?: string
  validateSemantics?: boolean
  allowWarnings?: boolean
}

export interface LoadCoursePackResult {
  ok: boolean
  pack?: CoursePack
  contentHash?: string
  errors: ValidationIssue[]
  warnings: ValidationIssue[]
}
