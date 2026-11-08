import assert from 'node:assert/strict'
import test from 'node:test'
import { BACKUP_FORMAT, BACKUP_SCHEMA_VERSION, parseBackup, serializeBackup, validateBackupPayload, type LearnCodeBackup } from './backup'

const validBackup = (): LearnCodeBackup => ({
  format: BACKUP_FORMAT,
  schemaVersion: BACKUP_SCHEMA_VERSION,
  createdAt: '2026-11-05T00:00:00.000Z',
  app: { version: '0.1.0', contentPacks: [{ packId: 'kotlin-foundations', version: '1.0.0' }] },
  data: {
    attemptHistory: [{ attemptId: 'attempt-1', exerciseId: 'kotlin-s1', correct: true, xpAwarded: 10, epochMillis: 1762300800000, lessonId: 'kotlin-syntax-core', chapterId: 'kotlin-unit-syntax' }],
    exerciseResults: [{ exerciseId: 'kotlin-s1', correct: true, xpAwarded: 10, epochMillis: 1762300800000, lessonId: 'kotlin-syntax-core', chapterId: 'kotlin-unit-syntax' }],
    dailyActivities: [{ epochDay: 20428, xpEarned: 10, exercisesCompleted: 1, goalMet: false }],
    reviewCards: [{ itemId: 'kotlin-s1', box: 1, dueEpochDay: 20429 }],
    completedLessons: [{ lessonId: 'kotlin-syntax-core', completedAtEpochMillis: 1762300800000 }],
    frozenEpochDays: [],
    heldStreakFreezes: 0,
    dailyGoalXp: 30,
      preferences: { selectedLanguage: 'kotlin', hearts: 5, heartRefillAt: null },
  },
})

test('backup round trip preserves the validated payload', () => {
  const parsed = parseBackup(serializeBackup(validBackup()))
  assert.deepEqual(parsed, validBackup())
})

test('invalid backup is rejected before import', () => {
  const result = validateBackupPayload({ format: BACKUP_FORMAT, schemaVersion: 99 })
  assert.equal(result.valid, false)
  assert.ok(result.errors.some((error) => error.includes('schema version')))
  assert.throws(() => parseBackup('{"format":"learn-code-progress"}'))
})

test('backup rejects unsafe ranges', () => {
  const backup = validBackup()
  backup.data.reviewCards[0].box = 8
  assert.equal(validateBackupPayload(backup).valid, false)
})
