import type { CoursePackSource } from "./types.ts"

const GITHUB_REPO_REGEX =
  /^https:\/\/github\.com\/([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+?)(?:\.git)?\/?$/
const GITHUB_RELEASE_REGEX =
  /^https:\/\/github\.com\/([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+)\/releases\/tag\/([^/?#]+)\/?$/
const GITHUB_BLOB_REGEX =
  /^https:\/\/github\.com\/([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+)\/blob\/([^/?#]+)\/(.+)$/
const RAW_GITHUB_REGEX =
  /^https:\/\/raw\.githubusercontent\.com\/([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+)\/([^/?#]+)\/(.+)$/

export const DEFAULT_TRUSTED_HOSTS = [
  "github.com",
  "raw.githubusercontent.com"
] as const

export interface ParsedGitHubUrl {
  owner: string
  repo: string
  ref?: string
  path?: string
  kind: "repo" | "release" | "blob" | "raw"
}

/**
 * Parses supported GitHub URL variants into structured metadata.
 */
export function parseGitHubUrl(url: string): ParsedGitHubUrl | null {
  const trimmed = url.trim()

  const rawMatch = RAW_GITHUB_REGEX.exec(trimmed)
  if (rawMatch) {
    return {
      owner: rawMatch[1],
      repo: rawMatch[2],
      ref: rawMatch[3],
      path: rawMatch[4],
      kind: "raw"
    }
  }

  const blobMatch = GITHUB_BLOB_REGEX.exec(trimmed)
  if (blobMatch) {
    return {
      owner: blobMatch[1],
      repo: blobMatch[2],
      ref: blobMatch[3],
      path: blobMatch[4],
      kind: "blob"
    }
  }

  const releaseMatch = GITHUB_RELEASE_REGEX.exec(trimmed)
  if (releaseMatch) {
    return {
      owner: releaseMatch[1],
      repo: releaseMatch[2],
      ref: releaseMatch[3],
      kind: "release"
    }
  }

  const repoMatch = GITHUB_REPO_REGEX.exec(trimmed)
  if (repoMatch) {
    return {
      owner: repoMatch[1],
      repo: repoMatch[2],
      kind: "repo"
    }
  }

  return null
}

/**
 * Validates whether a given URL meets trusted source safety criteria:
 * - Must use HTTPS
 * - Host must be in the trusted hosts whitelist (defaulting to GitHub)
 */
export function isTrustedSourceUrl(
  url: string,
  allowedHosts: readonly string[] = DEFAULT_TRUSTED_HOSTS
): boolean {
  try {
    const parsed = new URL(url)
    if (parsed.protocol !== "https:") return false
    return allowedHosts.some(
      (host) => parsed.hostname === host || parsed.hostname.endsWith(`.${host}`)
    )
  } catch {
    return false
  }
}

/**
 * Resolves a canonical download URL from a course pack source or source URL string.
 */
export function resolvePackDownloadUrl(
  source: CoursePackSource | string,
  defaultFilename = "course-pack.json"
): string {
  const url = typeof source === "string" ? source.trim() : source.url.trim()
  const explicitRef = typeof source === "object" && source.ref ? source.ref.trim() : undefined
  const explicitPath = typeof source === "object" && source.path ? source.path.trim() : undefined

  const parsed = parseGitHubUrl(url)
  if (!parsed) {
    return url
  }

  const ref = explicitRef || parsed.ref || "main"
  const path = explicitPath || parsed.path || defaultFilename

  return `https://raw.githubusercontent.com/${parsed.owner}/${parsed.repo}/${ref}/${path}`
}

/**
 * Normalizes a GitHub URL into a canonical CoursePackSource object.
 */
export function createCoursePackSource(
  inputUrl: string,
  options: { ref?: string; commit?: string; path?: string } = {}
): CoursePackSource {
  const parsed = parseGitHubUrl(inputUrl)
  if (parsed) {
    return {
      url: `https://github.com/${parsed.owner}/${parsed.repo}`,
      ref: options.ref || parsed.ref || "main",
      commit: options.commit,
      path: options.path || parsed.path || "course-pack.json"
    }
  }

  return {
    url: inputUrl,
    ref: options.ref,
    commit: options.commit,
    path: options.path
  }
}
