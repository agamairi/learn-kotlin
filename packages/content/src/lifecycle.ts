import type {
  CoursePack,
  CoursePackSource,
  InstalledPackRecord,
  PackRollbackRecord,
  PackUpdateCheckResult,
  PackUpdateType
} from "./types.ts"
import { SUPPORTED_SCHEMA_VERSIONS } from "./types.ts"
import { compareSemver } from "./validator.ts"

/**
 * Classifies the semantic version change from one version to another.
 */
export function classifyVersionChange(fromVersion: string, toVersion: string): PackUpdateType {
  const cmp = compareSemver(fromVersion, toVersion)
  if (cmp === 0) return "equal"
  if (cmp > 0) return "downgrade"

  const [fromMajor, fromMinor] = fromVersion.split(".").map((n) => parseInt(n, 10))
  const [toMajor, toMinor] = toVersion.split(".").map((n) => parseInt(n, 10))

  if (toMajor > fromMajor) return "major"
  if (toMinor > fromMinor) return "minor"
  return "patch"
}

/**
 * Checks client version compatibility against a declared minimum client version requirement.
 */
export function isClientCompatible(
  minimumClientVersion?: string,
  currentClientVersion?: string
): { compatible: boolean; reason?: string } {
  if (!minimumClientVersion || !currentClientVersion) {
    return { compatible: true }
  }

  if (compareSemver(currentClientVersion, minimumClientVersion) < 0) {
    return {
      compatible: false,
      reason: `Client version ${currentClientVersion} is below required minimum ${minimumClientVersion}`
    }
  }

  return { compatible: true }
}

/**
 * Evaluates a candidate course pack against an currently installed pack.
 */
export function checkPackUpdate(
  current: InstalledPackRecord,
  candidate: CoursePack,
  clientVersion?: string
): PackUpdateCheckResult {
  if (candidate.packId !== current.packId) {
    return {
      packId: current.packId,
      hasUpdate: false,
      currentVersion: current.version,
      candidateVersion: candidate.version,
      updateType: "equal",
      isCompatible: false,
      incompatibilityReason: `Candidate packId '${candidate.packId}' does not match installed packId '${current.packId}'`,
      contentChanged: false
    }
  }

  const updateType = classifyVersionChange(current.version, candidate.version)
  const contentChanged = current.contentHash !== candidate.contentHash

  // Check schema version support
  if (!SUPPORTED_SCHEMA_VERSIONS.includes(candidate.schemaVersion as any)) {
    return {
      packId: current.packId,
      hasUpdate: updateType !== "equal" || contentChanged,
      currentVersion: current.version,
      candidateVersion: candidate.version,
      updateType,
      isCompatible: false,
      incompatibilityReason: `Unsupported candidate schemaVersion '${candidate.schemaVersion}'`,
      contentChanged
    }
  }

  // Check client version compatibility
  const clientCheck = isClientCompatible(candidate.minimumClientVersion, clientVersion)
  if (!clientCheck.compatible) {
    return {
      packId: current.packId,
      hasUpdate: updateType !== "equal" || contentChanged,
      currentVersion: current.version,
      candidateVersion: candidate.version,
      updateType,
      isCompatible: false,
      incompatibilityReason: clientCheck.reason,
      contentChanged
    }
  }

  const hasUpdate = (updateType !== "equal" && updateType !== "downgrade") || contentChanged

  return {
    packId: current.packId,
    hasUpdate,
    currentVersion: current.version,
    candidateVersion: candidate.version,
    updateType,
    isCompatible: true,
    contentChanged
  }
}

/**
 * Creates an initial InstalledPackRecord upon successful first installation.
 */
export function createInstalledPackRecord(
  pack: CoursePack,
  options: {
    source?: CoursePackSource
    installedAt?: string
    metadata?: Record<string, unknown>
  } = {}
): InstalledPackRecord {
  return {
    packId: pack.packId,
    version: pack.version,
    source: options.source || pack.source,
    contentHash: pack.contentHash,
    installedAt: options.installedAt || new Date().toISOString(),
    lastKnownGood: true,
    schemaVersion: pack.schemaVersion,
    minimumClientVersion: pack.minimumClientVersion,
    status: "active",
    metadata: options.metadata
  }
}

/**
 * Prepares an update transition, preserving the existing record as lastKnownGood.
 */
export function preparePackUpdate(
  current: InstalledPackRecord,
  newPack: CoursePack,
  options: { source?: CoursePackSource; updatedAt?: string } = {}
): {
  updatingRecord: InstalledPackRecord
  lastKnownGoodRecord: InstalledPackRecord
} {
  const lastKnownGoodRecord: InstalledPackRecord = {
    ...current,
    lastKnownGood: true
  }

  const updatingRecord: InstalledPackRecord = {
    packId: newPack.packId,
    version: newPack.version,
    source: options.source || newPack.source,
    contentHash: newPack.contentHash,
    installedAt: options.updatedAt || new Date().toISOString(),
    lastKnownGood: false,
    schemaVersion: newPack.schemaVersion,
    minimumClientVersion: newPack.minimumClientVersion,
    status: "updating",
    previousVersion: current.version,
    metadata: current.metadata
  }

  return { updatingRecord, lastKnownGoodRecord }
}

/**
 * Commits a successfully installed and verified update as the active, lastKnownGood version.
 */
export function commitPackUpdate(updatingRecord: InstalledPackRecord): InstalledPackRecord {
  return {
    ...updatingRecord,
    status: "active",
    lastKnownGood: true
  }
}

/**
 * Rolls back a failed update to the last known good record and creates a rollback audit record.
 */
export function rollbackPackUpdate(
  failedRecord: InstalledPackRecord,
  lastKnownGoodRecord: InstalledPackRecord,
  reason: string,
  rolledBackAt: string = new Date().toISOString()
): {
  activeRecord: InstalledPackRecord
  rollbackRecord: PackRollbackRecord
} {
  const rollbackRecord: PackRollbackRecord = {
    packId: failedRecord.packId,
    failedVersion: failedRecord.version,
    rolledBackToVersion: lastKnownGoodRecord.version,
    reason,
    rolledBackAt
  }

  const activeRecord: InstalledPackRecord = {
    ...lastKnownGoodRecord,
    status: "active",
    lastKnownGood: true,
    metadata: {
      ...lastKnownGoodRecord.metadata,
      lastRollback: rollbackRecord
    }
  }

  return { activeRecord, rollbackRecord }
}
