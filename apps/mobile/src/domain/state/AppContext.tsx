import React, { createContext, useContext, useState, useMemo, useCallback, useEffect } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { TabRoute, ScreenMode, LessonParams, SyncStatus } from '../../types/navigation';
import type { SupportedLanguage } from '../../types/languages';
import { INITIAL_REVIEW_CARDS, type DemoFlashcard } from '../../data/initialStore';
import { MobileSyncManager } from '../sync/syncAdapter';
import { LeitnerScheduler, CARD_XP, type ReviewCard } from '@learn/core';
import type { SyncEvent } from '@learn/api';

export interface UserSession {
  type: 'guest' | 'account';
  id: string;
  email?: string;
  displayName: string;
}

export type ByokProvider = 'openai-compatible' | 'anthropic-compatible' | 'custom';

export interface AppState {
  // Navigation
  screen: ScreenMode;
  currentTab: TabRoute;
  activeLessonParams: LessonParams | null;

  // Session
  user: UserSession;
  selectedLanguage: SupportedLanguage;
  byokProvider: ByokProvider;
  byokKeyConfigured: boolean;

  // Learning Progress
  xpToday: number;
  totalXp: number;
  dailyTargetXp: number;
  currentStreak: number;
  longestStreak: number;
  freezesHeld: number;
  completedLessonIds: string[];

  // Spaced Repetition (Leitner 5-box)
  reviewCards: DemoFlashcard[];

  // Sync
  syncStatus: SyncStatus;
  pendingOutboxCount: number;
  lastSyncedAt: string | null;

  // Actions
  setTab: (tab: TabRoute) => void;
  setSelectedLanguage: (language: SupportedLanguage) => void;
  startLesson: (chapterId: string, lessonId: string) => void;
  finishLesson: (lessonId: string, earnedXp: number) => void;
  exitLesson: () => void;
  continueAsGuest: () => void;
  signIn: (email: string, displayName?: string) => void;
  signOut: () => void;
  setDailyGoalTarget: (targetXp: number) => void;
  recordExerciseGrade: (
    exerciseId: string,
    isCorrect: boolean,
    optionIndex: number,
    xpAwarded?: number
  ) => boolean;
  scheduleCardReview: (cardId: string, correct: boolean) => void;
  triggerManualSync: () => Promise<void>;
  resetProgress: () => void;
  saveByokConfig: (provider: ByokProvider, apiKey: string) => Promise<boolean>;
  clearByokConfig: () => Promise<void>;
}

const AppContext = createContext<AppState | null>(null);

const createEventId = (kind: string): string =>
  `evt_${Date.now()}_${kind}_${Math.random().toString(36).slice(2, 10)}`;

