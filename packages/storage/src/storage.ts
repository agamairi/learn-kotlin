import type { SyncApi, SyncCursor, SyncEvent, SyncPushResult } from "@learn/api"

export type OutboxRecord = {
  event: SyncEvent
  attempts: number
  enqueuedAt: string
  lastAttemptAt?: string
}

export interface LocalSyncStore {
  listOutbox(): Promise<OutboxRecord[]>
  enqueue(event: SyncEvent): Promise<void>
  acknowledge(eventId: string): Promise<void>
  getCursor(): Promise<SyncCursor>
  setCursor(cursor: SyncCursor): Promise<void>
  applyRemoteEvents(events: SyncEvent[]): Promise<void>
}

export type SyncRunResult = {
  pushed: SyncPushResult[]
  pulled: number
  remainingOutbox: number
  cursor: SyncCursor
}

export class SyncCoordinator {
  constructor(private readonly store: LocalSyncStore) {}

  async flush(api: SyncApi): Promise<SyncRunResult> {
    const outbox = await this.store.listOutbox()
    const pushed = outbox.length > 0
      ? await api.push(outbox.map((record) => record.event))
      : []

    for (const result of pushed) {
      if (result.accepted) {
        await this.store.acknowledge(result.eventId)
      }
    }

    const cursor = await this.store.getCursor()
    const pulled = await api.pull(cursor)
    if (pulled.events.length > 0) {
      await this.store.applyRemoteEvents(pulled.events)
    }
    await this.store.setCursor(pulled.cursor)

    return {
      pushed,
      pulled: pulled.events.length,
      remainingOutbox: (await this.store.listOutbox()).length,
      cursor: pulled.cursor,
    }
  }
}

export class InMemoryLocalSyncStore implements LocalSyncStore {
  private readonly outbox = new Map<string, OutboxRecord>()
  private readonly appliedEvents = new Map<string, SyncEvent>()
  private cursor: SyncCursor = { sequence: 0 }

  async listOutbox(): Promise<OutboxRecord[]> {
    return [...this.outbox.values()].map((record) => ({
      ...record,
      event: { ...record.event, payload: { ...record.event.payload } },
    }))
  }

  async enqueue(event: SyncEvent): Promise<void> {
    if (!this.outbox.has(event.eventId) && !this.appliedEvents.has(event.eventId)) {
      this.outbox.set(event.eventId, {
        event,
        attempts: 0,
        enqueuedAt: new Date().toISOString(),
      })
    }
  }

  async acknowledge(eventId: string): Promise<void> {
    this.outbox.delete(eventId)
  }

  async getCursor(): Promise<SyncCursor> {
    return { ...this.cursor }
  }

  async setCursor(cursor: SyncCursor): Promise<void> {
    this.cursor = { ...cursor }
  }

  async applyRemoteEvents(events: SyncEvent[]): Promise<void> {
    for (const event of events) {
      this.appliedEvents.set(event.eventId, event)
      this.outbox.delete(event.eventId)
    }
  }

  getAppliedEvents(): SyncEvent[] {
    return [...this.appliedEvents.values()]
  }
}
