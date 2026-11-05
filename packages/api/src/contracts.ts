export type SyncEventType =
  | "exercise_attempt_recorded"
  | "lesson_settled"
  | "review_card_updated"
  | "daily_activity_recorded"
  | "setting_changed"
  | "pack_installed"
  | "pack_removed"

export type SyncEvent = {
  eventId: string
  userId: string
  deviceId: string
  type: SyncEventType
  occurredAt: string
  payload: Record<string, unknown>
}

export type SyncCursor = {
  sequence: number
}

export type SyncPushResult = {
  eventId: string
  accepted: boolean
  duplicate: boolean
  serverSequence: number
}

export type SyncPullResult = {
  events: SyncEvent[]
  cursor: SyncCursor
}

export type UserAccount = {
  id: string
  email?: string
  displayName?: string
  createdAt: string
}

export type Entitlement = {
  productId: string
  status: "active" | "grace_period" | "expired"
  expiresAt?: string
  source: "app_store" | "play_store" | "web" | "admin"
}

export type NotificationPreferences = {
  dailyGoal: boolean
  reviewDue: boolean
  streakRisk: boolean
  packUpdates: boolean
  quietHoursStart?: string
  quietHoursEnd?: string
}

export type CoursePackSource = {
  url: string
  ref?: string
}

export type InstalledPackRecord = {
  packId: string
  version: string
  source: CoursePackSource
  contentHash: string
  installedAt: string
  lastKnownGood: boolean
}

export interface SyncApi {
  push(events: SyncEvent[]): Promise<SyncPushResult[]>
  pull(cursor: SyncCursor): Promise<SyncPullResult>
}

export interface AccountApi {
  getCurrentAccount(): Promise<UserAccount | null>
  signIn(input: { email: string; password: string }): Promise<UserAccount>
  signOut(): Promise<void>
  deleteAccount(): Promise<void>
}

export interface EntitlementApi {
  listEntitlements(): Promise<Entitlement[]>
}

export interface NotificationApi {
  getPreferences(): Promise<NotificationPreferences>
  savePreferences(preferences: NotificationPreferences): Promise<void>
}