const LANGUAGE_STORAGE_KEY = 'learn-code:selected-language';
const BYOK_STORAGE_KEY = 'learn-code:byok-config';

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Navigation State
  const [screen, setScreen] = useState<ScreenMode>('onboarding');
  const [currentTab, setCurrentTab] = useState<TabRoute>('home');
  const [activeLessonParams, setActiveLessonParams] = useState<LessonParams | null>(null);

  // User State
  const [user, setUser] = useState<UserSession>({
    type: 'guest',
    id: 'guest-local-user',
    displayName: 'Learner',
  });
  const [selectedLanguage, setSelectedLanguageState] = useState<SupportedLanguage>('kotlin');
  const [byokProvider, setByokProvider] = useState<ByokProvider>('openai-compatible');
  const [byokKeyConfigured, setByokKeyConfigured] = useState(false);

  // Progress State
  const [xpToday, setXpToday] = useState<number>(20);
  const [totalXp, setTotalXp] = useState<number>(140);
  const [dailyTargetXp, setDailyTargetXpState] = useState<number>(30);
  const [currentStreak, setCurrentStreak] = useState<number>(3);
  const [longestStreak, setLongestStreak] = useState<number>(7);
  const [freezesHeld, setFreezesHeld] = useState<number>(1);
  const [completedLessonIds, setCompletedLessonIds] = useState<string[]>([]);

  // Review State
  const [reviewCards, setReviewCards] = useState<DemoFlashcard[]>(INITIAL_REVIEW_CARDS);

  // Sync State
  const [syncStatus, setSyncStatus] = useState<SyncStatus>('guest');
  const [pendingOutboxCount, setPendingOutboxCount] = useState<number>(0);
  const [lastSyncedAt, setLastSyncedAt] = useState<string | null>(null);

  // Sync Manager instance
  const syncManager = useMemo(() => new MobileSyncManager(), []);
  const leitnerScheduler = useMemo(() => new LeitnerScheduler(), []);

  useEffect(() => {
    AsyncStorage.getItem(LANGUAGE_STORAGE_KEY).then((value) => {
      if (value === 'kotlin' || value === 'swift' || value === 'react' || value === 'typescript') {
        setSelectedLanguageState(value);
      }
    });
    AsyncStorage.getItem(BYOK_STORAGE_KEY).then((value) => {
      if (!value) return;
      try {
        const parsed = JSON.parse(value) as { provider?: ByokProvider; apiKey?: string };
        if (parsed.provider === 'openai-compatible' || parsed.provider === 'anthropic-compatible' || parsed.provider === 'custom') {
          setByokProvider(parsed.provider);
        }
        setByokKeyConfigured(Boolean(parsed.apiKey));
      } catch {
        setByokKeyConfigured(false);
      }
    });
  }, []);
  // Set navigation tab
  const setTab = useCallback((tab: TabRoute) => {
    setCurrentTab(tab);
    if (screen !== 'main') {
      setScreen('main');
    }
  }, [screen]);

  const setSelectedLanguage = useCallback((language: SupportedLanguage) => {
    setSelectedLanguageState(language);
    setCurrentTab('home');
    void AsyncStorage.setItem(LANGUAGE_STORAGE_KEY, language);
  }, []);

  // Launch a lesson
  const startLesson = useCallback((chapterId: string, lessonId: string) => {
    setActiveLessonParams({ chapterId, lessonId });
    setScreen('lesson');
  }, []);

  // Exit lesson back to path or previous view
  const exitLesson = useCallback(() => {
    setActiveLessonParams(null);
    setScreen('main');
  }, []);

  // Continue as guest
  const continueAsGuest = useCallback(() => {
    setUser({
      type: 'guest',
      id: 'guest-local-user',
      displayName: 'Guest Learner',
    });
    setSyncStatus('guest');
    setScreen('main');
  }, []);

  // Sign in / Create account
  const signIn = useCallback((email: string, displayName?: string) => {
    setUser({
      type: 'account',
      id: `usr_${Date.now()}`,
      email,
      displayName: displayName || email.split('@')[0] || 'Learner',
    });
    setSyncStatus('synced');
    setLastSyncedAt(new Date().toISOString());
    setScreen('main');
  }, []);

  // Sign out back to onboarding
  const signOut = useCallback(() => {
    setUser({
      type: 'guest',
      id: 'guest-local-user',
      displayName: 'Guest Learner',
    });
    setSyncStatus('guest');
    setScreen('onboarding');
  }, []);

  // Change daily goal target
  const setDailyGoalTarget = useCallback((target: number) => {
    setDailyTargetXpState(target);
  }, []);

  // Record an exercise grade using @learn/core MultipleChoiceGrader
  const recordExerciseGrade = useCallback((
    exerciseId: string,
    isCorrect: boolean,
    optionIndex: number,
    xpAwarded = CARD_XP
  ) => {
    const awarded = isCorrect ? xpAwarded : 0;
    if (isCorrect) {
      setXpToday((prev) => prev + awarded);
      setTotalXp((prev) => prev + awarded);
    }

    // Queue sync event in local outbox
    const event: SyncEvent = {
      eventId: createEventId('attempt'),
      userId: user.id,
      deviceId: 'mobile-shell-dev',
      type: 'exercise_attempt_recorded',
      occurredAt: new Date().toISOString(),
      payload: {
        exerciseId,
        correct: isCorrect,
        selectedIndex: optionIndex,
        xpAwarded: awarded,
      },
    };

    syncManager.enqueueEvent(event).then(() => {
      syncManager.getPendingCount().then((count) => {
        setPendingOutboxCount(count);
        if (user.type === 'account') {
          setSyncStatus('pending');
        }
      });
    });

    return isCorrect;
  }, [user.id, user.type, syncManager]);

  // Finish a lesson
  const finishLesson = useCallback((lessonId: string, earnedXp: number) => {
    setCompletedLessonIds((prev) =>
      prev.includes(lessonId) ? prev : [...prev, lessonId]
    );

    // Enqueue lesson settled event
    const event: SyncEvent = {
      eventId: createEventId('settled'),
      userId: user.id,
      deviceId: 'mobile-shell-dev',
      type: 'lesson_settled',
      occurredAt: new Date().toISOString(),
      payload: {
        lessonId,
        earnedXp,
      },
    };

    syncManager.enqueueEvent(event).then(() => {
      syncManager.getPendingCount().then((count) => {
        setPendingOutboxCount(count);
        if (user.type === 'account') {
          setSyncStatus('pending');
        }
      });
    });

    if (earnedXp > 0) {
      setXpToday((prev) => prev + earnedXp);
      setTotalXp((prev) => prev + earnedXp);
    }

    setActiveLessonParams(null);
    setScreen('main');
    setCurrentTab('path');
  }, [user.id, user.type, syncManager]);

  // Schedule next card review using @learn/core LeitnerScheduler
  const scheduleCardReview = useCallback((cardId: string, correct: boolean) => {
    setReviewCards((prev) =>
      prev.map((card) => {
        if (card.id !== cardId) return card;

        const coreCard: ReviewCard = {
          itemId: card.id,
          box: card.box,
          dueEpochDay: 0,
        };
        const scheduled = leitnerScheduler.scheduleNextReview(coreCard, correct, 0);

        return {
          ...card,
          box: scheduled.box,
          dueToday: false, // marked reviewed for today
        };
      })
    );

    if (correct) {
      setXpToday((prev) => prev + 5);
      setTotalXp((prev) => prev + 5);
    }
  }, [leitnerScheduler]);

  // Trigger manual sync
  const triggerManualSync = useCallback(async () => {
    if (user.type === 'guest') {
      setSyncStatus('guest');
      return;
    }

    setSyncStatus('syncing');
    try {
      await syncManager.sync();
      setPendingOutboxCount(0);
      setSyncStatus('synced');
      setLastSyncedAt(new Date().toISOString());
    } catch {
      setSyncStatus('pending');
    }
  }, [user.type, syncManager]);

  // Reset demo progress
  const resetProgress = useCallback(() => {
    setXpToday(0);
    setTotalXp(0);
    setCompletedLessonIds([]);
    setReviewCards(INITIAL_REVIEW_CARDS);
    setPendingOutboxCount(0);
    setSyncStatus(user.type === 'guest' ? 'guest' : 'synced');
  }, [user.type]);

  const saveByokConfig = useCallback(async (provider: ByokProvider, apiKey: string) => {
    const trimmedKey = apiKey.trim();
    if (!trimmedKey) return false;
    await AsyncStorage.setItem(BYOK_STORAGE_KEY, JSON.stringify({ provider, apiKey: trimmedKey }));
    setByokProvider(provider);
    setByokKeyConfigured(true);
    return true;
  }, []);

  const clearByokConfig = useCallback(async () => {
    await AsyncStorage.removeItem(BYOK_STORAGE_KEY);
    setByokKeyConfigured(false);
  }, []);

  const value: AppState = {
    screen,
    currentTab,
    activeLessonParams,
    user,
    selectedLanguage,
    byokProvider,
    byokKeyConfigured,
    xpToday,
    totalXp,
    dailyTargetXp,
    currentStreak,
    longestStreak,
    freezesHeld,
    completedLessonIds,
    reviewCards,
    syncStatus,
    pendingOutboxCount,
    lastSyncedAt,
    setTab,
    setSelectedLanguage,
    startLesson,
    finishLesson,
    exitLesson,
    continueAsGuest,
    signIn,
    signOut,
    setDailyGoalTarget,
    recordExerciseGrade,
    scheduleCardReview,
    triggerManualSync,
    resetProgress,
    saveByokConfig,
    clearByokConfig,
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
};

export const useApp = (): AppState => {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useApp must be used within an AppProvider');
  }
  return context;
};
