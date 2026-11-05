import assert from "node:assert/strict"
import test, { describe, it } from "node:test"
import {
  checkPackUpdate,
  classifyVersionChange,
  commitPackUpdate,
  createInstalledPackRecord,
  isClientCompatible,
  preparePackUpdate,
  rollbackPackUpdate
} from "../lifecycle.ts"
import { createValidCoursePack } from "./fixtures.ts"

describe("Installed Pack, Update and Rollback Lifecycle", () => {
  describe("Version Classification and Client Compatibility", () => {
    it("classifies semver version changes correctly", () => {
      assert.equal(classifyVersionChange("1.0.0", "1.0.0"), "equal")
      assert.equal(classifyVersionChange("1.0.0", "1.0.1"), "patch")
      assert.equal(classifyVersionChange("1.0.0", "1.1.0"), "minor")
      assert.equal(classifyVersionChange("1.0.0", "2.0.0"), "major")
      assert.equal(classifyVersionChange("2.0.0", "1.9.9"), "downgrade")
    })

    it("verifies client compatibility against minimum requirement", () => {
      assert.equal(isClientCompatible("1.0.0", "1.0.0").compatible, true)
      assert.equal(isClientCompatible("1.0.0", "1.2.0").compatible, true)

      const incompatible = isClientCompatible("2.0.0", "1.5.0")
      assert.equal(incompatible.compatible, false)
      assert.ok(incompatible.reason?.includes("below required minimum 2.0.0"))
    })
  })

  describe("checkPackUpdate", () => {
    it("identifies a valid minor update", () => {
      const pack = createValidCoursePack()
      const installed = createInstalledPackRecord(pack)

      const candidate = createValidCoursePack()
      candidate.version = "1.1.0"
      candidate.contentHash = "sha256:new-hash"

      const check = checkPackUpdate(installed, candidate, "1.0.0")
      assert.equal(check.hasUpdate, true)
      assert.equal(check.updateType, "minor")
      assert.equal(check.isCompatible, true)
      assert.equal(check.contentChanged, true)
    })

    it("rejects candidate with different packId", () => {
      const pack = createValidCoursePack()
      const installed = createInstalledPackRecord(pack)

      const candidate = createValidCoursePack()
      candidate.packId = "different-pack"

      const check = checkPackUpdate(installed, candidate)
      assert.equal(check.isCompatible, false)
      assert.ok(check.incompatibilityReason?.includes("does not match installed packId"))
    })

    it("rejects candidate requiring newer client version", () => {
      const pack = createValidCoursePack()
      const installed = createInstalledPackRecord(pack)

      const candidate = createValidCoursePack()
      candidate.version = "1.1.0"
      candidate.minimumClientVersion = "3.0.0"

      const check = checkPackUpdate(installed, candidate, "2.1.0")
      assert.equal(check.isCompatible, false)
      assert.ok(check.incompatibilityReason?.includes("below required minimum 3.0.0"))
    })
  })

  describe("Installation, Update, and Rollback State Boundaries", () => {
    it("creates an active installed record marked as lastKnownGood", () => {
      const pack = createValidCoursePack()
      const record = createInstalledPackRecord(pack)

      assert.equal(record.packId, pack.packId)
      assert.equal(record.version, pack.version)
      assert.equal(record.lastKnownGood, true)
      assert.equal(record.status, "active")
      assert.ok(record.installedAt)
    })

    it("manages the two-phase update transition cleanly", () => {
      const initialPack = createValidCoursePack()
      const initialRecord = createInstalledPackRecord(initialPack)

      const updatedPack = createValidCoursePack()
      updatedPack.version = "1.1.0"
      updatedPack.contentHash = "sha256:updated-hash"

      const { updatingRecord, lastKnownGoodRecord } = preparePackUpdate(
        initialRecord,
        updatedPack
      )

      assert.equal(lastKnownGoodRecord.version, "1.0.0")
      assert.equal(lastKnownGoodRecord.lastKnownGood, true)

      assert.equal(updatingRecord.version, "1.1.0")
      assert.equal(updatingRecord.status, "updating")
      assert.equal(updatingRecord.lastKnownGood, false)
      assert.equal(updatingRecord.previousVersion, "1.0.0")

      // Commit the update
      const activeRecord = commitPackUpdate(updatingRecord)
      assert.equal(activeRecord.status, "active")
      assert.equal(activeRecord.lastKnownGood, true)
      assert.equal(activeRecord.version, "1.1.0")
    })

    it("rolls back a failed update, restoring lastKnownGood and recording rollback metadata", () => {
      const initialPack = createValidCoursePack()
      const initialRecord = createInstalledPackRecord(initialPack)

      const brokenPack = createValidCoursePack()
      brokenPack.version = "1.1.0"

      const { updatingRecord, lastKnownGoodRecord } = preparePackUpdate(
        initialRecord,
        brokenPack
      )

      const rollbackReason = "Runtime verification failed: blank syntax invalid"
      const { activeRecord, rollbackRecord } = rollbackPackUpdate(
        updatingRecord,
        lastKnownGoodRecord,
        rollbackReason
      )

      // Verify active record was safely rolled back
      assert.equal(activeRecord.version, "1.0.0")
      assert.equal(activeRecord.status, "active")
      assert.equal(activeRecord.lastKnownGood, true)

      // Verify rollback metadata
      assert.equal(rollbackRecord.packId, initialPack.packId)
      assert.equal(rollbackRecord.failedVersion, "1.1.0")
      assert.equal(rollbackRecord.rolledBackToVersion, "1.0.0")
      assert.equal(rollbackRecord.reason, rollbackReason)
      assert.ok(rollbackRecord.rolledBackAt)

      // Verify metadata attached to active record
      assert.deepEqual(activeRecord.metadata?.lastRollback, rollbackRecord)
    })
  })
})
