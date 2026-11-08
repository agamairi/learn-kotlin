export const BACKUP_FORMAT = 'learn-code-progress'
export const BACKUP_SCHEMA_VERSION = 1
export const MAX_BACKUP_BYTES = 5 * 1024 * 1024

export interface BackupExerciseResult {
  exerciseId: string
  problemId?: string | null
  correct: boolean
  xpAwarded: number
  epochMillis: number
  lessonId?: string | null
  chapterId?: string | null
}

export interface BackupAttemptHistory extends BackupExerciseResult {
  attemptId: string
  courseId?: string | null
  isRetry?: boolean
}

export interface BackupDailyActivity {
  epochDay: number
  xpEarned: number
  exercisesCompleted: number
  goalMet: boolean
}

export interface BackupXpLedgerEntry {
  entryId: string
  amount: number
  epochMillis: number
}

export interface BackupReviewCard {
  itemId: string
  box: number
  dueEpochDay: number
}

export interface LearnCodeBackup {
  format: typeof BACKUP_FORMAT
  schemaVersion: typeof BACKUP_SCHEMA_VERSION
  createdAt: string
  app: {
    version: string
    contentPacks: Array<{ packId: string; version: string }>
  }
  data: {
    attemptHistory: BackupAttemptHistory[]
    exerciseResults: BackupExerciseResult[]
    /** Optional for backwards compatibility with schema-version-1 backups created before the ledger was exported. */
    xpLedger?: BackupXpLedgerEntry[]
    dailyActivities: BackupDailyActivity[]
    reviewCards: BackupReviewCard[]
    completedLessons: Array<{ lessonId: string; completedAtEpochMillis: number }>
    frozenEpochDays: number[]
    heldStreakFreezes: number
    dailyGoalXp: number
    preferences: {
      selectedLanguage: string
      hearts?: number
      heartRefillAt?: number | null
    }
  }
}

export interface BackupValidationResult {
  valid: boolean
  errors: string[]
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const isFiniteNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value)

const isIntegerNumber = (value: unknown): value is number =>
  isFiniteNumber(value) && Number.isInteger(value)

const nonEmpty = (value: unknown): value is string =>
  typeof value === 'string' && value.trim().length > 0

