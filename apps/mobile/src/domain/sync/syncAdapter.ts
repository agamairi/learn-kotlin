import type { SyncApi, SyncCursor, SyncEvent } from '@learn/api';
import { InMemorySyncApi } from '@learn/api';
import {
  SyncCoordinator,
  type LocalSyncStore,
  type OutboxRecord,
  type SyncRunResult,
} from '@learn/storage';
import { AsyncStorageLocalSyncStore } from './asyncStorageStore';

/**
 * In-memory implementation of LocalSyncStore conforming to @learn/storage.
 * Serves as the fallback adapter until the native SQLite driver is wired.
 */
export class InMemoryLocalSyncStore implements LocalSyncStore {
  private outbox: OutboxRecord[] = [];
  private cursor: SyncCursor = { sequence: 0 };
  private appliedEvents: SyncEvent[] = [];

  async listOutbox(): Promise<OutboxRecord[]> {
    return [...this.outbox];
  }

  async enqueue(event: SyncEvent): Promise<void> {
    this.outbox.push({
      event,
      attempts: 0,
      enqueuedAt: new Date().toISOString(),
    });
  }

  async acknowledge(eventId: string): Promise<void> {
    this.outbox = this.outbox.filter((record) => record.event.eventId !== eventId);
  }

  async getCursor(): Promise<SyncCursor> {
    return { ...this.cursor };
  }

  async setCursor(cursor: SyncCursor): Promise<void> {
    this.cursor = { ...cursor };
  }

  async applyRemoteEvents(events: SyncEvent[]): Promise<void> {
    this.appliedEvents.push(...events);
  }

  getPendingCount(): number {
    return this.outbox.length;
  }
}

/**
 * Mobile sync manager wrapping SyncCoordinator from @learn/storage.
 */
export class MobileSyncManager {
  private readonly store: LocalSyncStore;
  private readonly coordinator: SyncCoordinator;
  private readonly remoteApi: SyncApi;

  constructor(remoteApi?: SyncApi, store?: LocalSyncStore) {
    this.store = store ?? new AsyncStorageLocalSyncStore();
    this.coordinator = new SyncCoordinator(this.store);
    this.remoteApi = remoteApi ?? new InMemorySyncApi();
  }

  async enqueueEvent(event: SyncEvent): Promise<void> {
    await this.store.enqueue(event);
  }

  async sync(): Promise<SyncRunResult> {
    return await this.coordinator.flush(this.remoteApi);
  }

  async getPendingCount(): Promise<number> {
    const list = await this.store.listOutbox();
    return list.length;
  }
}
