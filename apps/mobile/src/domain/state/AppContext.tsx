import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { effectiveEpochDay } from '@learn/core'
import type { TabRoute, ScreenMode, LessonParams } from '../../types/navigation'
import type { SupportedLanguage } from '../../types/languages'
import { BUNDLED_PACK_METADATA, COURSE_CATALOG, getCourse } from '../../data/courseCatalog'
import { LeitnerScheduler, ProgressRecorder, type RecordOutcome } from '@learn/core'
import { serializeBackup } from '@learn/storage'
import { SQLiteCourseRepository, SQLiteProgressRepository } from '../persistence/sqliteProgressRepository'
import { createBackupFile, restoreBackupFile } from '../persistence/backupFiles'
import { getAdFreeEntitlement, purchaseAdRemoval, restoreAdRemoval } from '../../services/billing'
import { showRewardedHeartAd } from '../../services/ads'

export interface UserSession {
  type: 'guest'
  id: string
  displayName: string
}

export interface LessonSettlementInfo {
  baseXp: number
  paceBonusXp: number
  totalXp: number
  duplicate: boolean
}

export interface ReviewCardView {
  id: string
  question: string
  answer: string
  codeSnippet?: string
  box: number
  dueToday: boolean
}

export interface AppState {
  screen: ScreenMode
  currentTab: TabRoute
  activeLessonParams: LessonParams | null
  user: UserSession
  selectedLanguage: SupportedLanguage
  isHydrated: boolean
  storageError: string | null
  xpToday: number
  totalXp: number
  dailyTargetXp: number
  currentStreak: number
  longestStreak: number
  freezesHeld: number
  completedLessonIds: string[]
  reviewCards: ReviewCardView[]
  hearts: number
  maxHearts: number
  nextHeartRefillAt: number | null
  setTab: (tab: TabRoute) => void
  setSelectedLanguage: (language: SupportedLanguage) => void
  startLesson: (chapterId: string, lessonId: string) => Promise<void>
  finishLesson: (lessonId: string, activeDurationMs: number) => Promise<LessonSettlementInfo>
  exitLesson: () => void
  continueAsGuest: () => void
  setDailyGoalTarget: (targetXp: number) => Promise<void>
  recordExerciseGrade: (exerciseId: string, isCorrect: boolean, optionIndex: number, isRetry?: boolean) => Promise<boolean>
  recordProblemPartGrade: (problemId: string, partId: string, isCorrect: boolean, isRetry?: boolean) => Promise<boolean>
  scheduleCardReview: (cardId: string, correct: boolean) => Promise<void>
  resetProgress: () => Promise<void>
  createBackup: () => Promise<string>
  restoreBackup: () => Promise<void>
  recoverHeartFromReview: () => Promise<void>
  recoverHeartFromRewardedAd: () => Promise<boolean>
  isAdFree: boolean
  purchaseRemoveAds: () => Promise<boolean>
  restorePurchases: () => Promise<boolean>
}

const AppContext = createContext<AppState | null>(null)
const TODAY_TIME_ZONE = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
const APP_VERSION = '0.1.0'
const MAX_HEARTS = 5
const HEART_REFILL_MS = 30 * 60 * 1000

const isSupportedLanguage = (value: string | null): value is SupportedLanguage =>
  value === 'kotlin' || value === 'swift' || value === 'flutter' || value === 'react-native' || value === 'java'

const preferredLanguage = (value: string | null): SupportedLanguage =>
  value === 'react' || value === 'typescript' ? 'react-native' : isSupportedLanguage(value) ? value : 'kotlin'