export function validateBackupPayload(value: unknown): BackupValidationResult {
  const errors: string[] = []
  if (!isRecord(value)) return { valid: false, errors: ['Backup must be a JSON object'] }
  if (value.format !== BACKUP_FORMAT) errors.push(`Unsupported backup format: ${String(value.format)}`)
  if (value.schemaVersion !== BACKUP_SCHEMA_VERSION) errors.push('Unsupported backup schema version')
  if (!nonEmpty(value.createdAt)) errors.push('createdAt must be a non-empty string')
  if (!isRecord(value.app)) {
    errors.push('app metadata is required')
  } else {
    if (!nonEmpty(value.app.version)) errors.push('app.version must be a non-empty string')
    if (!Array.isArray(value.app.contentPacks)) errors.push('app.contentPacks must be an array')
  }
  if (!isRecord(value.data)) {
    errors.push('data is required')
    return { valid: false, errors }
  }

  const data = value.data
  for (const field of ['attemptHistory', 'exerciseResults', 'dailyActivities', 'reviewCards', 'completedLessons', 'frozenEpochDays']) {
    if (!Array.isArray(data[field])) errors.push(`data.${field} must be an array`)
  }
  if (data.xpLedger !== undefined && !Array.isArray(data.xpLedger)) errors.push('data.xpLedger must be an array when provided')
  if (!isFiniteNumber(data.heldStreakFreezes) || data.heldStreakFreezes < 0 || data.heldStreakFreezes > 2) {
    errors.push('data.heldStreakFreezes must be between 0 and 2')
  }
  if (!isFiniteNumber(data.dailyGoalXp) || data.dailyGoalXp < 0 || data.dailyGoalXp > 10000) {
    errors.push('data.dailyGoalXp must be between 0 and 10000')
  }
  if (!isRecord(data.preferences) || !nonEmpty(data.preferences.selectedLanguage)) {
    errors.push('data.preferences.selectedLanguage is required')
  } else {
    if (data.preferences.hearts !== undefined && (!isIntegerNumber(data.preferences.hearts) || data.preferences.hearts < 0 || data.preferences.hearts > 5)) {
      errors.push('data.preferences.hearts must be an integer between 0 and 5')
    }
    if (data.preferences.heartRefillAt !== null && data.preferences.heartRefillAt !== undefined && (!isFiniteNumber(data.preferences.heartRefillAt) || data.preferences.heartRefillAt <= 0)) {
      errors.push('data.preferences.heartRefillAt must be null or a positive timestamp')
    }
  }

  if (Array.isArray(data.exerciseResults)) {
    data.exerciseResults.forEach((result, index) => {
      if (!isRecord(result) || !nonEmpty(result.exerciseId)) errors.push(`data.exerciseResults[${index}].exerciseId is required`)
      if (isRecord(result) && result.problemId !== undefined && result.problemId !== null && !nonEmpty(result.problemId)) errors.push(`data.exerciseResults[${index}].problemId must be a non-empty string when provided`)
      if (!isRecord(result) || typeof result.correct !== 'boolean') errors.push(`data.exerciseResults[${index}].correct must be boolean`)
      if (!isRecord(result) || !isFiniteNumber(result.xpAwarded) || result.xpAwarded < 0 || result.xpAwarded > 1000) errors.push(`data.exerciseResults[${index}].xpAwarded is invalid`)
      if (!isRecord(result) || !isFiniteNumber(result.epochMillis) || result.epochMillis <= 0) errors.push(`data.exerciseResults[${index}].epochMillis is invalid`)
    })
  }
  if (Array.isArray(data.attemptHistory)) {
    data.attemptHistory.forEach((attempt, index) => {
      if (!isRecord(attempt) || !nonEmpty(attempt.attemptId)) errors.push(`data.attemptHistory[${index}].attemptId is required`)
      if (!isRecord(attempt) || !nonEmpty(attempt.exerciseId)) errors.push(`data.attemptHistory[${index}].exerciseId is required`)
      if (isRecord(attempt) && attempt.problemId !== undefined && attempt.problemId !== null && !nonEmpty(attempt.problemId)) errors.push(`data.attemptHistory[${index}].problemId must be a non-empty string when provided`)
      if (!isRecord(attempt) || typeof attempt.correct !== 'boolean') errors.push(`data.attemptHistory[${index}].correct must be boolean`)
      if (!isRecord(attempt) || !isFiniteNumber(attempt.epochMillis) || attempt.epochMillis <= 0) errors.push(`data.attemptHistory[${index}].epochMillis is invalid`)
    })
  }
  if (Array.isArray(data.xpLedger)) {
    data.xpLedger.forEach((entry, index) => {
      if (!isRecord(entry) || !nonEmpty(entry.entryId)) errors.push(`data.xpLedger[${index}].entryId is required`)
      if (!isRecord(entry) || !isFiniteNumber(entry.amount) || entry.amount < 0 || entry.amount > 100000) errors.push(`data.xpLedger[${index}].amount is invalid`)
      if (!isRecord(entry) || !isFiniteNumber(entry.epochMillis) || entry.epochMillis <= 0) errors.push(`data.xpLedger[${index}].epochMillis is invalid`)
    })
  }
  if (Array.isArray(data.dailyActivities)) {
    data.dailyActivities.forEach((activity, index) => {
      if (!isRecord(activity) || !isIntegerNumber(activity.epochDay)) errors.push(`data.dailyActivities[${index}].epochDay is invalid`)
      if (!isRecord(activity) || !isFiniteNumber(activity.xpEarned) || activity.xpEarned < 0) errors.push(`data.dailyActivities[${index}].xpEarned is invalid`)
      if (!isRecord(activity) || !isIntegerNumber(activity.exercisesCompleted) || activity.exercisesCompleted < 0) errors.push(`data.dailyActivities[${index}].exercisesCompleted is invalid`)
      if (!isRecord(activity) || typeof activity.goalMet !== 'boolean') errors.push(`data.dailyActivities[${index}].goalMet must be boolean`)
    })
  }
  if (Array.isArray(data.reviewCards)) {
    data.reviewCards.forEach((card, index) => {
      if (!isRecord(card) || !nonEmpty(card.itemId)) errors.push(`data.reviewCards[${index}].itemId is required`)
      if (!isRecord(card) || !isIntegerNumber(card.box) || card.box < 1 || card.box > 5) errors.push(`data.reviewCards[${index}].box must be between 1 and 5`)
      if (!isRecord(card) || !isIntegerNumber(card.dueEpochDay)) errors.push(`data.reviewCards[${index}].dueEpochDay is invalid`)
    })
  }
  if (Array.isArray(data.completedLessons)) {
    data.completedLessons.forEach((lesson, index) => {
      if (!isRecord(lesson) || !nonEmpty(lesson.lessonId)) errors.push(`data.completedLessons[${index}].lessonId is required`)
      if (!isRecord(lesson) || !isFiniteNumber(lesson.completedAtEpochMillis) || lesson.completedAtEpochMillis <= 0) errors.push(`data.completedLessons[${index}].completedAtEpochMillis is invalid`)
    })
  }
  if (Array.isArray(data.frozenEpochDays)) {
    data.frozenEpochDays.forEach((day, index) => {
      if (!Number.isInteger(day)) errors.push(`data.frozenEpochDays[${index}] must be an integer`)
    })
  }
  return { valid: errors.length === 0, errors }
}

export function serializeBackup(payload: LearnCodeBackup): string {
  const result = validateBackupPayload(payload)
  if (!result.valid) throw new Error(result.errors.join('; '))
  return JSON.stringify(payload, null, 2)
}

export function parseBackup(serialized: string): LearnCodeBackup {
  if (new TextEncoder().encode(serialized).byteLength > MAX_BACKUP_BYTES) throw new Error('Backup file is too large')
  let parsed: unknown
  try {
    parsed = JSON.parse(serialized)
  } catch {
    throw new Error('Backup is not valid JSON')
  }
  const result = validateBackupPayload(parsed)
  if (!result.valid) throw new Error(result.errors.join('; '))
  return parsed as LearnCodeBackup
}
