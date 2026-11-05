import test from "node:test"
import assert from "node:assert/strict"
import { InMemorySyncApi } from "./inMemorySyncApi"
import type { SyncEvent } from "./contracts"

const event: SyncEvent = {
  eventId: "event-1",
  userId: "user-1",
  deviceId: "device-1",
  type: "lesson_settled",
  occurredAt: "2026-11-04T12:00:00.000Z",
  payload: { lessonId: "lesson-1", xp: 30 },
}

test("sync accepts an event once and treats retries as duplicates", async () => {
  const api = new InMemorySyncApi()

  const first = await api.push([event])
  const retry = await api.push([event])

  assert.deepEqual(first[0], {
    eventId: "event-1",
    accepted: true,
    duplicate: false,
    serverSequence: 1,
  })
  assert.deepEqual(retry[0], {
    eventId: "event-1",
    accepted: true,
    duplicate: true,
    serverSequence: 1,
  })
})

test("sync pulls only events after the cursor", async () => {
  const api = new InMemorySyncApi()
  await api.push([event])

  const first = await api.pull({ sequence: 0 })
  const second = await api.pull(first.cursor)

  assert.equal(first.events.length, 1)
  assert.equal(second.events.length, 0)
  assert.equal(first.cursor.sequence, 1)
})