const createRunId = () => `run_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
const createAttemptId = () => `attempt_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [screen, setScreen] = useState<ScreenMode>('onboarding')
  const [currentTab, setCurrentTab] = useState<TabRoute>('home')
  const [activeLessonParams, setActiveLessonParams] = useState<LessonParams | null>(null)
  const [selectedLanguage, setSelectedLanguageState] = useState<SupportedLanguage>('kotlin')
  const [isHydrated, setIsHydrated] = useState(false)
  const [storageError, setStorageError] = useState<string | null>(null)
  const [xpToday, setXpToday] = useState(0)
  const [totalXp, setTotalXp] = useState(0)
  const [dailyTargetXp, setDailyTargetXpState] = useState(30)
  const [currentStreak, setCurrentStreak] = useState(0)
  const [longestStreak, setLongestStreak] = useState(0)
  const [freezesHeld, setFreezesHeld] = useState(0)
  const [completedLessonIds, setCompletedLessonIds] = useState<string[]>([])
  const [reviewCards, setReviewCards] = useState<ReviewCardView[]>([])
  const [hearts, setHearts] = useState(MAX_HEARTS)
  const [nextHeartRefillAt, setNextHeartRefillAt] = useState<number | null>(null)
  const [isAdFree, setIsAdFree] = useState(false)
  const [repository, setRepository] = useState<SQLiteProgressRepository | null>(null)
  const [recorder, setRecorder] = useState<ProgressRecorder | null>(null)

  const allCourses = useMemo(() => Object.values(COURSE_CATALOG), [])

  const refreshState = useCallback(async (repo: SQLiteProgressRepository, progressRecorder: ProgressRecorder, language: SupportedLanguage) => {
    const streak = await progressRecorder.refreshStreak()
    const today = effectiveEpochDay(Date.now(), TODAY_TIME_ZONE)
    const snapshot = await repo.getSnapshot(today, streak)
    const heartValue = await repo.getPreference('hearts')
    const refillValue = await repo.getPreference('heart_refill_at')
    let currentHearts = heartValue == null ? MAX_HEARTS : Math.max(0, Math.min(MAX_HEARTS, Number(heartValue)))
    let refillAt = refillValue ? Number(refillValue) : null
    if (currentHearts < MAX_HEARTS && refillAt && refillAt <= Date.now()) {
      const recovered = Math.min(MAX_HEARTS - currentHearts, Math.floor((Date.now() - refillAt) / HEART_REFILL_MS) + 1)
      currentHearts += recovered
      refillAt = currentHearts >= MAX_HEARTS ? null : refillAt + recovered * HEART_REFILL_MS
      await repo.setPreference('hearts', String(currentHearts))
      await repo.setPreference('heart_refill_at', refillAt == null ? '' : String(refillAt))
    }
    const cardById = new Map(allCourses.flatMap((course) => course.chapters.flatMap((chapter) => chapter.lessons.flatMap((lesson) => lesson.exercises.map((exercise) => [exercise.id, { exercise }] as const)))))
    const cards: ReviewCardView[] = snapshot.reviewCards.map((card) => {
      const source = cardById.get(card.itemId)
      return {
        id: card.itemId,
        question: source?.exercise.prompt ?? 'Review this idea from your course.',
        answer: source && 'explanation' in source.exercise && typeof source.exercise.explanation === 'string' ? source.exercise.explanation : 'Return to the lesson example and explain the decision in your own words.',
        codeSnippet: source && 'code' in source.exercise && typeof source.exercise.code === 'string' ? source.exercise.code : undefined,
        box: card.box,
        dueToday: card.dueEpochDay <= today,
      }
    })
    setSelectedLanguageState(language)
    setXpToday(snapshot.xpToday)
    setTotalXp(snapshot.totalXp)
    setDailyTargetXpState(snapshot.dailyGoalXp)
    setCurrentStreak(snapshot.currentStreak)
    setLongestStreak(snapshot.longestStreak)
    setFreezesHeld(snapshot.freezesHeld)
    setCompletedLessonIds(snapshot.completedLessonIds)
    setReviewCards(cards)
    setHearts(currentHearts)
    setNextHeartRefillAt(refillAt)
  }, [allCourses])

  useEffect(() => {
    let cancelled = false
    const hydrate = async () => {
      try {
        const repo = await SQLiteProgressRepository.open()
        const progressRecorder = new ProgressRecorder(repo, new SQLiteCourseRepository(allCourses), repo, undefined, undefined, undefined, TODAY_TIME_ZONE)
        const storedLanguage = await repo.getPreference('selectedLanguage')
        const onboardingCompleted = (await repo.getPreference('onboardingCompleted')) === 'true'
        const language = preferredLanguage(storedLanguage)
        if (cancelled) return
        setRepository(repo)
        setRecorder(progressRecorder)
        await refreshState(repo, progressRecorder, language)
        setIsAdFree(await getAdFreeEntitlement())
        if (!cancelled) {
          setScreen(onboardingCompleted ? 'main' : 'onboarding')
          setIsHydrated(true)
          setStorageError(null)
        }
      } catch (error) {
        if (!cancelled) {
          setStorageError(error instanceof Error ? error.message : 'Local progress storage could not be opened.')
          setIsHydrated(true)
        }
      }
    }
    void hydrate()
    return () => { cancelled = true }
  }, [allCourses, refreshState])

  const setTab = useCallback((tab: TabRoute) => {
    setCurrentTab(tab)
    if (screen !== 'main') setScreen('main')
  }, [screen])

  const setSelectedLanguage = useCallback((language: SupportedLanguage) => {
    setSelectedLanguageState(language)
    setCurrentTab('home')
    if (repository) void repository.setPreference('selectedLanguage', language)
  }, [repository])

  const startLesson = useCallback(async (chapterId: string, lessonId: string) => {
    if (!recorder) return
    const runId = createRunId()
    const started = await recorder.beginLessonRun(lessonId, runId)
    if (!started) {
      setStorageError('This lesson is not available in the active course pack.')
      return
    }
    setActiveLessonParams({ chapterId, lessonId, runId })
    setScreen('lesson')
  }, [recorder])

  const exitLesson = useCallback(() => {
    if (activeLessonParams?.runId && recorder) recorder.abandonLessonRun(activeLessonParams.runId)
    setActiveLessonParams(null)
    setScreen('main')
  }, [activeLessonParams, recorder])

  const continueAsGuest = useCallback(() => {
    setScreen('main')
    setCurrentTab('home')
    if (repository) void repository.setPreference('onboardingCompleted', 'true')
  }, [repository])

  const setDailyGoalTarget = useCallback(async (target: number) => {
    if (!repository) return
    await repository.setDailyGoalXp(target)
    setDailyTargetXpState(Math.round(target))
  }, [repository])

  const recordExerciseGrade = useCallback(async (exerciseId: string, isCorrect: boolean, _optionIndex: number, isRetry = false) => {
    if (!recorder || !repository || !activeLessonParams?.runId) return false
    const attemptId = createAttemptId()
    const outcome: RecordOutcome = await recorder.record({ attemptId, exerciseId, correct: isCorrect, isLessonRetry: isRetry, lessonRunId: activeLessonParams.runId })
    if (outcome.type === 'rejected') {
      setStorageError(outcome.reason ?? 'The answer could not be recorded.')
      return false
    }
    await repository.recordAttemptHistory({
      attemptId,
      exerciseId,
      correct: isCorrect,
      xpAwarded: outcome.type === 'recorded' ? outcome.xpAwarded : 0,
      epochMillis: Date.now(),
      lessonId: activeLessonParams.lessonId,
      chapterId: activeLessonParams.chapterId,
      courseId: getCourse(selectedLanguage).id,
      isRetry,
    })
    if (!isCorrect && outcome.type === 'recorded') {
      const nextHearts = Math.max(0, hearts - 1)
      const refillAt = nextHearts < MAX_HEARTS ? (nextHeartRefillAt ?? Date.now() + HEART_REFILL_MS) : null
      await repository.setPreference('hearts', String(nextHearts))
      await repository.setPreference('heart_refill_at', refillAt == null ? '' : String(refillAt))
      setHearts(nextHearts)
      setNextHeartRefillAt(refillAt)
    }
    return outcome.type === 'recorded'
  }, [activeLessonParams, hearts, nextHeartRefillAt, recorder, repository, selectedLanguage])

  const recordProblemPartGrade = useCallback(async (problemId: string, partId: string, isCorrect: boolean, isRetry = false) => {
    if (!recorder || !repository || !activeLessonParams?.runId) return false
    const attemptId = createAttemptId()
    const outcome: RecordOutcome = await recorder.record({
      attemptId,
      problemId,
      partId,
      correct: isCorrect,
      isLessonRetry: isRetry,
      lessonRunId: activeLessonParams.runId,
    })
    if (outcome.type === 'rejected') {
      setStorageError(outcome.reason ?? 'The answer could not be recorded.')
      return false
    }
    await repository.recordAttemptHistory({
      attemptId,
      exerciseId: partId,
      problemId,
      correct: isCorrect,
      xpAwarded: outcome.type === 'recorded' ? outcome.xpAwarded : 0,
      epochMillis: Date.now(),
      lessonId: activeLessonParams.lessonId,
      chapterId: activeLessonParams.chapterId,
      courseId: getCourse(selectedLanguage).id,
      isRetry,
    })
    if (!isCorrect && outcome.type === 'recorded') {
      const nextHearts = Math.max(0, hearts - 1)
      const refillAt = nextHearts < MAX_HEARTS ? (nextHeartRefillAt ?? Date.now() + HEART_REFILL_MS) : null
      await repository.setPreference('hearts', String(nextHearts))
      await repository.setPreference('heart_refill_at', refillAt == null ? '' : String(refillAt))
      setHearts(nextHearts)
      setNextHeartRefillAt(refillAt)
    }
    return outcome.type === 'recorded'
  }, [activeLessonParams, hearts, nextHeartRefillAt, recorder, repository, selectedLanguage])

  const finishLesson = useCallback(async (lessonId: string, activeDurationMs: number): Promise<LessonSettlementInfo> => {
    if (!recorder || !repository || !activeLessonParams?.runId) return { baseXp: 0, paceBonusXp: 0, totalXp: 0, duplicate: false }
    const result = await recorder.settleLessonRun(lessonId, activeLessonParams.runId, { activeDurationMs })
    await refreshState(repository, recorder, selectedLanguage)
    if (result.type === 'settled') return { baseXp: result.baseXp, paceBonusXp: result.paceBonusXp, totalXp: result.totalXp, duplicate: false }
    return { baseXp: 0, paceBonusXp: 0, totalXp: 0, duplicate: result.type === 'duplicate' }
  }, [activeLessonParams, recorder, refreshState, repository, selectedLanguage])

  const scheduleCardReview = useCallback(async (cardId: string, correct: boolean) => {
    if (!repository) return
    const existing = await repository.getReviewCard(cardId)
    if (!existing) return
    const today = effectiveEpochDay(Date.now(), TODAY_TIME_ZONE)
    const scheduled = new LeitnerScheduler().scheduleNextReview(existing, correct, today)
    await repository.upsertReviewCard(scheduled)
    if (correct && hearts < MAX_HEARTS) {
      const nextHearts = hearts + 1
      await repository.setPreference('hearts', String(nextHearts))
      setHearts(nextHearts)
    }
    if (recorder) await refreshState(repository, recorder, selectedLanguage)
  }, [hearts, recorder, refreshState, repository, selectedLanguage])

  const resetProgress = useCallback(async () => {
    if (!repository) return
    await repository.clearProgress()
    await repository.setPreference('hearts', String(MAX_HEARTS))
    await repository.setPreference('heart_refill_at', '')
    if (recorder) await refreshState(repository, recorder, selectedLanguage)
  }, [recorder, refreshState, repository, selectedLanguage])

  const createBackup = useCallback(async () => {
    if (!repository) throw new Error('Progress storage is not ready.')
    const backup = await repository.createBackup(APP_VERSION, BUNDLED_PACK_METADATA, selectedLanguage)
    return createBackupFile(serializeBackup(backup))
  }, [repository, selectedLanguage])

  const restoreBackup = useCallback(async () => {
    if (!repository || !recorder) throw new Error('Progress storage is not ready.')
    const serialized = await restoreBackupFile()
    const backup = await repository.restoreBackup(serialized)
    const language = preferredLanguage(backup.data.preferences.selectedLanguage ?? selectedLanguage)
    await refreshState(repository, recorder, language)
  }, [recorder, refreshState, repository, selectedLanguage])

  const recoverHeartFromReview = useCallback(async () => {
    if (!repository || hearts >= MAX_HEARTS) return
    const nextHearts = hearts + 1
    await repository.setPreference('hearts', String(nextHearts))
    setHearts(nextHearts)
  }, [hearts, repository])

  const recoverHeartFromRewardedAd = useCallback(async () => {
    if (isAdFree) {
      await recoverHeartFromReview()
      return true
    }
    const rewarded = await showRewardedHeartAd()
    if (!rewarded) return false
    await recoverHeartFromReview()
    return true
  }, [isAdFree, recoverHeartFromReview])

  const purchaseRemoveAds = useCallback(async () => {
    const active = await purchaseAdRemoval()
    if (active) setIsAdFree(true)
    return active
  }, [])

  const restorePurchases = useCallback(async () => {
    const active = await restoreAdRemoval()
    if (active) setIsAdFree(true)
    return active
  }, [])

  const value: AppState = {
    screen,
    currentTab,
    activeLessonParams,
    user: { type: 'guest', id: 'guest-local-user', displayName: 'Learner' },
    selectedLanguage,
    isHydrated,
    storageError,
    xpToday,
    totalXp,
    dailyTargetXp,
    currentStreak,
    longestStreak,
    freezesHeld,
    completedLessonIds,
    reviewCards,
    hearts,
    maxHearts: MAX_HEARTS,
    nextHeartRefillAt,
    setTab,
    setSelectedLanguage,
    startLesson,
    finishLesson,
    exitLesson,
    continueAsGuest,
    setDailyGoalTarget,
    recordExerciseGrade,
    recordProblemPartGrade,
    scheduleCardReview,
    resetProgress,
    createBackup,
    restoreBackup,
    recoverHeartFromReview,
    recoverHeartFromRewardedAd,
    isAdFree,
    purchaseRemoveAds,
    restorePurchases,
  }

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

export const useApp = (): AppState => {
  const context = useContext(AppContext)
  if (!context) throw new Error('useApp must be used within an AppProvider')
  return context
}
