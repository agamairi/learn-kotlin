import AsyncStorage from '@react-native-async-storage/async-storage';
import type { SyncCursor, SyncEvent } from '@learn/api';
import type { LocalSyncStore, OutboxRecord } from '@learn/storage';

type StoredState = {
  outbox: OutboxRecord[];
  cursor: SyncCursor;
  appliedEvents: SyncEvent[];
};

const DEFAULT_STATE: StoredState = {
  outbox: [],
  cursor: { sequence: 0 },
  appliedEvents: [],
};

export class AsyncStorageLocalSyncStore implements LocalSyncStore {
  private readonly key: string;

  constructor(namespace = 'learn-code') {
    this.key = `${namespace}:sync-state`;
  }

  private async readState(): Promise<StoredState> {
    const serialized = await AsyncStorage.getItem(this.key);
    if (!serialized) return { ...DEFAULT_STATE, outbox: [], appliedEvents: [] };

    try {
      const parsed = JSON.parse(serialized) as Partial<StoredState>;
      return {
        outbox: Array.isArray(parsed.outbox) ? parsed.outbox : [],
        cursor: parsed.cursor ?? { sequence: 0 },
        appliedEvents: Array.isArray(parsed.appliedEvents) ? parsed.appliedEvents : [],
      };
    } catch {
      return { ...DEFAULT_STATE, outbox: [], appliedEvents: [] };
    }
  }

  private async writeState(state: StoredState): Promise<void> {
    await AsyncStorage.setItem(this.key, JSON.stringify(state));
  }

  async listOutbox(): Promise<OutboxRecord[]> {
    const state = await this.readState();
    return state.outbox.map((record) => ({
      ...record,
      event: { ...record.event, payload: { ...record.event.payload } },
    }));
  }

  async enqueue(event: SyncEvent): Promise<void> {
    const state = await this.readState();
    const known = state.outbox.some((record) => record.event.eventId === event.eventId) ||
      state.appliedEvents.some((knownEvent) => knownEvent.eventId === event.eventId);
    if (known) return;

    state.outbox.push({
      event,
      attempts: 0,
      enqueuedAt: new Date().toISOString(),
    });
    await this.writeState(state);
  }

  async acknowledge(eventId: string): Promise<void> {
    const state = await this.readState();
    state.outbox = state.outbox.filter((record) => record.event.eventId !== eventId);
    await this.writeState(state);
  }

  async getCursor(): Promise<SyncCursor> {
    return (await this.readState()).cursor;
  }

  async setCursor(cursor: SyncCursor): Promise<void> {
    const state = await this.readState();
    state.cursor = { ...cursor };
    await this.writeState(state);
  }

  async applyRemoteEvents(events: SyncEvent[]): Promise<void> {
    const state = await this.readState();
    const appliedIds = new Set(state.appliedEvents.map((event) => event.eventId));
    for (const event of events) {
      if (!appliedIds.has(event.eventId)) {
        state.appliedEvents.push(event);
        appliedIds.add(event.eventId);
      }
      state.outbox = state.outbox.filter((record) => record.event.eventId !== event.eventId);
    }
    await this.writeState(state);
  }
}
