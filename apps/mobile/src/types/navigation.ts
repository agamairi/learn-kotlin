export type TabRoute = 'home' | 'path' | 'review' | 'stats' | 'settings';

export type ScreenMode = 'onboarding' | 'main' | 'lesson';

export interface LessonParams {
  chapterId: string;
  lessonId: string;
}

export type SyncStatus = 'synced' | 'syncing' | 'pending' | 'guest' | 'offline';
