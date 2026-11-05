import type { SyncApi, SyncCursor, SyncEvent, SyncPullResult, SyncPushResult } from "./contracts"

export class InMemorySyncApi implements SyncApi {
  private readonly events: SyncEvent[] = []
  private readonly seenEventIds = new Set<string>()

  async push(events: SyncEvent[]): Promise<SyncPushResult[]> {
    return events.map((event) => {
      const alreadySeen = this.seenEventIds.has(event.eventId)
      if (!alreadySeen) {
        this.seenEventIds.add(event.eventId)
        this.events.push(event)
      }

      return {
        eventId: event.eventId,
        accepted: true,
        duplicate: alreadySeen,
        serverSequence: this.events.indexOf(event) + 1,
      }
    })
  }

  async pull(cursor: SyncCursor): Promise<SyncPullResult> {
    const events = this.events.slice(cursor.sequence)
    return {
      events,
      cursor: { sequence: this.events.length },
    }
  }
}
