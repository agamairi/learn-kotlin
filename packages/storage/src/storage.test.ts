import test from "node:test"
import assert from "node:assert/strict"
import { InMemorySyncApi, type SyncEvent } from "@learn/api"
import { InMemoryLocalSyncStore, SyncCoordinator } from "./storage"

const event: SyncEvent = {
  eventId: "lesson-run-1",
  userId: "user-1",
  deviceId: "device-1",
  type: "lesson_settled",
  occurredAt: "2026-11-04T12:00:00.000Z",
  payload: { packId: "kotlin-stripe", lessonId: "lesson-1", xp: 30 },
}

test("flush acknowledges local events and advances the sync cursor", async () => {
  const local = new InMemoryLocalSyncStore()
  const coordinator = new SyncCoordinator(local)
  const api = new InMemorySyncApi()

  await local.enqueue(event)
  const result = await coordinator.flush(api)

  assert.equal(result.pushed[0]?.accepted, true)
  assert.equal(result.remainingOutbox, 0)
  assert.equal(result.cursor.sequence, 1)
})

test("a retry after a failed network attempt keeps the outbox event", async () => {
  const local = new InMemoryLocalSyncStore()
  const coordinator = new SyncCoordinator(local)
  const failingApi = {
    async push(): Promise<never> {
      throw new Error("offline")
    },
    async pull() {
      return { events: [], cursor: { sequence: 0 } }
    },
  }

  await local.enqueue(event)
  await assert.rejects(() => coordinator.flush(failingApi), /offline/)
  assert.equal((await local.listOutbox()).length, 1)
})

test("duplicate remote events do not create duplicate local records", async () => {
  const local = new InMemoryLocalSyncStore()
  await local.applyRemoteEvents([event, event])

  assert.equal(local.getAppliedEvents().length, 1)
})
